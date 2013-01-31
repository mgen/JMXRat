package JMXRat;

import com.sun.tools.attach.*;
import jline.console.ConsoleReader;
import jline.console.completer.ArgumentCompleter;
import jline.console.completer.NullCompleter;
import jline.console.completer.StringsCompleter;
import jline.internal.TerminalLineSettings;

import javax.management.*;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.*;


public class JMXRat implements Runnable, NotificationListener {


    //--Console:
    ConsoleReader reader;
    //full Description
    String[] cmplValues;
    //understandable Description
    String[] cmplKeys;
    PrintWriter out;
    //Color
    public static  String GREEN ;
    public static  String DGREEN;
    public static  String WHITE;
    public static  String RED;


    //String for the prompt-line
    String promptTxt;
    //Completor field
    List completor = new LinkedList();
    ArgumentCompleter arc;

    //--MBeans Conncection
    MBeanServerConnection mbsc = null;
    //Hashmap containing running applications
    public Map<String, VirtualMachine> runningApps = null;
    //Map of Methods of the current connected JooFlux Application
    public HashMap<String, String[]> methodMap = new HashMap<String, String[]>();
    String[] methodArr;

    public static void main(String[] args) {

        try {
            JMXRat jr = new JMXRat();
            //Start Thread, monitoring the shell input
            (new Thread(new JMXRat())).start();
            //manage the problem on exit, that the terminal does not respond anymore
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    System.out.println("Restoring old terminal...");
                    TerminalLineSettings t = null;
                    try {
                        t = new TerminalLineSettings();
                        t.restore();
                    } catch (IOException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    } catch (InterruptedException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }

                }
            });

        } catch(AttachNotSupportedException e2){

        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


    }

    public JMXRat() throws Exception {

        //Setting up prompt
        reader = new ConsoleReader();
        reader.setHistoryEnabled(true);
        out = new PrintWriter(System.out) {
            public void println(String x) {
                System.out.println(DGREEN + x + WHITE);

            }
        };
        promptTxt = DGREEN + "prompt> " + WHITE;
        //reader.clearScreen();

        //determine whether OS is Windows, then no colors, else ANSI colors for shell enabled
        setColors();

        addCompletors();

        String line;


    }

    private boolean isCorrectArgument(String[] args) {


        if (((args.length == 3) && args[0].equalsIgnoreCase("change")
                && methodMap.containsKey(args[1])) ||
                ((args.length == 3) && args[0].equalsIgnoreCase("change")
                        && methodMap.containsValue(args[1])
                        && methodMap.containsValue(args[2]))) {
            return true;
        } else {

            return false;
        }
    }

    private void addCompletors() {


        //Adding Completors
        reader.addCompleter(new ArgumentCompleter(
                new StringsCompleter("refresh"),
                new NullCompleter()
        ));
        reader.addCompleter(new ArgumentCompleter(
                new StringsCompleter("help"),
                new NullCompleter()
        ));
        reader.addCompleter(new ArgumentCompleter(
                new StringsCompleter("list"),
                new NullCompleter()
        ));

        runningApps = findEngines();


        if (runningApps.size() == 0) {
            out.println("--no javaagent applications detected, type r to refresh");
            //  cmplValues[0]="--no javaagent applications detected, type r to refresh";
            return;
        }

        //full Description
        cmplValues = new String[runningApps.size()];
        //understandable Description
        cmplKeys = new String[runningApps.size()];

        try {
            int index = 0;
            for (Map.Entry<String, VirtualMachine> mapEntry : runningApps.entrySet()) {
                cmplValues[index] = mapEntry.getValue().getAgentProperties().toString();
                cmplKeys[index] = mapEntry.getKey().toString();
                index++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        //Add completers containing running processes


        reader.addCompleter(new ArgumentCompleter(
                new StringsCompleter("methods"),
                new NullCompleter()
        ));


        reader.addCompleter(new ArgumentCompleter(
                new StringsCompleter("connect"),
                new StringsCompleter(cmplKeys),
                new NullCompleter()
        ));


    }


    private Map<String, VirtualMachine> findEngines() {
        Map<String, VirtualMachine> result = new HashMap<String, VirtualMachine>();
        List<VirtualMachineDescriptor> list = VirtualMachine.list();


        for (VirtualMachineDescriptor vmd : list) {
            //Form the readable description for the completor
            String[] strArr = vmd.toString().split(" ");
            String desc = strArr[strArr.length - 2] + "-" + strArr[strArr.length - 1];
            try {
                //Check, whether JooFlux, or an Agent is connected.
                // if(VirtualMachine.attach(vmd).getAgentProperties().getProperty("com.sun.management.jmxremote.localConnectorAddress")!=null){
                if (VirtualMachine.attach(vmd).getAgentProperties().get("sun.jvm.args").toString().contains("jooflux")) {
                    result.put(desc, VirtualMachine.attach(vmd));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (AttachNotSupportedException e) {
                 //suppressing, as it happens on routine startup
                //e.printStackTrace();
            }
        }
        return result;
    }

    private MBeanServerConnection JMXConnect(VirtualMachine vm) {
        MBeanServerConnection server = null;
        try {
            Properties props = vm.getAgentProperties();
            String connectorAddress = props.getProperty("com.sun.management.jmxremote.localConnectorAddress");
            if (connectorAddress == null) {
                props = vm.getSystemProperties();
                String home = props.getProperty("java.home");
                String agent = home + File.separator + "lib" + File.separator + "management-agent.jar";
                vm.loadAgent(agent);
                props = vm.getAgentProperties();
                connectorAddress = props.getProperty("com.sun.management.jmxremote.localConnectorAddress");
            }

            JMXServiceURL url = new JMXServiceURL(connectorAddress);

            JMXConnector conn = JMXConnectorFactory.connect(url);
            conn.addConnectionNotificationListener(this, null, conn);

            server = conn.getMBeanServerConnection();


        } catch (AgentInitializationException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (MalformedURLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (AgentLoadException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


        return server;
    }

    public String displayHelp() {

        return GREEN + "******- JMXRat - Help \n " +
                "commands:\n" +
                "list -\t lists all available running java processes with a JooFlux-Agent \n" +
                "refresh -\t refreshes the list of running java processes (e.g. if empty) \n" +
                "connect <process> -\t connects via JMX to chosen process \n" +
                "methods -\t shows available methods to change of currently connected process \n" +
                "applyAfter <callSitesKey> <aspectClass>- <aspectMethod> - \t apply Aspect after Method Execution\n" +
                "applyBefore <callSitesKey> <aspectClass>- <aspectMethod> - \t apply Aspect before Method Execution\n" +
                "force <method name> <method name>- - \t try to force change of given methods \n" +
                "change <method name> <method name>- \t changes the first method signature into the second, if connected\n" + WHITE;


    }


    @Override
    public void run() {

        String line[];

        try {
            while ((line = reader.readLine(promptTxt).split(" ")) != null) {
                out.println("======>\"" + line[0] + "\"");
                out.flush();

                if (line[0].equalsIgnoreCase("quit") || line[0].equalsIgnoreCase("exit")) {
                    break;
                }
                if (line[0].equalsIgnoreCase("help") || line[0].equalsIgnoreCase("?")) {
                    out.println(displayHelp());
                    out.flush();
                }
                if (line[0].equalsIgnoreCase("list")) {
                    if (cmplValues == null) {
                        out.println("======>No JooFlux-applications found");

                    } else {
                        out.println("======> List of currently running JooFlux-applications");
                        for (int i = 0; i < cmplValues.length; i++) {
                            out.println("\n" + cmplValues[i]);
                        }
                    }
                    out.flush();
                }
                if (line[0].equalsIgnoreCase("refresh")) {
                    out.println("======> Refreshing...");
                    out.flush();
                    addCompletors();


                }
                if (line.length == 2 && line[0].equalsIgnoreCase("connect") && runningApps.containsKey(line[1])) {
                    //Check if we are already connected to an application
                    if (mbsc == null) {
                        out.println("======> Connecting to" + line[1]);
                        out.flush();
                        //TODO: extend exception handling ==>
                        mbsc = JMXConnect(runningApps.get(line[1]));
                        //get methods of the application, and add them as completors
                        handleMethodDescriptors();
                        /*methodArr = (String[]) mbsc.getAttribute(new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                                "RegisteredCallSiteKeys");

                        reader.addCompleter (new ArgumentCompleter(
                                new StringsCompleter("change"),
                                new StringsCompleter(methodArr),
                                new NullCompleter()
                        ));*/

                        //Change prompt text, when connected
                        promptTxt = DGREEN + "connected >" + WHITE;
                    } else {
                        out.println("======> already connected");
                        out.flush();
                    }
                    /*try {

                        mbsc.invoke(
                                new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                                "changeCallSiteTarget",
                                new Object[]{"virtual",
                                        "fr/insalyon/telecom/joofluxtest/gui/switcher/MyActionListener.counterIncrement:(MyActionListener)void",
                                        "fr/insalyon/telecom/joofluxtest/gui/switcher/MyActionListener.pictureSwitch:()V"},new String[]{String.class.getName(),String.class.getName(),String.class.getName()});
                    } catch (Exception e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    } */


                }
                if (line[0].equalsIgnoreCase("methods")) {
                    if (mbsc != null && methodMap != null) {
                        out.println("======> Listing application's methods:" + line);
                        //print out all available methods to change of the connected applications

                        for (Map.Entry e : methodMap.entrySet()) {
                            String[] strE = (String[]) e.getValue();
                            out.println(strE[0]);
                        }

                        out.flush();

                    } else {
                        //if not connected to JooFlux-Application,
                        out.println("======> Listing application's methods failed - not connected or no methods available to change" + line);
                        out.flush();
                    }

                }

                if (mbsc != null && line.length == 3 && line[0].equalsIgnoreCase("force")) {
                    out.println("======> trying forced change of following methods:" + line[1] + line[2]);
                    out.flush();

                    try {
                        mbsc.invoke(
                                new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                                "changeCallSiteTarget",
                                new Object[]{(String) mbsc.invoke(
                                        new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                                        "getCallSiteType",
                                        new Object[]{line[1]}, new String[]{String.class.getName()}),
                                        line[1],
                                        line[2]}, new String[]{String.class.getName(), String.class.getName(), String.class.getName()});
                    } catch (Exception e) {
                        out.println("Changing failed or methods incorrect");
                        e.printStackTrace();
                    }

                }
                    //Applying Aspects after method execution with: applyAfterAspect(String callSitesKey, String aspectClass, String aspectMethod)
                if (mbsc != null && line.length == 4 && line[0].equalsIgnoreCase("applyAfter")) {
                       try{
                           mbsc.invoke(
                                   new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                                   "applyAfterAspect",
                                   new Object[]{
                                           methodMap.get(line[1])[0],
                                           line[2],
                                           line[3]}, new String[]{String.class.getName(), String.class.getName(), String.class.getName()});

                           out.println("======> applying Aspect before Method execution \n Class:" + line[1]+ "\n Method:" + line[3]);
                           out.flush();




                  }catch (Exception e){
                           try {
                               mbsc.invoke(
                                       new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                                       "applyAfterAspect",
                                       new Object[]{
                                               line[1],
                                               line[2],
                                               line[3]}, new String[]{String.class.getName(), String.class.getName(), String.class.getName()});
                           } catch (InstanceNotFoundException e1) {
                               e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                           } catch (MBeanException e1) {
                               e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                           } catch (ReflectionException e1) {
                               e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                           } catch (MalformedObjectNameException e1) {
                               e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                           }

                           out.println("======> applying Aspect before Method execution \n Class:" + line[1]+ "\n Method:" + line[3]);
                           out.flush();
                       }


                }

                //Applying Aspects before method execution with: applyBeforeAspect(String callSitesKey, String aspectClass, String aspectMethod)
                if (mbsc != null && line.length == 4 && line[0].equalsIgnoreCase("applyBefore")) {



                    //Try to invoke the applyBeforeMethod using the short form of the method name, if it fails, use the long form
                    try{
                        System.out.println(methodMap.get(line[1])[0]+"\n" + line[2]+"\n" + line[3]);


                        mbsc.invoke(
                                new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                                "applyBeforeAspect",
                                new Object[]{
                                        methodMap.get(line[1])[0],
                                        line[2],
                                        line[3]}, new String[]{String.class.getName(), String.class.getName(), String.class.getName()});

                        out.println("======> applying Aspect before Method execution \n Class:" + line[1]+ "\n Method:" + line[3]);
                        out.flush();


                    }catch(Exception e){


                        //long form
                        System.out.println(line[1]+"\n" + line[2]+"\n" + line[3]);
                        try {

                            mbsc.invoke(
                                    new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                                    "applyBeforeAspect",
                                    new Object[]{
                                            line[1],
                                            line[2],
                                            line[3]}, new String[]{String.class.getName(), String.class.getName(), String.class.getName()});

                            out.println("======> applying Aspect before Method execution \n Class:" + line[1]+ "\n Method:" + line[3]);
                            out.flush();
                        } catch (Exception e2) {
                            out.println("applying Aspect before Method execution failed.");
                            e2.printStackTrace();
                            e.printStackTrace();
                        }

                    }

                }

                if (mbsc != null && isCorrectArgument(line)) {
                    //TODO: Supporting both changing, long and short forms, change without arguments exception, all arguments not available?
                    out.println("======> Changing following methods:" + line[1] + line[2]);
                    //Change call Site Target
                    try {
                        /*
                        line[0]= change
                        line[1]= first method
                        line[2]= second method
                         */
                        //Change, if methodMaps contains the short, readable Version of the Method name
                        if (methodMap.containsKey(line[1])) {


                            mbsc.invoke(
                                    new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                                    "changeCallSiteTarget",
                                    new Object[]{methodMap.get(line[1])[1],
                                            methodMap.get(line[1])[0],
                                            line[2]}, new String[]{String.class.getName(), String.class.getName(), String.class.getName()});
                        }



                    } catch (Exception e) {
                        out.println("======> Failed to change method");
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.

                    }


                    out.flush();

                }


            }
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


    }

    public void handleMethodDescriptors() {

        try {

            methodArr = (String[]) mbsc.getAttribute(new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                    "RegisteredCallSiteKeys");


            for (String item : methodArr) {
                String[] strArr = item.split("/");



                methodMap.put(strArr[strArr.length - 1], new String[]{
                        item,
                        (String) mbsc.invoke(
                                new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                                "getCallSiteType",
                                new Object[]{item}, new String[]{String.class.getName()})
                });
            }
            //Add completor with readable method form
            reader.addCompleter(new ArgumentCompleter(
                    new StringsCompleter("change"),
                    new StringsCompleter(methodMap.keySet().toArray(new String[0])),
                    new NullCompleter()
            ));
            //Add forced change completor
            reader.addCompleter(new ArgumentCompleter(
                    new StringsCompleter("force"),
                    new NullCompleter()
            ));
            //Add applyBeforeAspect change completor
            reader.addCompleter(new ArgumentCompleter(
                    new StringsCompleter("applyBefore"),
                    new StringsCompleter(methodMap.keySet().toArray(new String[0])),
                    new NullCompleter()
            ));
            //Add applyAfterAspect change completor
            reader.addCompleter(new ArgumentCompleter(
                    new StringsCompleter("applyAfter"),
                    new StringsCompleter(methodMap.keySet().toArray(new String[0])),
                    new NullCompleter()
            ));


        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    public static void setColors(){
        String OS = System.getProperty("os.name").toLowerCase();


        if(OS.indexOf("win") >= 0){
            GREEN = "";
            DGREEN = "";
            WHITE = "";
            RED = "";
        } else{
        GREEN = "\u001B[1;32m";
        DGREEN = "\u001B[0;32m";
        WHITE = "\u001B[0m";
        RED = "\u001B[0;31m";
        }

    }

    //Listen for JMX-Connection Closing
    @Override
    public void handleNotification(Notification notification, Object o) {
        if (notification instanceof JMXConnectionNotification) {

            JMXConnectionNotification jmxNotif = (JMXConnectionNotification) notification;
            String type = jmxNotif.getType();

            if (type.equals(JMXConnectionNotification.FAILED) ||
                    type.equals(JMXConnectionNotification.CLOSED)) {
                System.out.println(RED + "Connection with JooFlux Agent down" + WHITE);
                System.exit(0);
            }

        }

    }
}





