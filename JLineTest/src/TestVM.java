import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.IOException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: kdrake
 * Date: 24.10.12
 * Time: 17:16
 * To change this template use File | Settings | File Templates.
 */
public class TestVM {

    public static void main(String[] args){
            TestVM t=new TestVM();

    }

    public TestVM() {

        final Map<String, VirtualMachine> localEngines = findEngines();
        String[] s=localEngines.keySet().toArray(new String[0]);

        for(int i=0; i<s.length;i++){
            try{
            System.out.println(s[i]);
                Properties props = localEngines.get(s[i]).getAgentProperties();
                System.out.println("Flags: "+props.get("sun.jvm.args").toString());
                    for (Map.Entry<Object, Object> c : props.entrySet()) {
                    System.out.println(c.getKey().toString());
                }


                /*  if(!props.getProperty("-javaagent").equals("")){
                     System.out.println("Yes Agent is there");

                 }else {System.out.println("No, not connected");    }*/
            }catch (Exception e){
                    e.printStackTrace();
            }
        }
     //   System.out.println(Arrays.toString(localEngines.keySet().toArray()));


    }

    private Map<String, VirtualMachine> findEngines() {
        Map<String, VirtualMachine> result = new HashMap<String, VirtualMachine>();
        List<VirtualMachineDescriptor> list = VirtualMachine.list();
        for (VirtualMachineDescriptor vmd: list) {
            String desc = vmd.toString();
            try {
                result.put(desc, VirtualMachine.attach(vmd));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (AttachNotSupportedException e) {
                e.printStackTrace();
            }
        }
        return result;
    }


}
