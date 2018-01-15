package com.zimbra.cs.event.recovery;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Provisioning;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;

public class EventBackfillUtil {
    private static Options OPTIONS = new Options();
    //TODO: use the options to initialize the EventBackFillFromDB::init method.
    //TODO: update zm-build/blob/develop/instructions/bundling-scripts/zimbra-core.sh to add jtnef-1.9.0.jar to /opt/zimbra/lib/jars/.
    //TODO: update zm-core-utils script src/bin/zmeventbackfillfromdb for any changes done to this class.
    //TODO: handle the account list if it is provided.
    static {
        OPTIONS.addOption("a", "account", true, "Comma-separated list of accounts for which events will be back filled. If not specified, all accounts will be back filled");
        OPTIONS.addOption("n", "num-threads", true, "Number of threads to use in the event back fill. If not set, defaults to 10");
        OPTIONS.addOption("slu", "solr-index-url", true, "Solr URL in the format as zimbraIndexURL. If not set, defaults to value already set for zimbraIndexURL attribute.");
        OPTIONS.addOption("elb", "event-logging-backend", true, "Solr URL in the format as zimbraEventLoggingBackends. If not set, defaults to value already set for zimbraEventLoggingBackends attribute.");
        OPTIONS.addOption("elnt", "event-logger-num-threads", true, "Number of threads of event logger. If not set, defaults to value set for EventLoggingNumThreads");
        OPTIONS.addOption("elbs", "event-logger-batch-size", true, "Batch size of event logger. If not set, defaults to value set for EventBatchMaxSize");
    }

    public static void main(String[] args) throws Exception {
        ZimbraLog.event.info("Starting the Event Backfill");
        try {
            Integer accNum = Integer.valueOf(args[0]) == -1 ? null : Integer.valueOf(args[0]);
            Integer batchSize = args.length > 1 ? Integer.valueOf(args[1]) : 10;
            Integer numThreads = args.length > 2 ? Integer.valueOf(args[2]) : 10;
            EventBackFillFromDB eventBackFillFromDB = new EventBackFillFromDB();
            eventBackFillFromDB.execute(accNum, batchSize, numThreads);
        } catch (ServiceException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
}
