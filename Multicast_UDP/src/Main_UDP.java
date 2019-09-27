import java.io.*;
import java.net.*;
import java.util.*;

public class Main_UDP {
    public static void main(String[] args) throws IOException {
        /*Map<Integer,Integer> mm = new HashMap();
        mm.put(1,1);
        mm.put(2,3);
        mm.put(3,4);
        ArrayList<Integer> rem=new ArrayList<>();
        for(Map.Entry<Integer,Integer> pr:mm.entrySet()){
            pr.setValue(pr.getValue()+1);
            if(pr.getValue()==2)
               rem.add(pr.getKey());
        }
        for(Integer s:rem)
            mm.remove(s);
        for(Map.Entry<Integer,Integer> pr:mm.entrySet())
            System.out.println(pr.getKey()+"->"+pr.getValue());
        MulticastSocket s=new MulticastSocket(5555);
        s.joinGroup(InetAddress.getByName("230.1.1.1"));*/
        new Server().start(5555);
    }
}

