package TreeChat;

import java.net.*;
import java.util.*;
import org.json.*;

class Tree extends Thread {
    private DatagramSocket socket;
    private SocketAddress alternate;
    private SocketAddress death;

    public Tree(DatagramSocket socket, SocketAddress alternate, SocketAddress death) {
        this.socket = socket;
        this.death = death;
        this.alternate = alternate;
    }

    @Override
    public void run() {
        try {
            Client.sendHello(socket, alternate);
            if (Client.alternate.equals(death))
                if (!Client.neighbourList.isEmpty()) {
                    Set<SocketAddress> neighbourAlter = Client.neighbourList.keySet();
                    Iterator iterator = neighbourAlter.iterator();
                    Client.alternate = (InetSocketAddress) iterator.next();
                    JSONObject alterIp = new JSONObject();
                    alterIp.put("id", -1);
                    alterIp.put("name", Client.name);
                    alterIp.put("ip", new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), Client.PORT));
                    alterIp.put("data", Client.alternate.getHostName());
                    alterIp.put("port", Client.alternate.getPort());
                    while (iterator.hasNext()) {
                        SocketAddress neighbour = (SocketAddress) iterator.next();
                        ClientSend.sendQueue.add(Map.entry(neighbour, alterIp));
                    }
                } else
                    Client.alternate = new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
