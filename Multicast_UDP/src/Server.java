import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    final static String ip = "230.1.1.1";
    private Map<String, Integer> users_list = new HashMap<>();
    private int timerprint=0;

    private void analyse_ip(String ip, Integer time) {
        ArrayList<String> remlist = new ArrayList<>();
        int rem = 0;
        int add = 0;
        if (!"".equals(ip)) {
            if (users_list.get(ip) == null)
                add++;
            users_list.put(ip, time);
        }
        for (Map.Entry<String, Integer> user : users_list.entrySet()) {
            if (!ip.equals(user.getKey()))
                user.setValue(user.getValue() + time);
            if (user.getValue() > 5000) {
                remlist.add(user.getKey());
                rem++;
            }
        }
        for (String s : remlist)
            users_list.remove(s);
        timerprint++;
        if (rem > 0 || add > 0||timerprint==20) {
            System.out.println("----------------------------\nIP's: ");
            if(add>0)
                System.out.println("Was added ip");
            if(rem>0)
                System.out.println("Was removed ip");
            for (Map.Entry<String, Integer> user : users_list.entrySet()) {
                System.out.println(user.getKey());
            }
            if(users_list.isEmpty())
                System.out.println("Empty");
            System.out.println("----------------------------");
            if(timerprint==20)
                timerprint=0;
        }

    }

    public void start(int PORT) throws IOException {
        boolean msg_ex=false;
        byte msg[] = "Hello".getBytes();
        InetAddress addr = InetAddress.getByName(ip);
        //NetworkInterface intface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
        MulticastSocket s = new MulticastSocket(PORT);
        s.joinGroup(addr);
        s.setTimeToLive(10);
        DatagramPacket packet = new DatagramPacket(msg, msg.length);
        packet.setAddress(addr);
        packet.setPort(PORT);
        DatagramPacket rcv_packet = new DatagramPacket(new byte[1024], 1024);
        String recv_ip = "";
        long time = System.currentTimeMillis() - 1000;
        s.setSoTimeout(1000);
        while (true) {
            if ((System.currentTimeMillis() - time) >= 1000) {
                analyse_ip(recv_ip, (int) (System.currentTimeMillis() - time));
                s.send(new DatagramPacket(msg, msg.length, addr, PORT));
                time = System.currentTimeMillis();
                recv_ip = "";
            }
            try {
                s.receive(rcv_packet);
                System.out.println((rcv_packet.getAddress()).toString() + " -> " + new String(rcv_packet.getData(), rcv_packet.getOffset(), rcv_packet.getLength()));
                recv_ip = rcv_packet.getAddress().toString();
                analyse_ip(recv_ip,0);
            } catch (SocketTimeoutException err) {
                System.out.println("Empty msq queue");
            }
        }
    }
}

