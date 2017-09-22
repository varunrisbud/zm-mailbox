package com.zimbra.cs.index.contactanalytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.ImmutableList;
import com.zimbra.common.mime.InternetAddress;
import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.MailboxTestUtil;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

public class InMemoryInteractionFrequencyRecorderTest {
    private static Provisioning prov;
    private static Account acct;
    private static Mailbox mbox;
    private static List<Contact> testContactList;
    private InteractionFrequencyRecorder interactionFrequencyRecorder;

    @BeforeClass
    public static void init() throws Exception {
        MailboxTestUtil.initServer();
        prov = Provisioning.getInstance();
        acct = prov.createAccount("interactionTest@zimbra.com", "test123", new HashMap<String, Object>());
        mbox = MailboxManager.getInstance().getMailboxByAccountId(acct.getId());
        testContactList = mbox.createAutoContact(null, ImmutableList.of(new InternetAddress("Test 1", "TEST1@zimbra.com"),
                new InternetAddress("Test 2", "TEST2@zimbra.com")));
    }

    @Before
    public void setup() throws ServiceException {
        InteractionFrequencyRecorder.setFactory(InMemoryInteractionFrequencyRecorderFactory.class);
        interactionFrequencyRecorder = InMemoryInteractionFrequencyRecorder.getFactory().getInteractionFrequencyRecorder(acct);
    }

    @Test
    public void testInteractions() {
        Calendar cal = Calendar.getInstance();
        cal.setWeekDate(2017, 27, 3);
        interactionFrequencyRecorder.addInteraction(testContactList.get(0), cal.getTimeInMillis());
        interactionFrequencyRecorder.addInteraction(testContactList.get(0), cal.getTimeInMillis());

        interactionFrequencyRecorder.addInteraction(testContactList.get(1), cal.getTimeInMillis());

        cal.setWeekDate(2017, 37, 4);
        interactionFrequencyRecorder.addInteraction(testContactList.get(0), System.currentTimeMillis());
        interactionFrequencyRecorder.addInteraction(testContactList.get(0), System.currentTimeMillis());

        interactionFrequencyRecorder.addInteraction(testContactList.get(1), System.currentTimeMillis());

        InteractionFrequencyGraphResult result = interactionFrequencyRecorder.getInteractionFrequency(testContactList.get(0), 0L, 0L, InteractionFrequencyRecorder.AggregationUnit.WEEK);

        assertNotNull("Interaction Frequency Graph Result is null", result);

        assertEquals("Actual contact is not same as contact used to initialize", testContactList.get(0), result.getWith());
        assertEquals("X axis label should be week", InteractionFrequencyRecorder.AggregationUnit.WEEK.name(), result.getXlabel());
        assertEquals("Y axis label should be frequency", "Frequency", result.getYlabel());
        assertEquals("Actual aggregationUnit is not same as aggregationUnit used to initialize", InteractionFrequencyRecorder.AggregationUnit.WEEK, result.getAggregationUnit());

        assertNotNull("Data points are null", result.getData());
        assertEquals("We should get 2 data points back", 2, result.getData().size());
        assertEquals("First data point should be for week 37", "37", result.getData().get(0).getX());
        assertEquals("First data point should be for week 37 with Frequency 2", 2, result.getData().get(0).getY().intValue());
        assertEquals("Second data point should be for week 27", "27", result.getData().get(1).getX());
        assertEquals("First data point should be for week 27 with Frequency 2", 2, result.getData().get(1).getY().intValue());

        InteractionFrequencyGraphResult result1 = interactionFrequencyRecorder.getInteractionFrequency(testContactList.get(1), 0L, 0L, InteractionFrequencyRecorder.AggregationUnit.WEEK);
        assertNotNull("Interaction Frequency Graph Result is null", result1);
        assertEquals("Actual contact is not same as contact used to initialize", testContactList.get(1), result1.getWith());
        assertNotNull("Data points are null", result1.getData());
        assertEquals("We should get 2 data points back", 2, result1.getData().size());
        assertEquals("First data point should be for week 37", "37", result1.getData().get(0).getX());
        assertEquals("First data point should be for week 37 with Frequency 1", 1, result1.getData().get(0).getY().intValue());
        assertEquals("Second data point should be for week 27", "27", result1.getData().get(1).getX());
        assertEquals("First data point should be for week 27 with Frequency 1", 1, result1.getData().get(1).getY().intValue());
    }
}