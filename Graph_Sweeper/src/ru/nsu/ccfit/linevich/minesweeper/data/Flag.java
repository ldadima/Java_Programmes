package ru.nsu.ccfit.linevich.minesweeper.data;

public class Flag {
    private Matrix flagMap;
    //private int totalFlags;
    private int countOfClosedFields;
    private Ranges game_ranges;

    public Flag(Ranges gr){
        game_ranges=gr;
    }

    public void start(){
        flagMap = new Matrix(Field.CLOSED,game_ranges);
        countOfClosedFields=game_ranges.getSize().getX()*game_ranges    .getSize().getY();
    }

    public int getCountOfClosedFields(){
        return countOfClosedFields;
    }
    public Field get(Coords coords){
        return flagMap.get(coords);
    }
    public void setOpenedToField(Coords coords){
        if(flagMap.get(coords)!=Field.OPENED) {
            flagMap.set(coords, Field.OPENED);
            countOfClosedFields--;
        }
    }
    public void toggleField(Coords coords){
        switch (flagMap.get(coords)){
            case FLAG:{setInformedToField(coords);return;}
            case INFORM: {setClosedToField(coords);break;}
            case CLOSED: {setFlagToField(coords);break;}
        }
    }
    public void setInformedToField(Coords coords){
        flagMap.set(coords,Field.INFORM);
    }
    public void setClosedToField(Coords coords){
        flagMap.set(coords,Field.CLOSED);
    }
    public void setFlagToField(Coords coords){
        flagMap.set(coords,Field.FLAG);
    }
    public void setBombedFields(Coords coords){
        flagMap.set(coords,Field.BOOM);
    }

    public void setNoBombToFlagSafeField(Coords coords) {
        if(flagMap.get(coords)==Field.FLAG)
            flagMap.set(coords,Field.NOBOMB);
    }

    public void setOpenedToClosedBombField(Coords coords) {
        if(flagMap.get(coords)==Field.CLOSED)
            flagMap.set(coords,Field.OPENED);
    }
    public int countFlagAround(Coords coords) {
        int countFlag = 0;
        for(Coords around : game_ranges.getCoordsAround(coords))
            if (flagMap.get(around) == Field.FLAG)
                countFlag++;
        return countFlag;
    }
}
