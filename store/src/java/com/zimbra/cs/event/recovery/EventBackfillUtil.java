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

public class EventBackfillUtil {

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

    public static void main(String[] args) throws Exception {
        ZimbraLog.event.info("Starting the Event Backfill");
        try {
            init();
            List<NamedEntry> accounts = getAllAccounts();
            int accNum = Integer.valueOf(args[0]);
            Account acc = Provisioning.getInstance().getAccountById(accounts.get(accNum).getId());
            testProc(acc);
        } catch (ServiceException e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
        System.exit(0);
    }

    public static List<Event> testProc(Account acc) throws ServiceException {
        Mailbox mbox = MailboxManager.getInstance().getMailboxByAccount(acc, MailboxManager.FetchMode.DO_NOT_AUTOCREATE);

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("after:").append(getOneYearBackDate(acc.getLocale()));
        SearchParams params = new SearchParams();
        params.setQueryString(queryBuilder.toString());
        params.setLocale(acc.getLocale());
        Set<MailItem.Type> types = new HashSet<>();
        types.add(MailItem.Type.MESSAGE);
        params.setTypes(types);
        params.setLimit(10000);
        params.setSortBy(SortBy.DATE_ASC);

        ZimbraQueryResults results = mbox.index.search(new OperationContext(mbox), params);
        List<Event> finalEvents = generateEvents(mbox, results);
        EventLogger.getEventLogger().log(finalEvents);
        return finalEvents;
    }

    public static List<Event> generateEvents(Mailbox mbox, ZimbraQueryResults results) throws ServiceException {
        List<Event> finalEvents = new ArrayList<>();
        while (results.hasNext()) {
            ZimbraHit result = results.getNext();
            MailItem mailItem = result.getMailItem();
            Email email = new Email(mailItem.getAccountId(), mailItem.getId(),mailItem.getFolderId(),  mailItem.getSender(), mailItem.getSortRecipients(), mailItem.getSubject());
            System.out.println(email);
            System.out.println(mailItem.getFolderUuid());
            Message msg = mbox.getMessageById(new OperationContext(mbox), result.getItemId());
            System.out.println(msg);

            List<Event> sentEvent = Event.generateSentEvents(msg, mailItem.getDate());
            finalEvents.addAll(sentEvent);
            System.out.println("Printing Sent Events");
            for (Event event : sentEvent) {
                System.out.println(event);
            }

            Event recvEvent = Event.generateReceivedEvent(msg, mbox.getAccount().getName(), mailItem.getDate());
            finalEvents.add(recvEvent);
            System.out.println("Printing Recv Events");
            System.out.println(recvEvent);

            List<Event> affEvent = Event.generateAffinityEvents(msg, mbox.getAccount().getName(), mailItem.getDate());
            finalEvents.addAll(affEvent);
            System.out.println("Printing affinity Events");
            for (Event event : affEvent) {
                System.out.println(event);
            }
            System.out.println("****************************************************");
        }
        System.out.println("Existed the while loop");
        return finalEvents;
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
