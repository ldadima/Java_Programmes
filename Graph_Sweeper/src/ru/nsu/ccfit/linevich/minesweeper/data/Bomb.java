package ru.nsu.ccfit.linevich.minesweeper.data;

public class Bomb {
   private Matrix bombMap;
   private int totalBombs;
   private Ranges game_ranges;

   public Bomb(Ranges gr,int totalBombs){
       game_ranges=gr;
       this.totalBombs=totalBombs;
       fixBombsCounts();
   }

   public void start(){
       bombMap = new Matrix(Field.ZERO,game_ranges);
       for(int i=0;i<totalBombs;i++)
           placeBomb();
   }

   public Field get(Coords coords){
       return bombMap.get(coords);
   }

   public int getTotalBombs(){
       return totalBombs;
   }

   private void fixBombsCounts(){
       int maxBombs = game_ranges.getSize().getX()*game_ranges.getSize().getY()/2;
       if(totalBombs>maxBombs)
           totalBombs=maxBombs;
   }

   private void placeBomb(){
       Coords coords=game_ranges.getRandomCoords();
       while( bombMap.get(coords)==Field.BOMB)coords=game_ranges.getRandomCoords();
       bombMap.set(coords,Field.BOMB);
       incNumberAroundBomb(coords);
   }

   private void incNumberAroundBomb(Coords coords){
       for(Coords around:game_ranges.getCoordsAround(coords)) {
           if (bombMap.get(around) != Field.BOMB)
               bombMap.set(around, bombMap.get(around).getNextNum());
       }
   }


}
