/**
 * Created with IntelliJ IDEA.
 * User: kdrake
 * Date: 14.10.12
 * Time: 20:01
 * To change this template use File | Settings | File Templates.
 */

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class JMXConnectorTest {

    public static void main(String[] args) throws IOException {
        JMXConnector jmxConnector=null;

    List<VirtualMachineDescriptor> vms = VirtualMachine.list();
    for (VirtualMachineDescriptor desc : vms) {
        VirtualMachine vm;
        try {
            vm = VirtualMachine.attach(desc);
        } catch (Exception e) {
            continue;
        }
        Properties props = null;
        try {
            props = vm.getAgentProperties();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        String connectorAddress =
                props.getProperty("com.sun.management.jmxremote.localConnectorAddress");
        if (connectorAddress == null) {
            continue;
        }
        try {
        JMXServiceURL url = new JMXServiceURL(connectorAddress);
        jmxConnector = JMXConnectorFactory.connect(url);

            MBeanServerConnection mbeanConn = jmxConnector.getMBeanServerConnection();
            Set<ObjectInstance> beanSet = mbeanConn.queryMBeans(null, null);

            Iterator<ObjectInstance> iterator = beanSet.iterator();

            while (iterator.hasNext()) {
                ObjectInstance instance = iterator.next();
                System.out.println("MBean Found:");
                System.out.println("Class Name:\t" + instance.getClassName());
                System.out.println("Object Name:\t" + instance.getObjectName());
                System.out.println("****************************************");
                //mbeanConn.invoke()
            }

        }catch (Exception e){

        }
            finally
         {
           jmxConnector.close();
        }
    }

    }

}
