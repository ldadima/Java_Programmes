import TreeChat.*;

public class Main {
    public static void main(String[] args) {
        if(args.length>3)
            new Client().start(args[0], Integer.valueOf(args[1]),Integer.valueOf(args[2]),args[3],Integer.valueOf(args[4]));
        else
            new Client().start(args[0], Integer.valueOf(args[1]),Integer.valueOf(args[2]),"", 0);
        /*Map<Integer,Integer> map = new HashMap<>();
        map.put(2,3);
        map.put(3,4);
        for(Iterator iterator=map.entrySet().iterator();iterator.hasNext();){
            Map.Entry<Integer,Integer> one = (Map.Entry<Integer, Integer>) iterator.next();
            if(one.getKey()==2)
                one.setValue(one.getValue()+2);
            if(one.getValue()>4)
                iterator.remove();
        }
        map.forEach((key,value)-> System.out.println(key+" - "+ value));*/
        /*try {
            JSONObject hello = new JSONObject();
            hello.put("id", 0);
            System.out.println(new String(hello.toString().getBytes(StandardCharsets.UTF_8)) + "\n" +hello.toString().getBytes(StandardCharsets.UTF_8).length);
            System.out.println();
        }
        catch (Exception e){

        }*/
    }
}
