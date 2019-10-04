public class Main {
    public static void main(String[] args) {
        //System.out.println(args[0]);
        new Client().start(args[0],Integer.valueOf(args[1]),args[2]);
    }
}
