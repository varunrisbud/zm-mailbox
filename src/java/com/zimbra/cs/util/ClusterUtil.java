/*
 * Created on 2005. 1. 19.
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.zimbra.cs.util;

import java.util.HashMap;
import java.util.Map;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.util.Config;
import com.zimbra.soap.Element;
import com.zimbra.soap.Element.XMLElement;

import com.zimbra.cs.localconfig.LC;
import java.io.File;

/**
 * @author jhahm
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class ClusterUtil {
    
    // public constants
    public static final String SERVERS_KEY = "servers";
    public static final String SERVICES_KEY = "services";

    public static final String ZIMBRA_CLUSTER_ROOT = LC.get("zimbra_cluster_root");
    
    // private constants
    private static Log mLog = LogFactory.getLog(ClusterUtil.class);

    //private static final String CLUSTAT_SCRIPT =
    //"/usr/sbin/clustat";
    private static final String CLUSTAT_SCRIPT = getPathRelativeToZimbraClusterHome("bin/zmclustat").getAbsolutePath();
    //private static final String CMAN_TOOL_SCRIPT =
    //"/sbin/cman_tool";
    private static final String CMAN_TOOL_SCRIPT = getPathRelativeToZimbraClusterHome("bin/zmcman_tool").getAbsolutePath();
    private static final String CLUSVCADM_SCRIPT = getPathRelativeToZimbraClusterHome("bin/zmclusvcadm").getAbsolutePath();

    private static final Pattern clustatPattern = Pattern.compile(" *[^ ]*([^=]*)=\"([^\"]*)");
    
    private static boolean onWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.startsWith("win");
    }

    /**
     * Returns a File object representing the path relative to the
     * Zimbra home directory.
     * @param path
     * @return
     */
    public static File getPathRelativeToZimbraClusterHome(String path) {
        char first = path.charAt(0);
        if (first == File.separatorChar || first == '/')
            return new File(path);

    	String home = ZIMBRA_CLUSTER_ROOT;
    	return new File(home, path);
    }

    /**
     * returns a map containing two arrays - services, and servers.
     * The arrays are of ClusterUtil.ServerStatus, and
     * ClusterUtil.services types, and contain information on the
     * server, and service status. 
     *
     * This should only works on a RedHat platform. Windows dev boxes
     * will get a null return value.
     */
    public static Map getClusterStatus ( ) throws ServiceException {
        if (onWindows()){
            mLog.info("Can't check cluster status on a Windows dev box");
            return null;
        }
        
        // clustat doesn't show nodes that are dead or coming up ...
        // Thus, we have to run cman_tool as well, and figure out
        // which nodes are dead, if any.
        StringBuffer clustatCmd = new StringBuffer(CLUSTAT_SCRIPT);
        clustatCmd.append(" -x");
        mLog.info("  will execute \"" + clustatCmd.toString() + "\"");

        StringBuffer cmantoolCmd = new StringBuffer(CMAN_TOOL_SCRIPT);
        //cmantoolCmd.append(" -t 2 nodes| /usr/bin/cut -c18,20-1000");
        mLog.info("  will execute \"" + cmantoolCmd.toString() + "\"");
        
        ExecUtil.ProcessOutput clustatProc = ExecUtil.exec(clustatCmd.toString());
        String clustatOutput = clustatProc.stdout;
        if (clustatProc.exitValue != 0) {
            throw new ClusterScriptException(clustatCmd.toString() + " returned " + clustatProc.exitValue);            
        }


        ExecUtil.ProcessOutput cmanProc = ExecUtil.exec(cmantoolCmd.toString());
        String cmanToolOutput = cmanProc.stdout;
        if (cmanProc.exitValue != 0) {
            throw new ClusterScriptException(cmantoolCmd.toString() + " returned " + cmanProc.exitValue);
        }
        
        
//         String clustatOutput = getDummyClustatOutput();
//         String cmanToolOutput = getDummyCmanToolOutput();
        
        //mLog.info("clustatOutput = " + clustatOutput);
        //mLog.info("cman_tool output = " + cmanToolOutput);
        
        String [] lines = cmanToolOutput.split("\\n");
        String [] vals;
        StringBuffer xml = new StringBuffer();
        HashMap status = new HashMap();
        ClusterUtil.ServerStatus [] servers = new ClusterUtil.ServerStatus [lines.length - 1];
        
        for (int i = 1 ; i < lines.length; ++i ){
            //mLog.info("  line["+i+"] = " + lines[i]);
            vals = lines[i].split("\\s\\s");
//             for (int j = 0 ; j < vals.length; ++j) {
//                 mLog.info("  vals["+j+"] = " + vals[j]);   
//             }
            ClusterUtil.ServerStatus ser = new ClusterUtil.ServerStatus(vals[1]);
            if (vals[0].equals("M")) {
                ser.status = 1;
            } else {
                ser.status = 0;
            }
            servers[ i - 1 ] = ser;            
        }

        status.put(ClusterUtil.SERVERS_KEY, servers);
        String servicesStr = clustatOutput.substring(clustatOutput.indexOf("<groups>") + 9, 
                                                     clustatOutput.indexOf("</groups>") - 2);

        lines = servicesStr.split("\\n");
        Matcher m = null;
        String key;
        String val;
        ClusterUtil.ServiceStatus s = null;
        ClusterUtil.ServiceStatus [] services = new ClusterUtil.ServiceStatus [lines.length];
        for (int i = 0; i < lines.length ; ++i) {
            //mLog.info("*********** lines[" + i + "]= " + lines[i]);
            m = clustatPattern.matcher(lines[i]);
            s = new ClusterUtil.ServiceStatus();
            while (m.find()){
                key = m.group(1).trim();
                val = m.group(2).trim();

                //mLog.info("key = " + key + " val = " + val);
                
                if (key.equals("name")){
                    s.name = val;
                } else if (key.equals("owner")){
                    s.owner = val;
                } else if (key.equals("last_owner")){
                    s.lastOwner = val;
                } else if (key.equals("state_str")){
                    s.status = val;
                } else if (key.equals("restarts")){
                    s.restarts = val;
                }
            }
            services[i] = s;
        }
        status.put(ClusterUtil.SERVICES_KEY, services);
        return status;
    }
    
    public static class ServerStatus 
    {
        public String name;
        public int status;

        public ServerStatus (String aName){
            name = aName;
        }

        public ServerStatus (String aName, int aStatus){
            name = aName;
            status = aStatus;
        }
    }

    public static class ServiceStatus 
    {
        public String name;
        public String status;
        public String owner;
        public String lastOwner;
        public String restarts;

        public ServiceStatus (){ }

    }
    

    private static String getDummyClustatOutput() {
        StringBuffer tmp = new StringBuffer();
        tmp.append("<clustat version=\"4.0\">\n");
        tmp.append("  <quorum quorate=\"1\" groupmember=\"1\"/>\n");
        tmp.append("  <nodes>");
        tmp.append("    <node name=\"barbara.liquidsys.com\" state=\"1\" nodeid=\"0x0000000000000002\"/>\n");
        tmp.append("    <node name=\"tweek.liquidsys.com\" state=\"1\" nodeid=\"0x0000000000000003\"/>\n");
        tmp.append("    <node name=\"jenna.liquidsys.com\" state=\"1\" nodeid=\"0x0000000000000001\"/>\n");
        tmp.append("  </nodes>\n");
        tmp.append("  <groups>\n");
        tmp.append("    <group name=\"mail1.liquidsys.com\" state=\"112\" state_str=\"started\"  owner=\"barbara.liquidsys.com\" last_owner=\"barbara.liquidsys.com\" restarts=\"1\"/>\n");        
        tmp.append("    <group name=\"mail2.liquidsys.com\" state=\"112\" state_str=\"started\"  owner=\"jenna.liquidsys.com\" last_owner=\"tweek.liquidsys.com\" restarts=\"0\"/>\n");
        tmp.append("  </groups>\n");
        tmp.append("</clustat>\n");
        return tmp.toString();
    }

    private static String getDummyCmanToolOutput(){
        StringBuffer tmp = new StringBuffer();
        
        tmp.append("t  Name\n");
        tmp.append("M  jenna.liquidsys.com\n");
        tmp.append("M  barbara.liquidsys.com\n");
        tmp.append("M  tweek.liquidsys.com\n");
        return tmp.toString();
    }


    public static void failoverService (String serviceName, String newServerName) throws ServiceException{
        if (onWindows()){
            mLog.info("Cluster services are not available on a Windows dev box");
//             try {
//                 Thread.sleep(10000);
//             } catch (Exception e) {
//                 e.printStackTrace();
//             }
            
            return;
        }
        
        // clustat doesn't show nodes that are dead or coming up ...
        // Thus, we have to run cman_tool as well, and figure out
        // which nodes are dead, if any.
        StringBuffer clusvcadmCmd = new StringBuffer("sudo ");
        clusvcadmCmd.append(CLUSVCADM_SCRIPT);
        clusvcadmCmd.append(" -r ");
        clusvcadmCmd.append(serviceName);
        clusvcadmCmd.append(" -m ");
        clusvcadmCmd.append(newServerName);
        
        mLog.info("  will execute \"" + clusvcadmCmd.toString() + "\"");        

        ExecUtil.ProcessOutput clusvcadmProc = ExecUtil.exec(clusvcadmCmd.toString());
        String clusvcadmOutput = clusvcadmProc.stdout;

        if (clusvcadmProc.exitValue != 0) {
            throw new ClusterScriptException(clusvcadmCmd.toString() + " returned " + clusvcadmProc.exitValue);
        }
        //System.out.println("  output = " + clusvcadmOutput);
        
    }
    
    public static class ClusterScriptException extends ServiceException 
    {
        ClusterScriptException(String message)
        {
            super(ServiceException.FAILURE, message, ServiceException.RECEIVERS_FAULT);
        }        
    }
    
    
}
