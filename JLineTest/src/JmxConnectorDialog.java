import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// requires Sun's default-tools.jar
public class JmxConnectorDialog extends JDialog implements ActionListener, KeyListener {

    private static final long serialVersionUID = 1L;
    private static final String CONNECTOR_ADDRESS_PROPERTY = "com.sun.management.jmxremote.localConnectorAddress";

    private final JRadioButton localButton = new JRadioButton("Local Process");
    private final JRadioButton remoteButton = new JRadioButton("Remote Process");
    private final Map<String, VirtualMachine> localEngines = findEngines();
    private final JComboBox typeList = new JComboBox(localEngines.keySet().toArray());
    private final JTextField hostField = new JTextField(20);
    private final JTextField portField = new JTextField(20);
    private final JTextField userField = new JTextField(20);
    private final JPasswordField passField = new JPasswordField(20);

    private JMXConnector connector = null;
    private String errorString = "";

    public static void main(String[] args) {
        JFrame frame = new JFrame("Graph Explorer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        JmxConnectorDialog jmxDialog = JmxConnectorDialog.showDialog(frame);
        JMXConnector connector = jmxDialog.getConnector();
        if (connector==null) {
            JOptionPane.showMessageDialog(frame,
                    "Can't connect to JMX server: "+jmxDialog.getError(),
                    "Connection error", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(frame, "Selected real Java process");
        }


        MBeanServerConnection mbsc = null;
        try {
            JOptionPane.showMessageDialog(frame, "Connecting...");
            mbsc = connector.getMBeanServerConnection();
            String result = (String)mbsc.invoke(
                    new ObjectName("fr.insalyon.telecom.jooflux.internal.jmx:type=JooFluxManagement"),
                    "changeCallSiteTarget",
                    new Object[]{"virtual",
                    "fr/insalyon/telecom/joofluxtest/gui/switcher/MyActionListener.counterIncrement:(MyActionListener)void",
                    "fr/insalyon/telecom/joofluxtest/gui/switcher/MyActionListener.pictureSwitch:()V"},new String[]{String.class.getName(),String.class.getName(),String.class.getName()});
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


    }

    public static JmxConnectorDialog showDialog(Component frameComp) {
        Frame frame = JOptionPane.getFrameForComponent(frameComp);
        JmxConnectorDialog dialog = new JmxConnectorDialog(frame);
        dialog.setVisible(true);
        return dialog;
    }


    public JmxConnectorDialog(Frame frame) {
        super(frame, "JMX Connection", true);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                errorString="User cancelled";
                setVisible(false);
            }
        });

        Container contentPane = getContentPane();
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        contentPane.add(content);

        content.add(createLocalPanel());
        content.add(createRemotePanel());
        content.add(createButtonPanel());

        ButtonGroup group = new ButtonGroup();
        group.add(localButton);
        group.add(remoteButton);

        pack();
    }

    private JPanel createLocalPanel() {
        JPanel localPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
        localButton.setPreferredSize(new Dimension(150, 50));
        localButton.setSelected(true);
        localPanel.add(localButton);
        typeList.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                localButton.setSelected(true);
            }
        });
        JPanel listPanel = new JPanel(new FlowLayout());
        listPanel.add(typeList, BorderLayout.SOUTH);
        localPanel.add(listPanel);
        return localPanel;
    }

    private JPanel createRemotePanel() {
        JPanel remotePanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
        remoteButton.setPreferredSize(new Dimension(150, 50));
        remotePanel.add(remoteButton);
        JPanel remoteOptionPanel = new JPanel();
        remoteOptionPanel.setLayout(new BoxLayout(remoteOptionPanel, BoxLayout.Y_AXIS));
        remoteOptionPanel.add(createField("Host", hostField));
        remoteOptionPanel.add(createField("Port", portField));
        remoteOptionPanel.add(createField("User", userField));
        remoteOptionPanel.add(createField("Password", passField));
        remotePanel.add(remoteOptionPanel);
        return remotePanel;
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton connectButton = new JButton("Connect");
        getRootPane().setDefaultButton(connectButton);
        connectButton.addActionListener(this);
        buttonPanel.add(connectButton);
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                errorString="User cancelled";
                setVisible(false);
            }
        });
        buttonPanel.add(cancelButton);
        return buttonPanel;
    }

    private JPanel createField(String label, JTextField field) {
        JPanel fieldPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 1));
        JLabel labelComponent = new JLabel(label);
        labelComponent.setPreferredSize(new Dimension(60, 10));
        fieldPanel.add(labelComponent);
        fieldPanel.add(field);
        field.addKeyListener(this);
        return fieldPanel;
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

    private JMXConnector getLocalConnection(VirtualMachine vm) {
        try {
            Properties props = vm.getAgentProperties();
            String connectorAddress = props.getProperty(CONNECTOR_ADDRESS_PROPERTY);
            if (connectorAddress == null) {
                props = vm.getSystemProperties();
                String home = props.getProperty("java.home");
                String agent = home + File.separator + "lib" + File.separator + "management-agent.jar";
                vm.loadAgent(agent);
                props = vm.getAgentProperties();
                connectorAddress = props.getProperty(CONNECTOR_ADDRESS_PROPERTY);
            }

            JMXServiceURL url = new JMXServiceURL(connectorAddress);
            return JMXConnectorFactory.connect(url);
        } catch (Exception e) {
            e.printStackTrace();
            errorString = e.getMessage();
            return null;
        }
    }

    private JMXConnector getRemoteConnection(String host, int port, String user, String password) {
        try {
            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://"+host+":"+port+"/jmxrmi");
            final Map<String, String[]> environment = new HashMap<String, String[]>();
            environment.put(JMXConnector.CREDENTIALS, new String[] {user, password} );
            return JMXConnectorFactory.connect(url, environment);
        } catch (Exception e) {
            e.printStackTrace();
            errorString = e.getMessage();
            return null;
        }
    }

    public JMXConnector getConnector() {
        return connector;
    }

    public String getError() {
        return errorString;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (localButton.isSelected()) {
            connector = getLocalConnection(localEngines.get(typeList.getSelectedItem()));
        } else {
            try {
                int port = Integer.parseInt(portField.getText());
                String password = String.valueOf(passField.getPassword());
                connector = getRemoteConnection(hostField.getText(), port, userField.getText(), password);
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), "Invalid port number",
                        "Connection error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        setVisible(false);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        remoteButton.setSelected(true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        remoteButton.setSelected(true);
    }

    @Override
    public void keyTyped(KeyEvent e) {
        remoteButton.setSelected(true);
    }
}