
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server extends Thread {

    HashMap<UUID, Node> nodeMap;
    ControlRoom control;

    public Server(HashMap nodeMap, ControlRoom control) {
        this.nodeMap = nodeMap;
        this.control = control;
    }

    @Override
    public void run() {
        try {
            ServerSocket socket = new ServerSocket(4444);
            System.out.println("Server socket created");
            while (true) {
                Socket clientSocket = socket.accept();

                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                out.flush();
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());

                Node data = (Node) in.readObject();
                boolean add = true;
                if(data.command != 0) {
                    System.out.println("Command for " + data.ID + " is " + data.command);
                }
                

                switch (data.command) {
                    case 2: // Shut down node
                        System.out.println("command 2 recieved");
                        Node n = nodeMap.get(data.recipient);
                        if (n != null) {
                            Client c = control.clientMap.get(n.globalID);
                            System.out.println("Deleting node " + n.ID);
                            c.interrupt();
                            nodeMap.remove(data.recipient);
                            control.clientMap.remove(n.globalID);
                            System.out.println("Deleted");
                            out.write(0);
                        } else {
                            out.write(1);
                        }

                        add = false;
                        break;
                    case 3: // ping, Not pinging by node but by host
                        out.write(1);
                        break;
                    case 4: // bandwidth calculation by host
                        System.out.println("reading bandwidth");
                        int bytes;
                        int total = 0;
                        long start = System.currentTimeMillis();

                        while ((bytes = in.read()) != -1) {
                            total += 1;
                        }

                        long time = System.currentTimeMillis() - start;
                        System.out.println("time in ms: " + time);
                        System.out.println("size in bytes: " + total);
                        double seconds = time / 1000.0;
                        double megabytes = total / 1048576.0; // 2^20
                        System.out.println("time in seconds: " + seconds);

                        System.out.println("size in megabytes: " + megabytes);

                        double result = megabytes / seconds;
                        out.writeDouble(result);
                        out.flush();
                        System.out.println(bytes);
                        System.out.println("finished");
                        break;
                    case 0:
                        if(data.status == 1) {
                            System.out.println("Server writing object");
                            out.writeObject(control.ourMap);
                            out.flush();
                        }
                        break;
                    case 5:
                        System.out.println(data.size);
                        byte[] buf = new byte[data.size];
                        
                        try{
                            in.readFully(buf, 0, data.size);
                        }catch(EOFException ex) {
                            
                        }
                        boolean f = new File("DirectoryForTest").mkdir();
                        FileOutputStream fos = new FileOutputStream("DirectoryForTest\\transfer.txt");
                        BufferedOutputStream bos = new BufferedOutputStream(fos);
                        bos.write(buf);
                        bos.close();
                        fos.close();
                        System.out.println("done transferring files");
                        break;
                    default:

                }

                if ((!nodeMap.containsKey(data.ID)) && (add == true)) {
                    data.command = 0;
                    nodeMap.put(data.ID, data);
                }
                if ((!control.hostList.contains(data.host)) && (add == true)) {
                    control.hostList.add(data.host);
                }

                out.flush();
                out.close();
                in.close();
                clientSocket.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
