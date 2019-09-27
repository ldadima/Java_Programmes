package ru.nsu.ccfit.linevich.minesweeper.graph;

import ru.nsu.ccfit.linevich.minesweeper.controller.Game;
import ru.nsu.ccfit.linevich.minesweeper.data.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class GraphSweeper extends JFrame {

    private Game game;

    private JPanel panel;
    private JLabel label;
    Ranges game_ranges;
    private final int COLS;
    private final int ROWS;
    private final int BOMBS;
    private static final int IMAGE_SIZE =50;
    public GraphSweeper(int c, int r,int b) {
        COLS=c;
        ROWS=r;
        BOMBS=b;
        game_ranges=new Ranges();
        game_ranges.setSize(new Coords(COLS,ROWS));
        game = new Game(BOMBS, game_ranges);
        game.start();
        setImages();
        initLabel();
        initPanel();
        initFrame();
    }
    private void initLabel(){
        label=new JLabel("Welcome");
        add(label,BorderLayout.SOUTH);
    }
    private void initFrame(){
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Sweeper");
        setResizable(false);
        setVisible(true);
        pack();
        setLocationRelativeTo(null);
    }
    private void initPanel(){
        panel = new JPanel(){
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                for(Coords coords: game_ranges.getAllCoords()) {
                    g.drawImage((Image) game.getField(coords).image,coords.getX()*IMAGE_SIZE, coords.getY()*IMAGE_SIZE, this);
                }
            }
        };

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int x = e.getX()/IMAGE_SIZE;
                int y = e.getY()/IMAGE_SIZE;
                Coords coords=new Coords(x,y);
                if(e.getButton()==MouseEvent.BUTTON1&&game.getState()== GameState.PLAYED)
                    game.pressLeftButton(coords);
                if(e.getButton()==MouseEvent.BUTTON3&&game.getState()==GameState.PLAYED)
                    game.pressRightButton(coords);
                if(e.getButton()==MouseEvent.BUTTON2)
                    game.start();
                label.setText(getMessage());
                panel.repaint();
            }
        });
        panel.setPreferredSize(new Dimension(
                game_ranges.getSize().getX()* IMAGE_SIZE,
                game_ranges.getSize().getY()*IMAGE_SIZE));
        add(panel);
    }

    private String getMessage(){
        switch (game.getState()){
            case PLAYED:return"Try to win";
            case BOOM:return "You LOSE";
            case WON:return "You WIN";
            default:return "";
        }
    }

    private void setImages(){
        for (Field field:Field.values())
            field.image = getImage(field.name().toLowerCase());
    }

    private Image getImage(String name){
        String filename = "../resourses/"+name+".png";
        ImageIcon icon = new ImageIcon(getClass().getResource(filename));
        return icon.getImage();
    }
}
