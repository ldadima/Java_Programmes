package ru.nsu.ccfit.linevich.minesweeper.data;

class Matrix {
    private Field[][] matrix;
    private Ranges game_ranges;

    Matrix(Field defaultField, Ranges gr) {
        game_ranges=gr;
        matrix = new Field[game_ranges.getSize().getX()][game_ranges.getSize().getY()];
        for (Coords coords : game_ranges.getAllCoords())
            matrix[coords.getX()][coords.getY()] = defaultField;
    }
    Field get(Coords coords){
        if(game_ranges.inRange(coords))
            return  matrix[coords.getX()][coords.getY()];
        return null;
    }
    void set(Coords coords,Field field){
        if(game_ranges.inRange(coords))
            matrix[coords.getX()][coords.getY()]=field;
    }
}