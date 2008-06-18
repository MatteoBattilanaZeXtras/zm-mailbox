package com.zimbra.cs.util;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.SortedSet;
import java.util.Formatter;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;

import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;
import com.zimbra.common.util.CliUtil;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.service.ServiceException;

import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Config;
import com.zimbra.cs.account.Server;

import com.zimbra.cs.extension.ExtensionDispatcherServlet;

public class ProxyConfGen
{
    private static class ProxyConfException extends Exception {
        public ProxyConfException(String msg) {
            super(msg);
        }
    }

    private static Log mLog = LogFactory.getLog (ProxyConfGen.class);
    private static Options mOptions = new Options();
    private static boolean mDryRun = false;
    private static String mWorkingDir = "/opt/zimbra";
    private static String mTemplateDir = mWorkingDir + "/conf";
    private static String mConfDir = mWorkingDir + "/conf";
    private static String mConfPrefix = "nginx.conf";
    private static String mTemplatePrefix = mConfPrefix;
    private static String mTemplateSuffix = ".template";
    private static Map<String, String> mVars = new HashMap<String, String>();
    private static Provisioning mProv = null;

    static
    {
        mOptions.addOption("h", "help", false, "show this usage text");
        mOptions.addOption("v", "verbose", false, "be verbose");

        mOptions.addOption("w", "workdir", true, "Proxy Working Directory (defaults to /opt/zimbra)");
        mOptions.addOption("t", "templatedir", true, "Proxy Template Directory (defaults to $workdir/conf)");
        mOptions.addOption("n", "dry-run", false, "Do not write any configuration, just show which files would be written");
        mOptions.addOption("d", "defaults", false, "Print default variable map");
        mOptions.addOption("D", "definitions", false, "Print variable map Definitions after loading LDAP configuration (and processing overrides)");
        mOptions.addOption("p", "prefix", true, "Config File prefix (defaults to nginx.conf)");
        mOptions.addOption("P", "template-prefix", true, "Template File prefix (defaults to $prefix)");

        Option cOpt = new Option("c","config",true,"Override a config variable. Argument format must be name=value. For list of names, run with -d or -D");
        cOpt.setArgs(Option.UNLIMITED_VALUES);
        mOptions.addOption(cOpt);
    }

    private static void usage(String errmsg)
    {
        if (errmsg != null) {
            System.out.println(errmsg);
        }
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("ProxyConfGen [options] ",
            "where [options] are one of:", mOptions,
            "ProxyConfGen generates the NGINX Proxy configuration files");
    }

    private static CommandLine parseArgs(String args[])
    {
        CommandLineParser parser = new GnuParser();
        CommandLine cl = null;
        try {
            cl = parser.parse(mOptions, args);
        } catch (ParseException pe) {
            usage(pe.getMessage());
            return cl;
        }

        return cl;
    }

    public static String getCoreConf () {
        return mConfPrefix;
    }

    public static String getCoreConfTemplate () {
        return getCoreConf() + mTemplateSuffix;
    }

    public static String getMainConf () {
        return mConfPrefix + ".main";
    }

    public static String getMainConfTemplate () {
        return getMainConf() + mTemplateSuffix;
    }

    public static String getMemcacheConf () {
        return mConfPrefix + ".memcache";
    }

    public static String getMemcacheConfTemplate () {
        return getMemcacheConf() + mTemplateSuffix;
    }

    public static String getMailConf () {
        return mConfPrefix + ".mail";
    }

    public static String getMailConfTemplate () {
        return getMailConf() + mTemplateSuffix;
    }

    public static String getMailImapConf () {
        return getMailConf() + ".imap";
    }

    public static String getMailImapConfTemplate () {
        return getMailImapConf() + mTemplateSuffix;
    }

    public static String getMailImapSConf () {
        return getMailConf() + ".imaps";
    }

    public static String getMailImapSConfTemplate () {
        return getMailImapSConf() + mTemplateSuffix;
    }

    public static String getMailPop3Conf () {
        return getMailConf() + ".pop3";
    }

    public static String getMailPop3ConfTemplate () {
        return getMailPop3Conf() + mTemplateSuffix;
    }

    public static String getMailPop3SConf () {
        return getMailConf() + ".pop3s";
    }

    public static String getMailPop3SConfTemplate () {
        return getMailPop3SConf() + mTemplateSuffix;
    }

    public static String getWebConf () {
        return mConfPrefix + ".web";
    }

    public static String getWebConfTemplate () {
        return getWebConf() + mTemplateSuffix;
    }

    public static String getWebHttpConf () {
        return getWebConf() + ".http";
    }

    public static String getWebHttpConfTemplate () {
        return getWebHttpConf() + mTemplateSuffix;
    }

    public static String getWebHttpSConf () {
        return getWebConf() + ".https";
    }

    public static String getWebHttpSConfTemplate () {
        return getWebHttpSConf() + mTemplateSuffix;
    }

    public static String getWebHttpModeConf (String mode) {
        return getWebHttpConf() + ".mode-" + mode;
    }

    public static String getWebHttpModeConfTemplate (String mode) {
        return getWebHttpModeConf(mode) + mTemplateSuffix;
    }

    public static String getWebHttpSModeConf (String mode) {
        return getWebHttpSConf() + ".mode-" + mode;
    }

    public static String getWebHttpSModeConfTemplate (String mode) {
        return getWebHttpSModeConf(mode) + mTemplateSuffix;
    }

    public static void expandTemplate (File tFile, File wFile)
        throws ProxyConfException
    {
        try {
            String tf = tFile.getAbsolutePath();
            String wf = wFile.getAbsolutePath();

            mLog.info("Expanding template:" + tf + " to file:" + wf);

            if (mDryRun) {
                return;
            }

            if (!tFile.exists()) {
                throw new ProxyConfException("Template file " + tf + " does not exist");
            }

            try {
                BufferedReader r = new BufferedReader(new FileReader (tFile));
                BufferedWriter w = new BufferedWriter(new FileWriter (wf));

                String i,o;

                while ((i = r.readLine()) != null) {
                    i = StringUtil.fillTemplate(i,mVars);
                    w.write(i);
                    w.newLine();
                }

                w.close();
                r.close();
            } catch (IOException ie) {
                throw new ProxyConfException("Cannot expand template file: " + ie.getMessage());
            }

        } catch (SecurityException se) {
            throw new ProxyConfException ("Cannot expand template: " + se.getMessage());
        }
    }

    /* Print the variable map */
    public static void displayVariables ()
    {
        /* for (Map.Entry<String, String> e : mVars.entrySet()) {
            System.out.println (e.getKey() + " --> " + e.getValue());
        } */

        SortedSet <String> sk = new TreeSet <String> (mVars.keySet());

        for (String k : sk) {
            Formatter f = new Formatter();
            f.format("%1$-40s%2$s", k, mVars.get(k));
            System.out.println (f.toString());
        }
    }

    public static void buildDefaultVars ()
    {
        mVars.put("core.workdir",                   mWorkingDir);
        mVars.put("core.cprefix",                   mConfPrefix);
        mVars.put("core.tprefix",                   mTemplatePrefix);
        mVars.put("main.user",                      "zimbra");
        mVars.put("main.group",                     "zimbra");
        mVars.put("main.workers",                   "4");
        mVars.put("main.pidfile",                   "log/nginx.pid");
        mVars.put("main.logfile",                   "log/nginx.log");
        mVars.put("main.loglevel",                  "info");
        mVars.put("main.workers",                   "10240");
        mVars.put("memcache.:servers",              "");            /* multiline conf */
        mVars.put("memcache.timeout",               "3s");
        mVars.put("memcache.reconnect",             "1m");
        mVars.put("memcache.ttl",                   "1h");
        mVars.put("memcache.unqual",                "off");
        mVars.put("mail.timeout",                   "3600s");
        mVars.put("mail.passerrors",                "on");
        mVars.put("mail.:auth_http",                 "");            /* multiline conf */
        mVars.put("mail.authwait",                  "10s");
        mVars.put("mail.pop3capa",                  "\"TOP\" \"USER\" \"UIDL\" \"EXPIRE 31 USER\"");
        mVars.put("mail.imapcapa",                  "\"IMAP4rev1\" \"ID\" \"LITERAL+\" \"SASL-IR\"");
        mVars.put("mail.imapid",                    "\"NAME\" \"nginx\" \"VERSION\" \"0\" \"RELEASE\" \"1\"");
        mVars.put("mail.duser",                     "nginx");
        mVars.put("mail.dpasswd",                   "nginx123");
        mVars.put("mail.saslapp",                   "nginx");
        mVars.put("mail.ipmax",                     "0");
        mVars.put("mail.ipttl",                     "3600s");
        mVars.put("mail.iprej",                     "Login rejected from this IP");
        mVars.put("mail.usermax",                   "0");
        mVars.put("mail.userttl",                   "3600s");
        mVars.put("mail.userrej",                   "Login rejected for this user");
        mVars.put("mail.upstream.pop3xoip",         "on");
        mVars.put("mail.upstream.imapid",           "on");
        mVars.put("mail.ssl.preferserverciphers",   "on");
        mVars.put("mail.ssl.cert",                  "/opt/zimbra/conf/nginx.crt");
        mVars.put("mail.ssl.key",                   "/opt/zimbra/conf/nginx.key");
        mVars.put("mail.ssl.ciphers",               "!SSLv2:!MD5:HIGH");
        mVars.put("mail.imap.authplain.enabled",    "");
        mVars.put("mail.imap.authgssapi.enabled",   "");
        mVars.put("mail.pop3.authplain.enabled",    "");
        mVars.put("mail.pop3.authgssapi.enabled",   "");
        mVars.put("mail.imap.literalauth",          "on");
        mVars.put("mail.imap.port",                 "143");
        mVars.put("mail.imap.tls",                  "only");
        mVars.put("mail.imaps.port",                "993");
        mVars.put("mail.pop3.port",                 "110");
        mVars.put("mail.pop3.tls",                  "only");
        mVars.put("mail.pop3s.port",                "995");
        mVars.put("mail.enabled",                   "");
        mVars.put("web.mailmode",                   "both");
        mVars.put("web.upstream.name",              "zimbra");
        mVars.put("web.upstream.:servers",          "");            /* multiline conf */
        mVars.put("web.:routehandlers",             "");            /* multiline conf */
        mVars.put("web.routetimeout",               "15s");
        mVars.put("web.uploadmax",                  "10485760");
        mVars.put("web.http.port",                  "80");
        mVars.put("web.http.maxbody",               "10485760");
        mVars.put("web.https.port",                 "443");
        mVars.put("web.https.maxbody",              "10485760");
        mVars.put("web.ssl.cert",                   "/opt/zimbra/conf/nginx.crt");
        mVars.put("web.ssl.key",                    "/opt/zimbra/conf/nginx.key");
        mVars.put("web.http.uport",                 "7070");
        mVars.put("web.enabled",                    "");
    }

    /* update the default variable map from the active configuration */
    public static void updateDefaultVars ()
        throws ServiceException
    {
        Config config = mProv.getConfig();
        Formatter f = null;

        f = new Formatter();
        int ipmax = config.getIntAttr("zimbraReverseProxyIPLoginLimit",0);
        f.format("%d",ipmax);
        mVars.put("mail.ipmax", f.toString());

        f = new Formatter();
        int ipttl = config.getIntAttr("zimbraReverseProxyIPLoginLimitTime",3600);
        f.format("%ds",ipttl);
        mVars.put("mail.ipttl", f.toString());

        f = new Formatter();
        int usermax = config.getIntAttr("zimbraReverseProxyUserLoginLimit",0);
        f.format("%d",usermax);
        mVars.put("mail.usermax", f.toString());

        f = new Formatter();
        int userttl = config.getIntAttr("zimbraReverseProxyUserLoginLimitTime",3600);
        f.format("%ds",userttl);
        mVars.put("mail.userttl", f.toString());

        boolean pop3xoip = config.getBooleanAttr("zimbraReverseProxySendPop3Xoip",true);
        if (pop3xoip) {
            mVars.put("mail.upstream.pop3xoip", "on");
        } else {
            mVars.put("mail.upstream.pop3xoip", "off");
        }

        boolean imapid = config.getBooleanAttr("zimbraReverseProxySendImapId",true);
        if (imapid) {
            mVars.put("mail.upstream.imapid", "on");
        } else {
            mVars.put("mail.upstream.imapid", "off");
        }

        boolean imapgssapi = config.getBooleanAttr("zimbraReverseProxyImapSaslGssapiEnabled",false);
        if (imapgssapi) {
            mVars.put("mail.imap.authgssapi.enabled", "");
        } else {
            mVars.put("mail.imap.authgssapi.enabled", "#");
        }

        boolean pop3gssapi = config.getBooleanAttr("zimbraReverseProxyPop3SaslGssapiEnabled",false);
        if (pop3gssapi) {
            mVars.put("mail.pop3.authgssapi.enabled", "");
        } else {
            mVars.put("mail.pop3.authgssapi.enabled", "#");
        }

        String mailSslCiphers = config.getAttr("zimbraReverseProxySSLCiphers","!SSLv2:!MD5:HIGH");
        mVars.put("mail.ssl.ciphers",mailSslCiphers);

        /* TODO - The global Config for zimbraReverseProxyMailEnabled and zimbraReverseProxyHttpEnabled is true */
        boolean mailEnabled = config.getBooleanAttr("zimbraReverseProxyMailEnabled",false);
        if (mailEnabled) {
            mVars.put("mail.enabled","");
        } else {
            mVars.put("mail.enabled","#");
        }

        boolean webEnabled = config.getBooleanAttr("zimbraReverseProxyHttpEnabled",false);
        if (webEnabled) {
            mVars.put("web.enabled","");
        } else {
            mVars.put("web.enabled","#");
        }

        f = new Formatter();
        int mailPop3Port = config.getIntAttr(Provisioning.A_zimbraPop3ProxyBindPort,110);
        f.format("%s",mailPop3Port);
        mVars.put("mail.pop3.port", f.toString());

        f = new Formatter();
        int mailPop3SPort = config.getIntAttr(Provisioning.A_zimbraPop3SSLProxyBindPort,995);
        f.format("%s",mailPop3SPort);
        mVars.put("mail.pop3s.port", f.toString());

        f = new Formatter();
        int mailImapPort = config.getIntAttr(Provisioning.A_zimbraImapProxyBindPort,143);
        f.format("%s",mailImapPort);
        mVars.put("mail.imap.port", f.toString());

        f = new Formatter();
        int mailImapSPort = config.getIntAttr(Provisioning.A_zimbraImapSSLProxyBindPort,993);
        f.format("%s",mailImapSPort);
        mVars.put("mail.imaps.port", f.toString());

        String mailImapTls = config.getAttr("zimbraReverseProxyImapStartTlsMode","only");
        mVars.put("mail.imap.tls", mailImapTls);

        String mailPop3Tls = config.getAttr("zimbraReverseProxyPop3StartTlsMode","only");
        mVars.put("mail.pop3.tls", mailPop3Tls);

        f = new Formatter();
        int webUploadMax = config.getIntAttr("zimbraFileUploadMaxSize",10485760);
        f.format("%d",webUploadMax);
        mVars.put("web.uploadmax", f.toString());

        /* There are a few configuration parameters which need to be exploded to multi-line 
           configuration directives. These are:

           memcache.:servers
           mail.:auth_http
           web.upstream.:servers
           web.:routehandlers
         */

        /* First find out the memcached servers $(zmprov gamcs) */
        String mcsconf = "";
        List<Server> mcs = mProv.getAllServers(Provisioning.SERVICE_MEMCACHED);
        for (Server mc : mcs)
        {
            String sn = mc.getAttr(Provisioning.A_zimbraServiceHostname,"");
            Formatter m = new Formatter();
            m.format("  servers   %s:%d;\n", sn, mc.getIntAttr(Provisioning.A_zimbraMemcachedBindPort,11211));
            mcsconf = mcsconf + m.toString();

            mLog.info("Memcache: Added server " + sn + " to memcache block");
        }
        mVars.put("memcache.:servers", mcsconf);

        /* Now the route lookup handlers for mail and http -- $(zmprov garpu) */
        String auconf = "";
        String zmrconf = "";
        List<Server> ss = mProv.getAllServers();
        String REVERSE_PROXY_PATH = ExtensionDispatcherServlet.EXTENSION_PATH + "/nginx-lookup";
        int REVERSE_PROXY_PORT = 7072;
        for (Server s : ss)
        {
            String sn = s.getAttr(Provisioning.A_zimbraServiceHostname,"");
            boolean isTarget = s.getBooleanAttr(Provisioning.A_zimbraReverseProxyLookupTarget, false);
            if (isTarget) {
                Formatter m = new Formatter();
                m.format("  auth_http   %s:%d%s;\n", sn, REVERSE_PROXY_PORT, REVERSE_PROXY_PATH);
                auconf = auconf + m.toString();

                Formatter n = new Formatter();
                n.format("  zmroutehandlers   %s:%d%s;\n", sn, REVERSE_PROXY_PORT, REVERSE_PROXY_PATH);
                zmrconf = zmrconf + n.toString();

                mLog.debug("Route Lookup: Added server " + sn);
            }
        }
        mVars.put("mail.:auth_http", auconf);
        mVars.put("web.:routehandlers", zmrconf);

        /* Now the upstream http servers $(zmprov garpb) */
        String upconf = "";
        List<Server> us = mProv.getAllServers();
        for (Server u : us) {
            boolean isTarget = u.getBooleanAttr(Provisioning.A_zimbraReverseProxyLookupTarget, false);
            if (isTarget) {
                String mode = u.getAttr(Provisioning.A_zimbraMailMode, "");
                String sn = u.getAttr(Provisioning.A_zimbraServiceHostname, "");

                if (mode.equalsIgnoreCase(Provisioning.MAIL_MODE.http.toString()) ||
                    mode.equalsIgnoreCase(Provisioning.MAIL_MODE.both.toString())
                ) {
                    int sp = u.getIntAttr(Provisioning.A_zimbraMailPort,0);
                    Formatter m = new Formatter();
                    m.format("    server    %s:%d;\n", sn, sp);
                    upconf = upconf + m.toString();
                    mLog.info("Added server to HTTP upstream: " + sn);
                } else {
                    mLog.warn("Upstream: Ignoring server:" + sn + " because its mail mode is:" + mode);
                }
            }
        }
        mVars.put("web.upstream.:servers", upconf);
    }

    public static void overrideDefaultVars (CommandLine cl)
    {
        String[] overrides = cl.getOptionValues('c');

        if (overrides != null) {
            for (String o : overrides) {
                mLog.debug("Processing config override " + o);
                int e = o.indexOf ("=");
                if (e <= 0) {
                    mLog.info("Ignoring config override " + o + " because it is not of the form name=value");
                } else {
                    String k = o.substring(0,e);
                    String v = o.substring(e+1);

                    if (mVars.containsKey(k)) {
                        mLog.info("Overriding config variable " + k + " with " + v);
                        mVars.put(k,v);
                    } else {
                        mLog.info("Ignoring non-existent config variable " + k);
                    }
                }
            }
        }
    }

    public static void main (String[] args) throws ServiceException
    {
        CommandLine cl = parseArgs(args);

        if (cl == null) {
            System.exit(1);
        }

        if (cl.hasOption('v')) {
            CliUtil.toolSetup("DEBUG");
        } else {
            CliUtil.toolSetup("INFO");
        }

        if (cl.hasOption('h')) {
            usage(null);
            System.exit(0);
        }

        if (cl.hasOption('n')) {
            mDryRun = true;
        }

        if (cl.hasOption('w')) {
            mWorkingDir = cl.getOptionValue('w');
            mTemplateDir = mWorkingDir + "/conf";
            mConfDir = mWorkingDir + "/conf";
        }

        if (cl.hasOption('t')) {
            mTemplateDir = cl.getOptionValue('t');
        }

        mLog.debug("Working Directory: " + mWorkingDir);
        mLog.debug("Template Directory: " + mTemplateDir);

        if (cl.hasOption('p')) {
            mConfPrefix = cl.getOptionValue('p');
            mTemplatePrefix = mConfPrefix;
        }

        if (cl.hasOption('P')) {
            mTemplatePrefix = cl.getOptionValue('P');
        }

        mLog.debug("Config File Prefix: " + mConfPrefix);
        mLog.debug("Template File Prefix: " + mTemplatePrefix);

        /* set up the default variable map */
        mLog.debug("Building Default Variable Map");
        buildDefaultVars();

        if (cl.hasOption('d')) {
            displayVariables();
            System.exit(0);
        }

        /* upgrade the variable map from the config in force */
        mProv = Provisioning.getInstance();

        mLog.debug("Updating Default Variable Map");
        updateDefaultVars();

        mLog.debug("Processing Config Overrides");
        overrideDefaultVars(cl);

        if (cl.hasOption('D')) {
            displayVariables();
            System.exit(0);
        }

        try {
            File confDir = new File(mConfDir,"");
            String confPath = confDir.getAbsolutePath();
            if (!confDir.canRead()) {
                throw new ProxyConfException ("Cannot read configuration directory " + confPath);
            }
            if (!confDir.canWrite()) {
                throw new ProxyConfException ("Cannot write to configuration directory " + confPath);
            }
            if (!confDir.exists()) {
                throw new ProxyConfException ("Configuration directory " + confDir.getAbsolutePath() + " does not exist");
            }
            expandTemplate(new File(mTemplateDir,getCoreConfTemplate()), new File(mConfDir,getCoreConf()));
            expandTemplate(new File(mTemplateDir,getMainConfTemplate()), new File(mConfDir,getMainConf()));
            expandTemplate(new File(mTemplateDir,getMemcacheConfTemplate()), new File(mConfDir,getMemcacheConf()));
            expandTemplate(new File(mTemplateDir,getMailConfTemplate()), new File(mConfDir,getMailConf()));
            expandTemplate(new File(mTemplateDir,getMailImapConfTemplate()), new File(mConfDir,getMailImapConf()));
            expandTemplate(new File(mTemplateDir,getMailImapSConfTemplate()), new File(mConfDir,getMailImapSConf()));
            expandTemplate(new File(mTemplateDir,getMailPop3ConfTemplate()), new File(mConfDir,getMailPop3Conf()));
            expandTemplate(new File(mTemplateDir,getMailPop3SConfTemplate()), new File(mConfDir,getMailPop3SConf()));
            expandTemplate(new File(mTemplateDir,getWebConfTemplate()), new File(mConfDir,getWebConf()));
            expandTemplate(new File(mTemplateDir,getWebHttpConfTemplate()), new File(mConfDir,getWebHttpConf()));
            expandTemplate(new File(mTemplateDir,getWebHttpSConfTemplate()), new File(mConfDir,getWebHttpSConf()));
            expandTemplate(new File(mTemplateDir,getWebHttpModeConfTemplate("http")), new File(mConfDir,getWebHttpModeConf("http")));
            expandTemplate(new File(mTemplateDir,getWebHttpModeConfTemplate("https")), new File(mConfDir,getWebHttpModeConf("https")));
            expandTemplate(new File(mTemplateDir,getWebHttpModeConfTemplate("both")), new File(mConfDir,getWebHttpModeConf("both")));
            expandTemplate(new File(mTemplateDir,getWebHttpModeConfTemplate("redirect")), new File(mConfDir,getWebHttpModeConf("redirect")));
            expandTemplate(new File(mTemplateDir,getWebHttpModeConfTemplate("mixed")), new File(mConfDir,getWebHttpModeConf("mixed")));
            expandTemplate(new File(mTemplateDir,getWebHttpSModeConfTemplate("http")), new File(mConfDir,getWebHttpSModeConf("http")));
            expandTemplate(new File(mTemplateDir,getWebHttpSModeConfTemplate("https")), new File(mConfDir,getWebHttpSModeConf("https")));
            expandTemplate(new File(mTemplateDir,getWebHttpSModeConfTemplate("both")), new File(mConfDir,getWebHttpSModeConf("both")));
            expandTemplate(new File(mTemplateDir,getWebHttpSModeConfTemplate("redirect")), new File(mConfDir,getWebHttpSModeConf("redirect")));
            expandTemplate(new File(mTemplateDir,getWebHttpSModeConfTemplate("mixed")), new File(mConfDir,getWebHttpSModeConf("mixed")));
        } catch (ProxyConfException pe) {
            mLog.error("Error while expanding templates: " + pe.getMessage());
        } catch (SecurityException se) {
            mLog.error("Error while expanding templates: " + se.getMessage());
        }
    }
}
