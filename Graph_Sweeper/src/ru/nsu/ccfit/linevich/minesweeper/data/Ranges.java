package ru.nsu.ccfit.linevich.minesweeper.data;

import java.util.ArrayList;
import java.util.Random;

public class Ranges {
    private Coords size;
    private ArrayList<Coords> allCoords;
    private Random random=new Random();

    public void setSize(Coords _size){
        size=_size;
        allCoords=new ArrayList<Coords>();
        for(int x=0; x < size.getX();x++)
            for (int y=0; y<size.getY();y++)
                allCoords.add(new Coords(x,y));
    }
    public Coords getSize(){
        return size;
    }
    public ArrayList<Coords> getAllCoords(){
        return allCoords;
    }
    boolean inRange(Coords coords){
        return coords.getX()>=0&&coords.getY()>=0
                &&coords.getX()<size.getX()&&coords.getY()<size.getY();
    }
    public Coords getRandomCoords(){
        return new Coords(random.nextInt(size.getX()),random.nextInt(size.getY()));
    }
    public ArrayList<Coords> getCoordsAround(Coords coords){
        Coords around;
        ArrayList<Coords> list=new ArrayList<Coords>();
        for(int x = coords.getX()-1; x<=coords.getX()+1;x++)
            for(int y = coords.getY()-1; y<=coords.getY()+1;y++)
                if(inRange(around=new Coords(x,y)))
                    if(!around.equals(coords))
                        list.add(new Coords(x,y));
        return list;
    }
}
