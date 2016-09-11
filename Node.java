
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Node implements Serializable {

    UUID ID;
    String host;
    HashMap nodeMap;
    int globalID;
    UUID recipient;
    int status = 0;
    int command = 0;
    int size = 0;
    
    public Node(UUID ID, HashMap nodeMap) {
        try {
            this.ID = ID;
            this.host = InetAddress.getLocalHost().getHostAddress();
            this.nodeMap = nodeMap;
        } catch (UnknownHostException ex) {
            Logger.getLogger(Node.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void setCommand(int command){
        this.command = command;
    }
    public void printDetails() {
        System.out.println("UUID: " + ID);
        System.out.println("Host: " + host);
    }
}
