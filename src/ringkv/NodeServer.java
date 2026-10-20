package ringkv;

import java.io.*;
import java.net.*;
import java.util.*;

public class NodeServer {
    private ServerSocket server = null;
    private Vector<Socket> sockets = new Vector<>();
    private DataInputStream in = null;
    private HashMap<String, String> dataMap = new HashMap<>();

    public ServerSocket getServer() {
        return this.server;
    }

    public Vector<Socket> getSockets() {
        return this.sockets;
    }

    public DataInputStream getInputStream() {
        return this.in;
    }
    public NodeServer(ServerSocket server, Socket socket, DataInputStream in) {
        this.server = server;
        this.sockets.add(socket);
        this.in = in;
    }

    public static NodeServer start() {
        Scanner scan = new Scanner(System.in); 
        System.out.printf("Type the port to listen to: ");

        String portStr = scan.nextLine();
        int port = Integer.parseInt(portStr);

        scan.close();

        ServerSocket server = null;
        Socket socket = null;
        DataInputStream in = null;

        try {
            server = new ServerSocket(port);

            System.out.println("Waiting for clients to connect...");

            socket = server.accept();
            System.out.printf("Client connected on port %d\n", port);

            in = new DataInputStream(socket.getInputStream());
        }
        catch (IOException IOEx) {
            System.out.println(IOEx);
        }

        return new NodeServer(server, socket, in);
    }

    public void run() {
        String msg = "";

        while (!msg.equals("stop")) {
            try { 
                for (int i = 0; i < this.sockets.size(); i++) {
                    this.in = new DataInputStream(this.sockets.get(i).getInputStream());
                    msg = this.in.readUTF();

                    String[] parsed = parseMessage(msg);
                    execCmd(parsed);
                }
            }
            catch (IOException ex) {
                stop();
                System.out.println(ex);
            }
        }

        System.out.println("Terminating server...");
    }

    public void stop() {
        for (int i = 0; i < this.sockets.size(); i++) {
            sockets.get(i).close();
        }

        this.server.close();
    }

    private String[] parseMessage(String msg) {
        String[] parsed = msg.split(" "); 
        parsed[0] = parsed[0].toUpperCase();

        return parsed;
    }

    private void execCmd(String[] parsed) {
        switch (parsed[0]) {
            case "GET":
                String keyGet = parsed[1];
                String valueGet = getCmd(keyGet);
                System.out.println("Key: " + keyGet + "/Value: " + valueGet);
            case "PUT":
                String keyPut = parsed[1];
                String valuePut = parsed[2];
                putCmd(keyPut, valuePut); 
                System.out.println("Key " + keyPut + " added with value " + valuePut);
            case "DEL":
                String keyDel = parsed[1];
                System.out.println("Key " + keyDel + " deleted");
            case "ADD":
                String keyAdd = parsed[1];
                String valueAdd = parsed[2];
                addCmd(keyAdd, valueAdd);
                System.out.println("Key " + keyAdd + " modified to value " + valueAdd);
            case "STOP":
                System.out.println("Closing server...");
                stop();
            default:
                System.out.println("Invalid command.");
        }
    }

    //TODO: find a way to check which client is the one sending a command
    //This should be done later, when concurrency is in play
    private boolean checkClientSequence(String msg) {
        HashMap<String, String> cloneMap = (HashMap<String, String>) this.dataMap.clone();
        String[] parsed = parseMessage(msg); 
    }

    private void putCmd(String key, String value) {
        this.dataMap.put(key, value);
    }

    private String getCmd(String key) {
        String value = this.dataMap.get(key);

        return value;
    }

    private void delCmd(String key) {
        this.dataMap.remove(key);
    }

    private void addCmd(String key, String value) {
        String currValueStr = this.dataMap.get(key);

        int newValue = Integer.parseInt(currValueStr) + Integer.parseInt(value);
        String finalValue = Integer.toString(newValue);

        this.dataMap.replace(key, finalValue);
    }
    
    private void addClient(Socket socket) {
        sockets.add(socket);
    }

    private void acceptClient() {
        Socket newSocket = this.server.accept();
        addClient(newSocket);

        System.out.println("New client connected...");
    }
}
