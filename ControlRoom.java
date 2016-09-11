
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.UUID;

class ControlRoom {

    int globalID = 0;
    HashMap<UUID, Node> nodeMap = new HashMap();
    HashMap<UUID, Node> ourMap = new HashMap();
    HashMap<Integer, Client> clientMap = new HashMap();
    ArrayList<String> hostList = new ArrayList(5);

    public void test() {

        Server server = new Server(nodeMap, this);
        server.start();

        Scanner scan = new Scanner(System.in);
        while (true) {
            System.out.println("Welcome to the Control Room. Please input a command");
            switch (scan.nextLine()) {
                case "help":
                    System.out.println("Help message");
                    System.out.println("1: Create a new node on the host");
                    System.out.println("2: Create a new node on the host with admin node powers");
                    System.out.println("deleteNode: Select a node on the host to delete");
                    System.out.println("printHost: print all the nodes on this host");
                    System.out.println("createMulti: create multiple nodes at once without admin powers");
                    System.out.println("adminNode: Give a node admin powers");
                    System.out.println("ip: Check which IP the bot is searching for");
                    System.out.println("Exit: exit the program");
                    break;
                case "1":
                    System.out.println("Creating new node on the host");
                    createClient(0);
                    break;
                case "2":
                    System.out.println("Creating new node on the host with admin");
                    createClient(1);
                    break;
                case "deleteNode":
                    System.out.println("Which node do you wish to shutdown on the host?");
                    int toDelete = Integer.parseInt(scan.nextLine());
                    boolean success = shutDownNode(toDelete);
                    if (success) {
                        System.out.println("Node " + toDelete + " successfully shutdown");
                    } else {
                        System.out.println("Node " + toDelete + " did not shutdown properly");
                    }
                    break;
                case "printHost":
                    System.out.println("Posting current host's node list");
                    printHostNodes();
                    break;
                case "createMulti":
                    System.out.println("How many nodes do you want to create on the host?");
                    int creationAmount = Integer.parseInt(scan.nextLine());
                    for (int i = 0; i < creationAmount; i++) {
                        createClient(0);
                    }
                    break;
                case "printAll":
                    printAllNodesByUUID();
                    break;
                case "controlNode":
                    System.out.println("Take control of which node on this host?");
                    printHostNodes();
                    int getNode = 0;
                    try {
                        getNode = Integer.parseInt(scan.nextLine());
                    } catch (NumberFormatException ex) {

                    }

                    Client c = getClient(getNode);
                    if (c != null) {
                        c.takeControl();
                    } else {
                        System.out.println("Specificed node does not exist");
                    }
                    break;
                case "deleteAll":
                    System.out.println("Deleting all host nodes");
                    deleteAllNodes();
                    break;
                case "ip":
                    System.out.println("Current search IP: " + clientMap.get(0).debug_value);
                    break;
                case "adminNode":
                    System.out.println("Select which node to be the admin node on this host");
                    printHostNodes();
                    int j = Integer.parseInt(scan.nextLine());
                    Client x = getClient(j);
                    if (x != null) {
                        x.node.status = 1;
                    } else {
                        System.out.println("Specificed node does not exist");
                    }
                    break;
                case "exit":
                    System.out.println("Exiting program");
                    System.exit(0);
                    break;
                default:
                    System.out.println("Invalid input: type help for assitance");
            }
        }
    }

    public void printHostNodes() {
        clientMap.forEach((k, v) -> {
            System.out.println("GlobalID: " + k);
            System.out.println("UUID: " + v.node.ID);
            System.out.println("host:" + v.node.host);
            System.out.println("Power: " + v.node.status);
        });
    }

    public void printAllNodesByUUID() {
        nodeMap.forEach((k, v) -> {
            System.out.println("UUID: " + k);
            System.out.println("host: " + v.host);
            //   System.out.println("Power: " + v.status);
        });
    }

    public void deleteAllNodes() {
        clientMap.forEach((k, v) -> {
            v.interrupt();
        });
        clientMap.clear();
        nodeMap.clear();
        ourMap.clear();
        hostList.clear();
    }

    public boolean shutDownNode(int nodeToKill) {
        Client c;
        if ((c = clientMap.get(nodeToKill)) != null) {
            nodeMap.remove(c.node.ID);
            ourMap.remove(c.node.ID);
            c.interrupt();
            clientMap.remove(nodeToKill);

            return true;
        }
        return false;

    }

    public Client getClient(int client) {
        Client c = clientMap.get(client);
        return c;
    }

    public int createClient(int status) {
        UUID ID = UUID.randomUUID();
        Node n = new Node(ID, nodeMap);
        Client c = new Client(n, nodeMap, globalID, this, status);
        c.start();
        clientMap.put(globalID, c);
        System.out.println("Node number " + globalID + " created");
        globalID++;
        return globalID - 1;
    }
}
