package ringkv;

import java.io.*;
import java.net.*;
import java.util.*;

public class NodeMain {
    private ServerSocket server = null;
    private Socket socket = null;
    private DataInputStream in = null;
    private InetAddress address = InetAddress.getLocalHost();

    public NodeMain(int port) {
        try {
            this.server = new ServerSocket(port); 
            System.out.println("RingKV started!");

            System.out.println("Waiting for clients to connect...");

            this.socket = this.server.accept();
            System.out.printf("Client connected on port %d\n", port);

            this.in = new DataInputStream(socket.getInputStream());

            String msg = "";

            while (!msg.equals("over")) {
                try {
                    System.out.println("Message received...");
                    msg = in.readUTF();
                } 
                catch (IOException IOEx) {
                    System.out.println(IOEx);
                }
            }
        }
        catch (IOException IOEx) {
            System.out.println(IOEx);
        }
    }

    public String getIpAddress() {
        return this.address.getHostAddress(); 
    }

    public static void main(String[] args) {

    }       
}

