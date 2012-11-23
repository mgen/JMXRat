import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import java.lang.management.ManagementFactory;
import java.util.Iterator;
import java.util.Set;

public class JMXConnectorTest2 {

    public static void main(String[] args) throws Exception {

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        Set<ObjectInstance> instances = server.queryMBeans(null, null);

        Iterator<ObjectInstance> iterator = instances.iterator();

        while (iterator.hasNext()) {
            ObjectInstance instance = iterator.next();
            System.out.println("MBean Found:");
            System.out.println("Class Name:\t" + instance.getClassName());
            System.out.println("Object Name:\t" + instance.getObjectName());

    }
    }
}