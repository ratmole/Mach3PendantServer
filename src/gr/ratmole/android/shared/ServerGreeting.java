package gr.ratmole.android.shared;

public class ServerGreeting {
    public String osName;
    public String osVersion;
    public String userName;
    public ServerWindow serverWindow;
    public boolean approvedByUser = false;
    public int Mach3PendantServerVersion;

    public String toString(){
        return "osName = " + osName + "\n" +
                "osVersion = " + osVersion + "\n" +
                "userName = " + userName + "\n" +
                "approvedByUser = " + approvedByUser + "\n" +
                "window= " + serverWindow.getTitle() + "\n" +
                "Mach3PendantServerVersion = " + Mach3PendantServerVersion;
    }
}
