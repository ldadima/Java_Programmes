package ru.nsu.ccfit.linevich.minesweeper.controller;

import ru.nsu.ccfit.linevich.minesweeper.data.*;

public class Game {

    private Bomb bomb;
    private Flag flag;
    private GameState state;
    private Ranges game_ranges;

    public Game(int bombs,Ranges gr) {
        game_ranges=gr;
        bomb = new Bomb(game_ranges,bombs);
        flag = new Flag(game_ranges);
    }

    public void start() {
        state = GameState.PLAYED;
        bomb.start();
        flag.start();
    }

    public GameState getState() {
        return state;
    }

    public Field getField(Coords coords) {
        if (flag.get(coords) != Field.OPENED)
            return flag.get(coords);
        return bomb.get(coords);
    }

    public void pressLeftButton(Coords coords) {
        openField(coords);
        checkWinner();
    }

    private void openField(Coords coords) {
        switch (flag.get(coords)) {
            case OPENED:
                if (bomb.get(coords).ordinal() == flag.countFlagAround(coords) && flag.countFlagAround(coords) != 0) {
                    openFieldsAroundWithoutFlags(coords);
                }
                return;
            case CLOSED:
                openClosedField(coords);
                return;
            default:
                return;
        }
    }

    private void openClosedField(Coords coords) {
        switch (bomb.get(coords)) {
            case ZERO:
                openFieldsAround(coords);
                return;
            case BOMB:
                openBombs(coords);
                return;
            default:
                flag.setOpenedToField(coords);
                return;
        }
    }

    private void openBombs(Coords bombed) {
        state = GameState.BOOM;
        flag.setBombedFields(bombed);
        for (Coords coords : game_ranges.getAllCoords()) {
            if (bomb.get(coords) == Field.BOMB)
                flag.setOpenedToClosedBombField(coords);
            else
                flag.setNoBombToFlagSafeField(coords);
        }
    }

    private void openFieldsAround(Coords coords) {
        flag.setOpenedToField(coords);
        for (Coords around : game_ranges.getCoordsAround(coords))
            openField(around);

    }

    private void openFieldsAroundWithoutFlags(Coords coords) {
        for (Coords around : game_ranges.getCoordsAround(coords))
            if (flag.get(around) == Field.CLOSED) openField(around);
    }

    public void pressRightButton(Coords coords) {
        flag.toggleField(coords);
    }

    private void checkWinner() {
        if (state == GameState.PLAYED)
            if (flag.getCountOfClosedFields() == bomb.getTotalBombs())
                state = GameState.WON;
    }
}
