package com.zimbra.cs.index.contactanalytics;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.Contact;

public abstract class InteractionFrequencyRecorder {
    abstract void addInteraction(Contact with, long timestamp);
    abstract InteractionFrequencyGraphResult getInteractionFrequency(Contact with, long from, long to, AggregationUnit aggregationUnit);

    enum AggregationUnit {
        MONTH, WEEK, DAY, HOUR
    }

    protected static Factory factory;

    public static final void setFactory(Class<? extends Factory> factoryClass) throws ServiceException {
        String className = factoryClass.getName();
        ZimbraLog.store.info("setting InteractionFrequencyRecorder.Factory class %s", className);
        try {
            factory = factoryClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw ServiceException.FAILURE(String.format("unable to initialize InteractionFrequencyRecorder factory %s", className), e);
        }
    }

    public static Factory getFactory() {
        return factory;
    }

    interface Factory {
        InteractionFrequencyRecorder getInteractionFrequencyRecorder(Account acct) throws ServiceException;
    }
}