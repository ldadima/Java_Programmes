package TreeChat;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public class Client {
    static Map<SocketAddress, Map.Entry<SocketAddress, Integer>> neighbourList = new HashMap<>();
    static InetSocketAddress alternate;
    static String name;
    static int PORT;

    public void start(String myname, int myPORT, int percent, String ip, int rPORT) {
        PORT = myPORT;
        name = myname;
        try (DatagramSocket client = new DatagramSocket(PORT)) {
            client.setSoTimeout(10000);
            if (!"".equals(ip)) {
                neighbourList.put(new InetSocketAddress(ip, rPORT), Map.entry(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0), 0));
                alternate = new InetSocketAddress(ip, rPORT);
                sendHello(client, new InetSocketAddress(ip, rPORT));
            } else alternate = new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0);
            new ClientSend(client).start();
            new ClientRcv(client, percent).start();
            try {
                int num = 0;
                while (true) {
                    num++;
                    String msg;
                    Scanner input = new Scanner(System.in);
                    msg = input.nextLine();
                    JSONObject message = new JSONObject();
                    message.put("id", num);
                    message.put("name", name);
                    message.put("ip", InetAddress.getLocalHost().getHostAddress());
                    message.put("port",PORT);
                    message.put("data", msg);
                    byte[] buf = message.toString().getBytes(StandardCharsets.UTF_8);
                    for (Map.Entry<SocketAddress, Map.Entry<SocketAddress, Integer>> neighbour : neighbourList.entrySet()) {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length, neighbour.getKey());
                        sendTCPOne(client, packet);
                        ClientSend.sendQueue.add(Map.entry(packet.getSocketAddress(), message));
                    }
                }
            } catch (Exception err) {
                err.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void sendTCPOne(DatagramSocket socket, DatagramPacket packet) {
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void sendHello(DatagramSocket socket, SocketAddress ip) {
        try {
            JSONObject hello = new JSONObject();
            hello.put("id", 0);
            hello.put("name", name);
            hello.put("ip", new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), PORT));
            hello.put("data", "hello");
            DatagramPacket connect = new DatagramPacket(hello.toString().getBytes(StandardCharsets.UTF_8), hello.toString().getBytes(StandardCharsets.UTF_8).length, ip);
            sendTCPOne(socket, connect);
            ClientSend.sendQueue.add(Map.entry(connect.getSocketAddress(), hello));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void timeUpdate(DatagramSocket socket, SocketAddress ip, int time) {
        try {
            if (!ip.equals(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))&&!neighbourList.isEmpty())
                neighbourList.put(ip, Map.entry(neighbourList.get(ip).getKey(), 0));
            for (Iterator<Map.Entry<SocketAddress, Map.Entry<SocketAddress, Integer>>> iterator = neighbourList.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<SocketAddress, Map.Entry<SocketAddress, Integer>> one = iterator.next();
                if (!ip.equals(one.getKey()))
                    one.setValue(Map.entry(one.getValue().getKey(), one.getValue().getValue() + time));
                if (one.getValue().getValue() > 30000) {
                    synchronized (ClientSend.sendQueue) {
                        ClientSend.sendQueue.removeIf(socketAddressJSONObjectEntry ->
                                one.getKey().equals((socketAddressJSONObjectEntry).getKey()));
                    }
                    if (!one.getValue().getKey().equals(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))) {
                        //sendHello(socket, one.getValue().getKey());
                        new Tree(socket, one.getValue().getKey(), one.getKey()).start();
                    }
                    iterator.remove();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
