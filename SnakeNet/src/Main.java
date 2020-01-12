import me.ippolitov.fit.snakes.SnakesProto;

import java.awt.*;
import java.util.ArrayList;
import java.util.*;

public class Main{
     public static void main(String[] args)  {
         int port=Integer.parseInt(args[0]);
         new Game(port).start();
     }
}
