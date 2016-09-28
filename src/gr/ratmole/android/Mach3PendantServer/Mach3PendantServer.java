package gr.ratmole.android.Mach3PendantServer;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.jezhumble.javasysmon.JavaSysMon;
import com.jezhumble.javasysmon.ProcessInfo;
import com.sun.jna.Native;
import com.sun.jna.PointerType;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import gr.ratmole.android.shared.*;
import gr.ratmole.android.shared.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;

public class Mach3PendantServer {
    private static final String propertiesFileName = "Mach3PendantServer.properties";
    private static final String APP_NAME = "Mach3PendantServer";
    private final Logger l = LoggerFactory.getLogger(Mach3PendantServer.class);
    private Properties prop = new Properties();
    private static final String PROP_KEY_REMEMBERED_DEVICES = "remembered.devices";

    private Server tcpServer, udpServer;
    private HandshakeListener handshakeListener = new HandshakeListener();
    private Robot robot;
    private List<Connection> authorizedConnections = new Vector<Connection>();
    private TrustedInterchangeListener trustedInterchangeListener = new TrustedInterchangeListener();
    private int prevHash = 0;
    private TrayIcon trayIcon;
    private boolean stopping = false;


    //User32 lib specific data
    private User32 user32 = (User32) Native.loadLibrary("user32", User32.class);
    private byte[] windowText = new byte[512];
    private static final long DELAY_OF_REQUEST_NEW_WINDOW_TITLE = 10;
    private PointerType hwnd;

  
    private static final int APP_VERSION = 101;

    private static final String MENU_LABEL_STATE = "State...";
    private static final String MENU_LABEL_EXIT = "Exit";

    private String tempPin;
    private List<String> rememberedDevices = new Vector<String>();

    private static final String MENU_LABEL_ABOUT = "About";

    public interface User32 extends StdCallLibrary {
        int GetWindowTextA(PointerType hWnd, byte[] lpString, int nMaxCount);

        WinDef.HWND GetForegroundWindow();

        int GetWindowThreadProcessId(PointerType hWnd, IntByReference p);
    }

    public Mach3PendantServer() {
        //Override System.out to the logger
        SysOutOverSLF4J.sendSystemOutAndErrToSLF4J();
        l.info("Starting " + APP_NAME + " " + APP_VERSION);
        loadProperties();

        initTrayIcon();

        try {
            robot = new Robot();
           
        } catch (AWTException e) {
            e.printStackTrace();
            displayErrorMessage("Mach3PendantServer init problem", e.getMessage());
            onStop();
        }

        tcpServer = new Server();
        udpServer = new Server();
        Network.register(tcpServer);

        try {
            registerListeners();
            establishConnection();
        } catch (IOException e) {
            e.printStackTrace();
            displayErrorMessage("Mach3PendantServer init problem", e.getMessage());
            onStop();
        }
        startWindowChangeNotification();
    }

    private void loadProperties() {
        try {
            prop.load(new FileInputStream(propertiesFileName));
        } catch (IOException e) {
            releaseAllKeys();
            e.printStackTrace();
        }

        l.debug("load remembered devices");
        String rd = prop.getProperty(PROP_KEY_REMEMBERED_DEVICES, "");
        StringTokenizer st = new StringTokenizer(rd, ";");
        while (st.hasMoreTokens()) {
            String device = st.nextToken();
            l.debug("add " + device + " to the list of remembered devices");
            rememberedDevices.add(device);
        }

    }

    private void establishConnection() throws IOException {
        udpServer.start();
        udpServer.bind(Network.TCP_PORT + 1, Network.UDP_PORT);
        tcpServer.start();
        tcpServer.bind(Network.TCP_PORT);
    }

    private void registerListeners() {
        tcpServer.addListener(handshakeListener);
    }

    private void onStop() {
        stopping = true;
        tcpServer.stop();
        udpServer.stop();
        releaseAllKeys();
        saveProperties();
        System.exit(0);
    }

    private void saveProperties() {

        //Save remembered devices
        StringBuilder sb = new StringBuilder();
        for (String d : rememberedDevices) {
            sb.append(d).append(";");
        }
        prop.setProperty(PROP_KEY_REMEMBERED_DEVICES, sb.toString());

        try {
            prop.store(new FileOutputStream(propertiesFileName), "This is a Mach3PendantServer properties file");
        } catch (IOException e) {
            releaseAllKeys();
            e.printStackTrace();
        }

    }

    private void initTrayIcon() {
        if (SystemTray.isSupported()) {

            SystemTray tray = SystemTray.getSystemTray();

            ActionListener exitListener = new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    onStop();
                }
            };

            PopupMenu popup = new PopupMenu();

            
            //Stats
            MenuItem state = new MenuItem(MENU_LABEL_STATE);
            popup.add(state);
            state.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    StringBuilder sb = new StringBuilder();
                    if (authorizedConnections.size() == 0) {
                        sb.append("You don't have any active connections");
                    } else {
                        sb.append("List of active connections:\n");
                    }
                    for (Connection con : authorizedConnections) {
                        if (!con.getRemoteAddressTCP().toString().contains("127.0.0.1")) {
                            sb.append("Wi-Fi: ");
                        }
                        sb.append(con.getRemoteAddressTCP()).append("\n");
                    }
                    JOptionPane.showMessageDialog(new Frame(), sb.toString(), "State", JOptionPane.PLAIN_MESSAGE);
                }
            });
            //Divider
            popup.add(new MenuItem("-"));
            //Stats
            MenuItem about = new MenuItem(MENU_LABEL_ABOUT + "...");
            popup.add(about);
            about.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(APP_NAME).append(" controls Mach3 by android devices running the Mach3Pendant application").append("\n");
                    sb.append("version ").append(APP_VERSION).append("\n");
                    sb.append("Created by raTMole").append("\n");
                    sb.append("ratmole@gmail.com");
                    JOptionPane.showMessageDialog(new Frame(), sb.toString(), MENU_LABEL_ABOUT, JOptionPane.PLAIN_MESSAGE);
                }
            });
            //Divider
            popup.add(new MenuItem("-"));
            //Exit
            MenuItem exitItem = new MenuItem(MENU_LABEL_EXIT);
            exitItem.addActionListener(exitListener);
            popup.add(exitItem);

            trayIcon = new TrayIcon(createImage("/img/tray_icon_50.png", "tray icon"), APP_NAME, popup);
            trayIcon.setImageAutoSize(true);

            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                releaseAllKeys();
                e.printStackTrace();
            }

        } else {
            l.error("System tray is currently not supported.");
        }
    }

    private void displayErrorMessage(String title, String message) {
        displayMessage(title, message, TrayIcon.MessageType.ERROR);
    }

    private void displayInfoMessage(String title, String message) {
        displayMessage(title, message, TrayIcon.MessageType.INFO);
    }

    private void displayMessage(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title,
                    message,
                    type);
        }
    }

    private void startWindowChangeNotification() {
        Thread notificationThread = new Thread() {
            public void run() {

                while (!stopping) {
                    windowChangeNotification();
                    try {
                        Thread.sleep(DELAY_OF_REQUEST_NEW_WINDOW_TITLE);
                    } catch (InterruptedException e) {
                        releaseAllKeys();
                        e.printStackTrace();
                    }
                }
            }
        };
        notificationThread.start();
    }

    private void windowChangeNotification() {
        ServerWindow msg = getCurrentServerWindow();
        int currHash = (msg.getTitle() + msg.getProcessName()).hashCode();
        if (currHash != prevHash) {
            prevHash = currHash;

            for (Connection con : authorizedConnections) {
                con.sendTCP(msg);
            }
        }
    }

    private ServerWindow getCurrentServerWindow() {

        hwnd = user32.GetForegroundWindow(); // assign the window handle here.
        user32.GetWindowTextA(hwnd, windowText, 512);
        String title = Native.toString(windowText);

        IntByReference p = new IntByReference();
        user32.GetWindowThreadProcessId(hwnd, p);
        int pid = p.getValue();
        String processName = getProcessNameByPid(pid);

        //l.debug("titile:" + title + " pid:" + pid + "procname:" + processName);
        ServerWindow msg = new ServerWindow(title, processName);

        return msg;
    }

    private String getProcessNameByPid(int pid) {
        ProcessInfo[] pidTable = new JavaSysMon().processTable();
        for (ProcessInfo info : pidTable) {
            if (info.getPid() == pid) {
                return info.getName();
            }
        }
        return "";
    }

	private void runEventSequence(EventSequence sequence) {
		for (Event event : sequence.getSequence()) {
			// KEY event
			if (event instanceof KeyEvent) {

				KeyEvent keyEvent = (KeyEvent) event;
				if (keyEvent.press) {
					robot.keyPress(keyEvent.code);
				} else {
					robot.keyRelease(keyEvent.code);

				}
			}

		}
	}
   

    //Obtain the image URL
    protected Image createImage(String path, String description) {
        URL imageURL = Mach3PendantServer.class.getResource(path);

        if (imageURL == null) {
            l.error("Resource not found: " + path);
            return null;
        } else {
            return (new ImageIcon(imageURL, description)).getImage();
        }
    }

    public class HandshakeListener extends Listener {
        private String tempUserAccountName;
        private String tempUserAccountType;

        @Override
        public void received(Connection connection, Object object) {
            if (object instanceof Handshake) {
                Handshake h = (Handshake) object;
                switch (h.getId()) {
                    case Handshake.PHONE_TRUST_ME:
                        l.debug("Receive PHONE:TRUST_ME");
                        tempUserAccountName = h.getName();
                        tempUserAccountType = h.getType();
                        if (isAlreadyRememberedDevice()) {
                            finishAuthorization(connection);
                        } else {
                            displayInfoMessage("", "Please enter PIN " + regeneratePin() + " on the Mach3Pendant client (" + tempUserAccountName + ")");
                            connection.sendTCP(new Handshake().pc_EnterPin());
                        }
                        break;
                    case Handshake.PHONE_SENT_PIN:
                        l.debug("Receive PHONE:SENT_PIN");
                        String pin = h.getPin();
                        l.debug("pin = " + pin);
                        if (pin.equals(tempPin)) {
                            finishAuthorization(connection);
                            if (h.isRemember()) rememberDevice();
                        } else {
                            displayInfoMessage("", "Please enter PIN " + regeneratePin() + " on the Mach3Pendant client (" + tempUserAccountName + ")");
                            connection.sendTCP(new Handshake().pc_IncorrectPin());
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        private void finishAuthorization(Connection con) {
            l.debug("Sent PC:I_TRUST_PHONE");
            con.sendTCP(new Handshake().pc_ITrustPhone());
            authorizedConnections.add(con);

            ServerGreeting greeting = new ServerGreeting();
            greeting.osName = System.getProperty("os.name");
            greeting.osVersion = System.getProperty("os.version");
            greeting.userName = System.getProperty("user.name");
            greeting.approvedByUser = true;
            greeting.serverWindow = getCurrentServerWindow();
            greeting.Mach3PendantServerVersion = APP_VERSION;

            con.sendTCP(greeting);
            con.addListener(trustedInterchangeListener);
            if (!isAlreadyRememberedDevice()) {
                displayInfoMessage("Connection", "Mach3Pendant client has been connected");
            }
        }

        private void rememberDevice() {
            rememberedDevices.add(tempUserAccountName + "+" + tempUserAccountType);
        }

        private boolean isAlreadyRememberedDevice() {
            for (String d : rememberedDevices) {
                if (d.equals(tempUserAccountName + "+" + tempUserAccountType)) return true;
            }
            return false;
        }
    }

    private String regeneratePin() {
        String time = Long.toString(System.currentTimeMillis());
        tempPin = time.substring(time.length() - 4);
        return tempPin;
    }

    private class TrustedInterchangeListener extends Listener {
        @Override
        public void received(Connection connection, Object object) {
            if (object instanceof EventSequence) {
                runEventSequence((EventSequence) object);
            } 
        }

    @Override
    public void disconnected(Connection connection) {
        displayInfoMessage("Connection", "Mach3Pendant client has been disconnected");
        releaseAllKeys();
        }
    
    
    }
    
    public void releaseAllKeys(){
    	for (String key: MapStringToKeyCode.map.keySet()) {
        robot.keyRelease(MapStringToKeyCode.map.get(key));
        }
    }
   
        public void finish() {
            releaseAllKeys();
        }
    
   
}
