package com.zimbra.cs.index.contactanalytics;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;

import java.util.HashMap;
import java.util.Map;

public class InMemoryInteractionFrequencyRecorderFactory implements InMemoryInteractionFrequencyRecorder.Factory {
    static Map<String, InteractionFrequencyRecorder> accountToIFRMap = new HashMap<>();

    @Override
    public InteractionFrequencyRecorder getInteractionFrequencyRecorder(Account acct) throws ServiceException {
        if (!accountToIFRMap.containsKey(acct.getId())) {
            accountToIFRMap.put(acct.getId(), new InMemoryInteractionFrequencyRecorder());
        }
        return accountToIFRMap.get(acct.getId());
    }
}
