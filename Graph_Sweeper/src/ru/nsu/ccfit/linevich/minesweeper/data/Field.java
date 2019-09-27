package ru.nsu.ccfit.linevich.minesweeper.data;

public enum Field {
    ZERO,
    NUM1,
    NUM2,
    NUM3,
    NUM4,
    NUM5,
    NUM6,
    NUM7,
    NUM8,
    BOMB,
    OPENED,
    CLOSED,
    FLAG,
    BOOM,
    NOBOMB,
    INFORM;

    public Object image;

    Field getNextNum(){
        return Field.values()[this.ordinal()+1];
    }
}
