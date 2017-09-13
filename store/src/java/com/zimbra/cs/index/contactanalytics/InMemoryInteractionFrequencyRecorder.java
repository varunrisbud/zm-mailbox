package com.zimbra.cs.index.contactanalytics;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.Contact;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryInteractionFrequencyRecorder extends InteractionFrequencyRecorder {
    Multimap<Contact, InteractionRecord> interactionMap = HashMultimap.create();
    @Override
    public void addInteraction(Contact with, long timestamp) {
        interactionMap.put(with, new InteractionRecord(timestamp));
    }

    @Override
    public InteractionFrequencyGraphResult getInteractionFrequency(Contact with, long from, long to, AggregationUnit aggregationUnit) {
        Collection<InteractionRecord> interactionRecordsList = interactionMap.get(with);
        Map<Integer, Long> resultData = interactionRecordsList.stream().collect(Collectors.groupingBy(InteractionRecord::getWeekOfYear, Collectors.counting()));
        InteractionFrequencyGraphResult result = new InteractionFrequencyGraphResult(with, from, to, aggregationUnit);
        resultData.forEach((k, v) -> result.addDataPoint(k.toString(), v.intValue()));
        return result;
    }

    private class InteractionRecord {
        private int year;
        private int month;
        private int weekOfYear;
        private int date;
        private int hour;
        private long timestamp;

        public InteractionRecord(long timestamp) {
            this.timestamp = timestamp;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(timestamp);
            this.year = cal.get(Calendar.YEAR);
            this.month = cal.get(Calendar.MONTH);
            this.weekOfYear = cal.get(Calendar.WEEK_OF_YEAR);
            this.date = cal.get(Calendar.DATE);
            this.hour = cal.get(Calendar.HOUR_OF_DAY);
        }

        public int getYear() {
            return year;
        }

        public int getMonth() {
            return month;
        }

        public int getWeekOfYear() {
            return weekOfYear;
        }

        public int getDate() {
            return date;
        }

        public int getHour() {
            return hour;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
