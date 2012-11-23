import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import jline.console.ConsoleReader;
import jline.console.completer.ArgumentCompleter;
import jline.console.completer.NullCompleter;
import jline.console.completer.StringsCompleter;
import jline.internal.TerminalLineSettings;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;



public class JMXRat implements Runnable{



    //--Console:
    ConsoleReader reader;
    //full Description
    String[] cmplValues;
    //understandable Description
    String[] cmplKeys;
    PrintWriter out;
    //String for the prompt-line
    String promptTxt;
    //Completor field
    List completor = new LinkedList();
    ArgumentCompleter arc;

    //--MBeans Conncection
    MBeanServerConnection mbsc=null;
    //Hashmap containing running applications
    public Map<String, VirtualMachine> runningApps=null;
    //List of Methods of the current connected JooFlux Application
    String[] methodArr;

public static void main(String[] args){

    try {
        JMXRat jr =new JMXRat();
        //Start Thread, monitoring the shell input
        (new Thread(new JMXRat())).start();
        //manage the problem on exit, that the terminal does not respond anymore
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
               System.out.println("Restoring old terminal...");
                TerminalLineSettings t= null;
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

    } catch (Exception e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }


}

    public JMXRat() throws Exception {

        //Setting up prompt
        reader = new ConsoleReader();
        reader.setHistoryEnabled(true);
        out = new PrintWriter(System.out);
        promptTxt="prompt> ";
        //reader.clearScreen();


        addCompletors();

        String line;



    }

    private boolean isCorrectArgument(String[] args) {


        if((args.length == 3) && args[0].equalsIgnoreCase("change")
            && Arrays.asList(methodArr).contains(args[1])
            && Arrays.asList(methodArr).contains(args[2])){
               return true;
        }else{

       return false;
        }
    }

    private void addCompletors(){


        //Adding Completors



        runningApps=findEngines();


        if(runningApps.size()==0){
            out.println( "--no javaagent applications detected, type r to refresh")  ;
          //  cmplValues[0]="--no javaagent applications detected, type r to refresh";
            return;
        }

        //full Description
        cmplValues=new String[runningApps.size()];
        //understandable Description
        cmplKeys=new String[runningApps.size()];

        try{
        int index = 0;
        for (Map.Entry<String, VirtualMachine> mapEntry : runningApps.entrySet()) {
            cmplValues[index] = mapEntry.getValue().getAgentProperties().toString();
            cmplKeys[index] = mapEntry.getKey().toString();
            index++;
        }
        }catch (Exception e){
            e.printStackTrace();
        }


        //Add completers containing running processes
        reader.addCompleter (new ArgumentCompleter(
                new StringsCompleter("list"),
                new NullCompleter()
        ));

        reader.addCompleter (new ArgumentCompleter(
                new StringsCompleter("methods"),
                new NullCompleter()
        ));
        reader.addCompleter (new ArgumentCompleter(
                new StringsCompleter("refresh"),
                new NullCompleter()
        ));
        reader.addCompleter (new ArgumentCompleter(
                new StringsCompleter("help"),
                new NullCompleter()
        ));

        reader.addCompleter (new ArgumentCompleter(
                new StringsCompleter("connect"),
                new StringsCompleter(cmplKeys),
                new NullCompleter()
        ));



    }


    private Map<String, VirtualMachine> findEngines() {
        Map<String, VirtualMachine> result = new HashMap<String, VirtualMachine>();
        List<VirtualMachineDescriptor> list = VirtualMachine.list();


        for (VirtualMachineDescriptor vmd: list) {
            //Form the readable description for the completor
            String[] strArr=vmd.toString().split(" ");
            String desc =strArr[strArr.length-2]+"-"+strArr[strArr.length-1];
            try {
              //Check, whether JooFlux, or an Agent is connected.
             // if(VirtualMachine.attach(vmd).getAgentProperties().getProperty("com.sun.management.jmxremote.localConnectorAddress")!=null){
                if(VirtualMachine.attach(vmd).getAgentProperties().get("sun.jvm.args").toString().contains("jooflux")){
                  result.put(desc, VirtualMachine.attach(vmd));
              }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (AttachNotSupportedException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private MBeanServerConnection JMXConnect(VirtualMachine vm) {
        MBeanServerConnection server=null;
     try{
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
        server = conn.getMBeanServerConnection();


     }catch(Exception e){
            e.printStackTrace();
        }
        return server;
    }

    public String displayHelp(){

        return "******- JMXRat - Help \n " +
                "commands:\n" +
                "list -\t lists all available running java processes with a JooFlux-Agent \n" +
                "refresh -\t refreshes the list of running java processes (e.g. if empty) \n" +
                "connect <process> -\t connects via JMX to chosen process \n" +
                "methods -\t shows available methods to change of currently connected process \n" +
                "change <method name> <method name>- \t changes the first method signature into the second, if connected\n";


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
                    if(cmplValues==null){
                        out.println("======>No JooFlux-applications found");

                    } else{
                        out.println("======> List of currently running JooFlux-applications");
                        for (int i=0; i<cmplValues.length; i++){
                            out.println("\n"+cmplValues[i]);
                        }      }
                    out.flush();
                }
                if (line[0].equalsIgnoreCase("refresh")) {
                    out.println("======> Refreshing...");
                    out.flush();
                    addCompletors();

                }
                if (line[0].equalsIgnoreCase("connect")&& runningApps.containsKey(line[1])) {
                    //Check if we are already connected to an application
                    if(mbsc==null){
                        out.println("======> Connecting to"+line[1]);
                        out.flush();
                        //TODO: extend exception handling ==>
                        mbsc =JMXConnect(runningApps.get(line[1]));
                        //get methods of the application
                        methodArr = (String[]) mbsc.getAttribute(new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                                "RegisteredCallSiteKeys");


                        //Change completors to methods
                        /*     //Form the readable description for the completor
                        String[] k;
                        for(int i=0;i<methodArr.length;i++){
                        k=methodArr[i].split("/");
                            methodArr[i]=k[k.length-2]+" "+k[strArr.length-1];
                        }*/
                        reader.addCompleter (new ArgumentCompleter(
                                new StringsCompleter("change"),
                                new StringsCompleter(methodArr),
                                new NullCompleter()
                        ));


                        /*  out.println("Name= " + (String)mbsc.invoke(new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                             "getRegisteredCallSiteKeys", new Object[] {}, new String[] {}));
                        out.flush();*/

                        //Change prompt text, when connected
                        promptTxt="connected >";
                    }else{
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
                if (line[0].equalsIgnoreCase("methods") ) {
                    if(mbsc!=null&& methodArr !=null){
                        out.println("======> Listing application's methods:"+line);
                        //print out all available methods to change of the connected applications

                        for(String item: methodArr){
                            out.println(item);
                        }

                        out.flush();

                    }else{
                        //if not connected to JooFlux-Application,
                        out.println("======> Listing application's methods failed - not connected or no methods available to change"+line);
                        out.flush();
                    }

                }

                if (mbsc!=null && isCorrectArgument(line)) {

                    out.println("======> Changing following methods:"+line);
                    //Change call Site Target
                    try {

                        mbsc.invoke(
                                new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                                "changeCallSiteTarget",
                                new Object[]{"virtual",
                                        line[1],
                                        line[2]},new String[]{String.class.getName(),String.class.getName(),String.class.getName()});
                    } catch (Exception e) {
                        out.println("======> Failed to change method");
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }


                    out.flush();

                }




            }
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (MBeanException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (AttributeNotFoundException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InstanceNotFoundException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (ReflectionException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (MalformedObjectNameException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


    }


}





