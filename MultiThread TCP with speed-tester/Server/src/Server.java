import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Server {


    public void start(int PORT) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                try {
                    //serverClientList.add(
                    new ServerClient(socket).start();
                } catch (Exception err) {
                    socket.close();
                }
            }

        } catch (Exception err) {
            System.out.println(err);
        }
    }
}

class ServerClient extends Thread {
    Socket socket;
    InputStream socketReader;
    int LEN = 4096;

    public ServerClient(Socket socket) throws IOException {
        this.socket = socket;
        this.socketReader = socket.getInputStream();
    }

    @Override
    public void run() {
        byte[] buf = new byte[LEN];
        long allBytes = 0;
        long allTime = System.currentTimeMillis();
        int name_len;
        long fileLen;
        long currLen;
        try {
            long time = System.currentTimeMillis();
            long currBytes = 0;
            currBytes += socketReader.readNBytes(buf, 0, 4);
            byte[] bufInt = new byte[4];
            name_len = ByteBuffer.wrap(buf).getInt();
            if(currBytes!=4){
                throw new Exception("Exception len read");
            }
            currBytes += socketReader.readNBytes(buf, 0, name_len);
            if(currBytes!=4+name_len){
                throw new Exception("Exception len read");
            }
            String filename = new String(buf, 0, name_len, StandardCharsets.UTF_8);
            try(BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream("uploads/" + new File(filename).getName()))) {
                currBytes += socketReader.readNBytes(buf, 0, 8);
                if(currBytes!=4+8+name_len){
                    throw new Exception("Exception len read");
                }
                fileLen = ByteBuffer.wrap(buf).getLong();
                currLen = 0;
                socket.setSoTimeout(10000);
                while (currLen < fileLen) {
                    int totalRead = socketReader.read(buf, 0, LEN);
                    if (totalRead != -1) {
                        currLen += totalRead;
                        currBytes += totalRead;
                        fileOut.write(buf, 0, totalRead);
                        long timepr = System.currentTimeMillis() - time;
                        if (timepr >= 3000) {
                            System.out.println("Current Speed of " + new File(filename).getName() + " = " + Long.valueOf(currBytes / timepr).toString() + " Kb/s");
                            currBytes = 0;
                            time = System.currentTimeMillis();
                        }
                    }
                }
            }
            allTime = (System.currentTimeMillis() - allTime);
            allBytes = 4 + 8 + name_len + currLen;
            try(OutputStream writer = socket.getOutputStream()){
            if (fileLen == new File("uploads/" + new File(filename).getName()).length())
                writer.write("Success".getBytes(StandardCharsets.UTF_8));
            else
                writer.write("Failure".getBytes(StandardCharsets.UTF_8));
            writer.flush();
            }
            long totalSpeed;
            if (allTime != 0)
                totalSpeed = allBytes / allTime;
            else {
                totalSpeed = allBytes;
                System.out.println("Infinite Speed");
            }
            System.out.println("Total Size of "+ new File(filename).getName() + " - " + fileLen + " bytes");
            System.out.println("Total Transfer Time of "+ new File(filename).getName() + "- " + allTime/1000 + " sec");
            System.out.println("Total " + new File(filename).getName() + " transfer speed = " + totalSpeed + " Kb/s");
            downService();
        } catch (Exception err) {
            System.out.println(err);
            downService();
        }
        finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }

    private void downService() {
        System.out.println("Client " + socket.toString() + " OFFED");
        try {
            if (!socket.isClosed()) {
                socketReader.close();
                /*for (ServerClient vr : Server.serverClientList) {
                    if(vr.equals(this)) vr.interrupt();
                    Server.serverClientList.remove(this);
                }*/
            }
        } catch (IOException ignored) {
        }
    }
}
