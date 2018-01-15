package com.zimbra.cs.event.recovery;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.NamedEntry;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.SearchDirectoryOptions;
import com.zimbra.cs.db.DbPool;
import com.zimbra.cs.event.Event;
import com.zimbra.cs.event.EventStore;
import com.zimbra.cs.event.SolrCloudEventStore;
import com.zimbra.cs.event.StandaloneSolrEventStore;
import com.zimbra.cs.event.logger.EventLogger;
import com.zimbra.cs.event.logger.SolrCloudEventHandlerFactory;
import com.zimbra.cs.event.logger.StandaloneSolrEventHandlerFactory;
import com.zimbra.cs.index.*;
import com.zimbra.cs.index.solr.SolrCloudIndex;
import com.zimbra.cs.index.solr.SolrIndex;
import com.zimbra.cs.ldap.ZLdapFilter;
import com.zimbra.cs.ldap.ZLdapFilterFactory;
import com.zimbra.cs.mailbox.*;
import com.zimbra.cs.store.StoreManager;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventBackFillFromDB {
    public static void init(Integer batchSize, Integer numThread) throws ServiceException, IOException {
        //Initialize Database
        DbPool.startup();

        //Initialize Blob store
        StoreManager.getInstance().startup();

        //Initialize Index Store
        IndexStore.registerIndexFactory("solr", SolrIndex.Factory.class.getName());
        IndexStore.registerIndexFactory("solrcloud", SolrCloudIndex.Factory.class.getName());

        Provisioning prov = Provisioning.getInstance();

        //Initialize Event Store
        EventStore.registerFactory("solr", StandaloneSolrEventStore.Factory.class.getName());
        EventStore.registerFactory("solrcloud", SolrCloudEventStore.Factory.class.getName());

        EventLogger.registerHandlerFactory("solr", new StandaloneSolrEventHandlerFactory());
        EventLogger.registerHandlerFactory("solrcloud", new SolrCloudEventHandlerFactory());

        prov.getLocalServer().setIndexURL("solrcloud:solr:9983");
        prov.getLocalServer().setEventBackendURL("solrcloud:solr:9983");
        prov.getLocalServer().setEventBatchMaxSize(batchSize);
        prov.getLocalServer().setEventLoggingNumThreads(numThread);

        EventLogger.getEventLogger().startupEventNotifierExecutor();
    }

    public static void cleanup() throws Exception {
        EventLogger.getEventLogger().shutdownEventLogger();
        DbPool.shutdown();
        IndexStore.getFactory().destroy();
        StoreManager.getInstance().shutdown();
    }

    public static List<NamedEntry> getAllAccounts() throws ServiceException {
        SearchDirectoryOptions options = new SearchDirectoryOptions();
        ZLdapFilter filter = ZLdapFilterFactory.getInstance().allAccounts();
        options.setFilter(filter);
        options.setTypes(SearchDirectoryOptions.ObjectType.accounts);
        List<NamedEntry> accounts = Provisioning.getInstance().searchDirectory(options);
        //TODO: Remove this print loop
        System.out.println("Printing Accounts");
        for (NamedEntry account : accounts) {
            System.out.println(account.getName() + " " + account.getId());
        }
        return accounts;
    }

    public static String getOneYearBackDate(Locale locale) {
        LocalDate oneYearBack = LocalDate.now().minusYears(1);
        return oneYearBack.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale));
    }

    public void execute(Integer accNum, Integer batchSize, Integer numThread) throws Exception {
        ZimbraLog.event.info("Starting the Event Backfill");
        try {
            init(batchSize, numThread);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<NamedEntry> accounts = getAllAccounts();
            int latchSize = accNum == null ? accounts.size() : 1;
            CountDownLatch numberOfAccountsToProcess = new CountDownLatch(latchSize);
            if(accNum == null) {
                for (NamedEntry acc : accounts) {
                    Account account = Provisioning.getInstance().getAccountById(acc.getId());
                    executor.execute(new GenerateEventsForAccountTask(account, numberOfAccountsToProcess));
                }
            }
            Thread.sleep(10000);
            executor.shutdown();
            numberOfAccountsToProcess.await();
            System.out.println("Done!");
        } catch (ServiceException e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
        System.exit(0);
    }

    public class GenerateEventsForAccountTask implements Runnable {
        private Account account;
        private CountDownLatch latch;

        public GenerateEventsForAccountTask(Account account, CountDownLatch numberOfAccountsToProcess) {
            this.account = account;
            this.latch = numberOfAccountsToProcess;
        }

        @Override
        public void run() {
            try {
                Mailbox mbox = MailboxManager.getInstance().getMailboxByAccount(account, MailboxManager.FetchMode.DO_NOT_AUTOCREATE);
                if(mbox != null) {
                    StringBuilder queryBuilder = new StringBuilder();
                    queryBuilder.append("after:").append(getOneYearBackDate(account.getLocale()));
                    SearchParams params = new SearchParams();
                    params.setQueryString(queryBuilder.toString());
                    params.setLocale(account.getLocale());
                    Set<MailItem.Type> types = new HashSet<>();
                    types.add(MailItem.Type.MESSAGE);
                    params.setTypes(types);
                    params.setLimit(10000000);
                    params.setSortBy(SortBy.DATE_ASC);

                    ZimbraQueryResults results = mbox.index.search(new OperationContext(mbox), params);
                    generateEvents(mbox, results);
                }
            } catch (ServiceException e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        }

        private void generateEvents(Mailbox mbox, ZimbraQueryResults results) throws ServiceException {
            EventLogger logger = EventLogger.getEventLogger();
            while (results.hasNext()) {
                ZimbraHit result = results.getNext();
                MailItem mailItem = result.getMailItem();
                Message msg = mbox.getMessageById(new OperationContext(mbox), result.getItemId());
                //SENT folder ID is 5. Emails in the SENT folder should generate SENT events.
                if(mailItem.getFolderId() == 5) {
                    logger.log(Event.generateSentEvents(msg, mailItem.getDate()));
                } //Generate RECEIVED and AFFINITY events for emails in all other folders.
                else {
                    logger.log(Event.generateReceivedEvent(msg, mbox.getAccount().getName(), mailItem.getDate()));
                    logger.log(Event.generateAffinityEvents(msg, mbox.getAccount().getName(), mailItem.getDate()));
                }
            }
        }
    }
}
