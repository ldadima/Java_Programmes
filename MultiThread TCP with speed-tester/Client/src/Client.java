import java.io.*;
import java.net.*;
import java.nio.*;

public class Client {
    public void start(String ip, int PORT, String filename) {
        Socket clientSocket;
        byte[] buf = new byte[4096];
        try (BufferedInputStream file = new BufferedInputStream(new FileInputStream(filename))){
            long file_len = new File(filename).length();
            System.out.println("Size - " + file_len);
            clientSocket = new Socket(ip, PORT);
            BufferedOutputStream writer = new BufferedOutputStream(clientSocket.getOutputStream());
            filename=new File(filename).getName();
            byte[] bufInt=ByteBuffer.allocate(4).putInt(filename.length()).array();
            writer.write(bufInt,0,4);
            writer.flush();
            writer.write(filename.getBytes(),0,filename.length());
            writer.flush();
            byte[] bufLong=ByteBuffer.allocate(8).putLong(file_len).array();
            writer.write(bufLong,0,8);
            writer.flush();
            int total=0;
            while ( (total=file.read(buf, 0, 4096))!=-1) {
                writer.write(buf, 0, total);
                writer.flush();
            }
            BufferedInputStream reader = new BufferedInputStream(clientSocket.getInputStream());
            int b_err = reader.readNBytes(buf,0,7);
            if(b_err!=7)
                throw new Exception("Exception Len Read");
            String answer = new String(buf,0,7);
            if("Success".equals(answer))
                System.out.println("Success transfer");
            else
                System.out.println("Failure transfer");
        } catch (Exception e) {
            System.out.println(e);
        }

    }

}
