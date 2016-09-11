
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Scanner;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client extends Thread {

    Node node;
    HashMap<UUID, Node> nodeMap;
    int globalID;
    int debug_value = 0;

    Socket socket = null;
    ObjectInputStream in = null;
    ObjectOutputStream out = null;
    ControlRoom control;
    boolean stall = false;

    public Client(Node n, HashMap<UUID, Node> nodeMap, int globalID, ControlRoom control, int status) {
        this.node = n;
        this.nodeMap = nodeMap;
        this.globalID = globalID;
        n.globalID = globalID;
        this.control = control;
        node.status = status;
        control.ourMap.put(node.ID, node);

    }

    @Override
    public void run() {
        int start = 2;
        int num = 0;
        boolean search = true;
        try {
            do {
                if (stall == false) {
                    if (search == true) {
                        System.out.println("Resuming search");
                        start = searchForNodes(start);
                        if (start > 20) {
                            search = false;
                        }
                    } else if (search == false) {
                        contactKnownNodes();
                        num++;
                    }

                }
                if (num > 6) {
                    num = 0;
                    search = true;
                    start = 2;
                }
                Thread.sleep(10000);
            } while (!this.isInterrupted());
        } catch (InterruptedException ex) {
            try {
                closeObjectStreams();
                socket.close();
            } catch (IOException ex1) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex1);
            }
        }
        System.out.println("Shutting down node " + globalID);
    }

    public Node getNode() {
        return this.node;
    }

    public void takeControl() {
        try {
            Scanner scan = new Scanner(System.in);
            System.out.println("What do you want this Node to do?");
            String command = scan.nextLine();
            Node n;
            String input;
            switch (command) {
                case "help":
                    System.out.println("deleteNode: Choose a node on this host or another host to delete");
                    System.out.println("ping: ping a node a node/host (measured in ms, extra data on node's host machine stdout)");
                    System.out.println("bandwidth: Get the average bandwidth between you and a node");
                    System.out.println("updateNodes: (admin) Get a list of nodes that another node has vision of and add it to your own list");
                    System.out.println("updateNodesIP: (admin) Same as above but you can check a random IP address");
                    System.out.println("transferFile: Transfer a sample text document from one node to another");
                    System.out.println("deleteSelf: delete this node");
                    break;
                case "deleteNode": // Command = 2
                    System.out.println("Choose a node to delete");
                    control.printAllNodesByUUID();
                    UUID toDelete = UUID.fromString(scan.nextLine());
                    n = nodeMap.get(toDelete);
                    if (n != null) {
                        node.recipient = n.ID;
                        node.setCommand(2);
                        System.out.println("transmitting");
                        stallAndTransmit(n.host);
                        int success = in.read();

                        if (success == 0) {
                            System.out.println("The remote node shut down properly");
                            nodeMap.remove(n.ID);
                        } else {
                            System.out.println("The remove node does not exist or did not shut down properly");
                        }
                        node.setCommand(0);
                    } else {
                        System.out.println("UUID input does not exist in our mapping");
                    }
                    stall = false;

                    break;
                case "ping": // command = 3;
                    System.out.println("Coose a node to ping by host");
                    control.printAllNodesByUUID();
                    UUID ping = UUID.fromString(scan.nextLine());
                    n = nodeMap.get(ping);
                    if (n != null) {
                        node.recipient = n.ID;
                        node.setCommand(3);
                        long start = stallForPing(n.host);

                        int i = in.read();
                        if (i == 1) {
                            long time = System.currentTimeMillis() - start;
                            System.out.println("Your ping to host " + n.host + " is " + time + "ms");
                        } else {
                            System.out.println("Error! Could not ping node/host. Is it still alive?");
                        }

                        node.setCommand(0);
                        closeObjectStreams();
                    } else {
                        System.out.println("UUID input does not exist in our mapping");
                    }
                    stall = false;
                    break;
                case "bandwidth": // command = 4;
                    System.out.println("Choose a node to determine your network speed");
                    control.printAllNodesByUUID();
                    UUID bandwidth = UUID.fromString(scan.nextLine());
                    n = nodeMap.get(bandwidth);
                    node.recipient = n.ID;
                    node.setCommand(4);
                    byte[] bytes = new byte[1024];
                    stallAndTransmitBandwidth(n.host, node, bytes);
                    socket.shutdownOutput();

                    double result = in.readDouble();
                    String pattern = "###.##";
                    DecimalFormat formatter = new DecimalFormat(pattern);
                    System.out.println(node.ID + "'s average speed to " + node.recipient + " is " + formatter.format(result) + "MB/s");

                    node.setCommand(0);
                    stall = false;
                    System.out.println("Finished bandwidth");
                    closeObjectStreams();
                    break;
                case "updateNodes":
                    if (node.status == 1) {
                        System.out.println("Choose a node to search by");
                        control.printAllNodesByUUID();
                        UUID mapNode = UUID.fromString(scan.nextLine());
                        n = nodeMap.get(mapNode);
                        node.recipient = n.ID;
                        node.setCommand(0);
                        socketAndForeignNodes(n.host, node);
                        closeObjectStreams();
                        System.out.println("finished update");
                    } else {
                        System.out.println("This nodes does not have permissions for this feature");
                    }
                    stall = false;
                    break;

                case "updateNodesIP":
                    if (node.status == 1) {
                        System.out.println("Current node list for reference above");
                        control.printAllNodesByUUID();
                        String nodeHost = scan.nextLine();
                        node.setCommand(0);
                        socketAndForeignNodes(nodeHost, node);
                        closeObjectStreams();
                        System.out.println("finished update");
                    } else {
                        System.out.println("This nodes does not have permissions for this feature");
                    }
                    stall = false;
                    break;
                case "transferFile":
                    System.out.println("Choose a host to transfer the file to");
                    control.printAllNodesByUUID();
                    String nodeHost = scan.nextLine();
                    File f = new File("transfer.txt");
                    long size = f.length();
                    InputStream file = new FileInputStream("transfer.txt");

                    int c;
                    socket = new Socket(nodeHost, 4444);
                    node.command = 5;
                    int s = (int) size;
                    byte[] buffer = new byte[s];
                    node.size = s;
                    createNewObjectStreams();
                    out.writeObject(node);
                    out.flush();

                    while ((c = file.read(buffer, 0, buffer.length)) != -1) {
                        out.write(buffer, 0, c);
                        out.flush();
                    }
                    closeObjectStreams();
                    node.command = 0;
                    break;
                case "deleteSelf": // No command
                    control.nodeMap.remove(node.ID);
                    control.clientMap.remove(globalID);
                    this.interrupt();
                    stall = false;
                    System.out.println("This should never happen");
                    break;
                default:
                    System.out.println("Invalid command. Returning to Control Room");

            }
        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            System.out.println("Not a proper UUID, exiting control node");
        }

    }

    public void stallAndTransmit(String IP) {
        stall = true;
        while (stall) {
            if (socket.isClosed()) {
                try {
                    socket = new Socket(IP, 4444);

                    createNewObjectStreams();
                    out.writeObject(node);
                } catch (IOException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                }
                return;
            }
        }
    }

    public long stallForPing(String IP) {
        long start = 0;
        stall = true;
        while (stall) {
            if (socket.isClosed()) {
                try {
                    start = System.currentTimeMillis();
                    socket = new Socket(IP, 4444);
                    createNewObjectStreams();
                    out.writeObject(node);
                    return start;
                } catch (IOException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                }
                stall = false;
            }
        }
        return start;
    }

    public void stallAndTransmitBandwidth(String IP, Node node, byte[] data) {
        stall = true;
        while (stall) {
            if (socket.isClosed()) {
                try {
                    socket = new Socket(IP, 4444);
                    createNewObjectStreams();
                    out.writeObject(node);
                    out.flush();
                    for (int i = 0; i < 500000; i++) {
                        out.write(data);
                        out.flush();
                    }
                    out.write(-1);
                    out.flush();
                } catch (IOException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                }
                return;
            }

        }
    }

    public void createNewObjectStreams() {
        try {
            out = new ObjectOutputStream((socket.getOutputStream()));
            out.flush();
            in = new ObjectInputStream((socket.getInputStream()));

        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void closeObjectStreams() {
        try {
            out.flush();
            out.close();
            in.close();
        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void contactKnownNodes() {
        nodeMap.forEach((k, v) -> {
            try {
                if (stall == true) {
                    return;
                }

                socket = new Socket();
                socket.connect(new InetSocketAddress(v.host, 4444), 1000);
                in = new ObjectInputStream(socket.getInputStream());
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();

                writeNode(out);
                out.flush();
                getForeignNodeData();
                socket.close();
                in.close();
                out.close();
            } catch (IOException ex) {
                System.out.println("Could not connect to node, removing from list");

                remove(k);
            }
        });
    }

    public int searchForNodes(int start) {

        try {
            String ip = null;
            for (int i = start; i <= 25; i++, socket.close()) {
                try {

                    start = i;
                    debug_value = i;
                    if (stall == true) {
                        return start;
                    }
                    ip = "192.168.1." + i;
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(ip, 4444), 1000);
                    in = new ObjectInputStream(socket.getInputStream());
                    out = new ObjectOutputStream(socket.getOutputStream());
                    out.flush();

                    writeNode(out);
                    out.flush();
                    getForeignNodeData();
                    socket.close();
                    in.close();
                    out.close();

                } catch (IOException ex) {
                    socket.close();
                }
            }
            return start;
        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);

        }
        return start;
    }

    public void socketAndForeignNodes(String ip, Node data) {
        stall = true;
        while (stall) {
            if (socket.isClosed()) {
                try {
                    socket = new Socket(ip, 4444);
                    createNewObjectStreams();
                    out.writeObject(node);
                    out.flush();
                    getForeignNodeData();
                } catch (IOException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                    return;

                }
                return;

            }
        }
    }

    public void remove(UUID k) {
        try {
            nodeMap.remove(k);
        } catch (ConcurrentModificationException ex) {

        }
    }

    public void getForeignNodeData() {
        if (node.status == 1) {
            try {
                HashMap<UUID, Node> newMap = (HashMap<UUID, Node>) in.readObject();

                newMap.forEach((k, v) -> {
                    if (!nodeMap.containsKey(k)) {
                        nodeMap.put(k, v);
                    }
                });
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void writeNode(ObjectOutputStream out) {
        try {
            out.writeObject(node);
            out.flush();

        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
