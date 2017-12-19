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
    public static void init() throws ServiceException, IOException {
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
        prov.getLocalServer().setEventBatchMaxSize(1);

        EventLogger.getEventLogger().startupEventNotifierExecutor();
    }

    public static void cleanup() throws Exception {
        DbPool.shutdown();
        IndexStore.getFactory().destroy();
        StoreManager.getInstance().shutdown();
        EventLogger.getEventLogger().shutdownEventLogger();
    }

    public static List<NamedEntry> getAllAccounts() throws ServiceException {
        SearchDirectoryOptions options = new SearchDirectoryOptions();
        ZLdapFilter filter = ZLdapFilterFactory.getInstance().allAccounts();
        options.setFilter(filter);
        options.setTypes(SearchDirectoryOptions.ObjectType.accounts);
        List<NamedEntry> accounts = Provisioning.getInstance().searchDirectory(options);
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

    public void execute() throws Exception {
        ZimbraLog.event.info("Starting the Event Backfill");
        try {
            init();
            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<NamedEntry> accounts = getAllAccounts();
            CountDownLatch numberOfAccountsToProcess = new CountDownLatch(accounts.size());
            for (NamedEntry acc : accounts) {
                Account account = Provisioning.getInstance().getAccountById(acc.getId());
                executor.execute(new GenerateEventsForAccountTask(account, numberOfAccountsToProcess));
            }
            executor.shutdown();
            System.out.println("Waiting for all account to process...");
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
            } catch (ServiceException e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        }

        private void generateEvents(Mailbox mbox, ZimbraQueryResults results) throws ServiceException {
            List<Event> finalEvents = new ArrayList<>();
            EventLogger logger = EventLogger.getEventLogger();
            while (results.hasNext()) {
                ZimbraHit result = results.getNext();
                MailItem mailItem = result.getMailItem();
                Message msg = mbox.getMessageById(new OperationContext(mbox), result.getItemId());
                //SENT folder ID is 5. Emails in the SENT folder should generate SENT events.
                if(mailItem.getFolderId() == 5) {
                    logger.log(Event.generateSentEvents(msg, mailItem.getDate()));
                } //Generate RECEIVED events for emails in all other folders.
                else {
                    logger.log(Event.generateReceivedEvent(msg, mbox.getAccount().getName(), mailItem.getDate()));
                }
                //Generate AFFINITY events for all emails.
                logger.log(Event.generateAffinityEvents(msg, mbox.getAccount().getName(), mailItem.getDate()));
            }
        }
    }

    public static class Email {
        String accountId;
        int id;
        int folder_id;
        String sender;
        String recipients;
        String subject;

        public Email(String accountId, int id, int folder_id, String sender, String recipients, String subject) {
            this.accountId = accountId;
            this.id = id;
            this.folder_id = folder_id;
            this.sender = sender;
            this.recipients = recipients;
            this.subject = subject;
        }

        @Override
        public String toString() {
            return "Email{" +
                    "accountId='" + accountId + '\'' +
                    ", id=" + id +
                    ", folder_id=" + folder_id +
                    ", sender='" + sender + '\'' +
                    ", recipients='" + recipients + '\'' +
                    ", subject='" + subject + '\'' +
                    '}';
        }

    }
}
