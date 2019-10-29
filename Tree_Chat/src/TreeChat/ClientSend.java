package TreeChat;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

class ClientSend extends Thread {
    private DatagramSocket socket;
    static List<Map.Entry<SocketAddress, JSONObject>> sendQueue = Collections.synchronizedList(new ArrayList<>());

    ClientSend(DatagramSocket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            while (true) {
                sleep(100);
                synchronized (ClientSend.sendQueue) {
                    if (!sendQueue.isEmpty())
                        for (Map.Entry<SocketAddress, JSONObject> one : sendQueue) {
                            byte[] buf = one.getValue().toString().getBytes(StandardCharsets.UTF_8);
                            DatagramPacket packet = new DatagramPacket(buf, buf.length, one.getKey());
                            Client.sendTCPOne(socket, packet);
                        }
                }
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}
