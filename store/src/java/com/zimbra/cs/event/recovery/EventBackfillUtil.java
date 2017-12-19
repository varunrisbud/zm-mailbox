package com.zimbra.cs.event.recovery;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;

public class EventBackfillUtil {

    public static void main(String[] args) throws Exception {
        ZimbraLog.event.info("Starting the Event Backfill");
        try {
            int accNum = Integer.valueOf(args[0]);
            EventBackFillFromDB eventBackFillFromDB = new EventBackFillFromDB();
            eventBackFillFromDB.execute();
        } catch (ServiceException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
}
