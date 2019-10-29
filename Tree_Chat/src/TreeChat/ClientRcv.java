package TreeChat;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

class ClientRcv extends Thread {
    private DatagramSocket socket;
    private int percent;
    private String name;

    ClientRcv(DatagramSocket socket, int percent) {
        this.socket = socket;
        this.percent = percent;
        this.name = Client.name;
    }

    private void successSend(Integer id, SocketAddress address) {
        try {
            if (id != -2) {
                JSONObject success = new JSONObject();
                success.put("id", -2);
                success.put("ip", new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), Client.PORT));
                success.put("name", name);
                success.put("data", id.toString());
                socket.send(new DatagramPacket(success.toString().getBytes(StandardCharsets.UTF_8), success.toString().getBytes(StandardCharsets.UTF_8).length, address));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
        SocketAddress ipTime;
        ArrayList<Map.Entry<SocketAddress, Integer>> repeatmsg = new ArrayList<>();
        int i = 0;
        while (true) {
            ipTime = new InetSocketAddress("0.0.0.0", 0);
            long time = System.currentTimeMillis();
            try {
                socket.receive(packet);
                try {
                    boolean flag = percent <= (int) (Math.random() * 100);
                    if (flag) {
                        ipTime = packet.getSocketAddress();
                        String s = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                        JSONObject message = new JSONObject(s);
                        successSend(message.getInt("id"), packet.getSocketAddress());
                        switch ((int) message.get("id")) {
                            case -3: {
                                if (Client.alternate.equals(packet.getSocketAddress()))
                                    Client.sendHello(socket, packet.getSocketAddress());
                                else
                                    Client.neighbourList.remove(packet.getSocketAddress());
                                break;
                            }
                            case -2: {
                                synchronized (ClientSend.sendQueue) {
                                    for (Iterator iter = ClientSend.sendQueue.iterator(); iter.hasNext(); ) {
                                        Map.Entry<SocketAddress, JSONObject> one = (Map.Entry<SocketAddress, JSONObject>) iter.next();
                                        if ((one.getKey().equals(packet.getSocketAddress())) && (one.getValue().get("id").toString().equals(message.get("data").toString())))
                                            iter.remove();
                                    }
                                }
                                break;
                            }
                            case -1: {
                                String host = message.getString("data");
                                int port = message.getInt("port");
                                Client.neighbourList.put(packet.getSocketAddress(), Map.entry(new InetSocketAddress(host, port), 0));
                                break;
                            }
                            case 0: {
                                Client.neighbourList.putIfAbsent(packet.getSocketAddress(), Map.entry(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0), 0));
                                if (Client.alternate.equals(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))) {
                                    Client.alternate = (InetSocketAddress) packet.getSocketAddress();
                                } else {
                                    JSONObject alter = new JSONObject();
                                    alter.put("id", -1);
                                    alter.put("ip", new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), Client.PORT));
                                    alter.put("name", name);
                                    alter.put("data", Client.alternate.getHostName());
                                    alter.put("port", Client.alternate.getPort());
                                    ClientSend.sendQueue.add(Map.entry(packet.getSocketAddress(), alter));
                                }

                                break;
                            }
                            default: {
                                boolean contin = Client.neighbourList.get(packet.getSocketAddress()) != null;
                                if (!contin) {
                                    JSONObject remove = new JSONObject();
                                    remove.put("id", -3);
                                    ClientSend.sendQueue.add(Map.entry(packet.getSocketAddress(), remove));
                                }
                                String host = message.getString("ip");
                                int port = message.getInt("port");
                                InetSocketAddress ipmsg = new InetSocketAddress(host,port);
                                if (!repeatmsg.isEmpty() && contin) {
                                    for (Map.Entry<SocketAddress, Integer> one : repeatmsg) {
                                        if (one.getKey().equals(ipmsg) && one.getValue().equals(message.getInt("id")))
                                            contin = false;
                                    }
                                }
                                if (contin) {
                                    repeatmsg.add(i, Map.entry(packet.getSocketAddress(), message.getInt("id")));
                                    i = (i + 1) % 50;
                                    System.out.println(message.get("name") + ": " + message.get("data"));
                                    for (Map.Entry<SocketAddress, Map.Entry<SocketAddress, Integer>> neighbour : Client.neighbourList.entrySet())
                                        if (!packet.getSocketAddress().equals(neighbour.getKey()))
                                            ClientSend.sendQueue.add(Map.entry(neighbour.getKey(), message));
                                }
                            }
                        }
                    }
                } catch (Exception err) {
                    err.printStackTrace();
                }
            } catch (SocketTimeoutException err) {
                System.out.println("No Message");
            }
            catch (Exception e){

            }
            if (!Client.neighbourList.isEmpty())
                Client.timeUpdate(socket, ipTime, (int) (System.currentTimeMillis() - time));
        }
    }
}

