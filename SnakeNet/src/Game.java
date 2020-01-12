import com.google.protobuf.ByteString;
import org.ietf.jgss.GSSContext;

import javax.management.relation.RoleStatus;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static me.ippolitov.fit.snakes.SnakesProto.*;

public class Game extends JFrame {
    private JPanel panelGame;
    private Dimension gameSize = new Dimension(600, 600);
    private JPanel panelScores;
    private JPanel panelButton;
    private JPanel panelCurGames;
    private JPanel panelInfo;
    private MulticastSocket multicastSocket;
    private DatagramSocket datagramSocket;
    private Fields gameFields;
    private int myID = 0;
    private Dimension sizeField = new Dimension(8, 8);
    private final Object gameState = new Object();
    private volatile boolean gameGo = false;
    private volatile int ping = 300;
    private volatile int nodeTimeout = 800;
    private volatile long gameTime = 1000;
    private volatile int typePlay = -1;
    private volatile AtomicLong msgSeq = new AtomicLong(0);
    private final Map<InetSocketAddress, Integer> timeMapPlayers = new HashMap();
    private Map<InetSocketAddress, Integer> addressMap = new HashMap();
    private Sender sender;
    private Receiver receiver;
    private volatile int stateOrder = 0;
    private int myPort;
    private InetAddress multiAddress;
    private InetSocketAddress masterAddress = null;
    private String masterName;


    Game(int Port) {
        super("Snake");
        try {
            myPort = Port;
            multiAddress = InetAddress.getByName("239.192.0.4");
            multicastSocket = new MulticastSocket(9192);
            multicastSocket.setSoTimeout(1);
            datagramSocket = new DatagramSocket(myPort);
            datagramSocket.setSoTimeout(300);
            multicastSocket.joinGroup(multiAddress);
            sender = new Sender(multicastSocket, datagramSocket);
            receiver = new Receiver(multicastSocket, datagramSocket);
            sender.start();
            receiver.start();
        } catch (Exception err) {
        }
        gameFields = new Fields(sizeField.height, sizeField.width, 1, 1, 0.1f, "");
        initPanel();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Snake");
        setResizable(false);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    void start() {
        long time = 0;
        JLabel lose = null;
        while (true) {
            try {
                synchronized (gameState) {
                    gameState.wait();
                    time = System.currentTimeMillis();
                    if (lose != null) {
                        Container parent = lose.getParent();
                        parent.remove(lose);
                        parent.validate();
                        parent.repaint();
                        lose = null;
                    }
                }
            } catch (Exception err) {
            }
            while (true) {
                if (typePlay == 1) {
                    stateOrder++;
                    if (System.currentTimeMillis() - time >= gameTime) {
                        time = System.currentTimeMillis();
                        int err;
                        synchronized (gameState) {
                            err = gameFields.moveSnakes(receiver.steerSnakes);
                        }
                        if (err == 1) {
                            initScores();
                        }
                        if (addressMap.size() + 1 > gameFields.snakes.size()) {
                            for (Map.Entry<InetSocketAddress, Integer> one : addressMap.entrySet()) {
                                if (gameFields.snakes.get(one.getValue()) == null && gameFields.scores.get(one.getValue()).getRole().getNumber() != 3) {
                                    byte[] msg = GameMessage.newBuilder().setMsgSeq(msgSeq.incrementAndGet()).setRoleChange(
                                            GameMessage.RoleChangeMsg.newBuilder().setReceiverRole(NodeRole.VIEWER).build()
                                    ).build().toByteArray();
                                    gameFields.scores.get(one.getValue()).setRole(NodeRole.VIEWER);
                                    try {
                                        datagramSocket.send(new DatagramPacket(msg, msg.length, one.getKey()));
                                    } catch (IOException ignored) {
                                    }
                                }
                            }
                            if (gameFields.snakes.get(myID) == null) {
                                gameFields.scores.get(myID).setRole(NodeRole.VIEWER);
                                byte[] msg = GameMessage.newBuilder().setMsgSeq(msgSeq.incrementAndGet()).setRoleChange(
                                        GameMessage.RoleChangeMsg.newBuilder().setSenderRole(NodeRole.VIEWER).build()
                                ).setSenderId(myID).build().toByteArray();
                                try {
                                    datagramSocket.send(new DatagramPacket(msg, msg.length, sender.deputy));
                                } catch (IOException e) {
                                }
                                typePlay = 3;
                            }
                        }
                        receiver.sendGameState();
                        synchronized (gameState) {
                            repaint();
                        }
                    }
                    if (!gameGo)
                        break;
                }
                if (typePlay != -1) {
                    if (gameFields.snakes.get(myID) == null && lose == null) {
                        lose = new JLabel();
                        lose.setText("YOU LOSE");
                        lose.setFont(new Font("Calibri", Font.BOLD, 50));
                        synchronized (gameState) {
                            repaint();
                        }
                        panelGame.add(lose, BorderLayout.CENTER);
                        panelGame.revalidate();
                    }
                    if (typePlay != 1)
                        time = System.currentTimeMillis();
                    if (!gameGo)
                        break;
                }
            }
        }
    }

    GameState initStateMsg(int order) {
        GameState.Builder state = GameState.newBuilder();
        state.setStateOrder(order);
        gameFields.snakes.forEach((id, snake) -> {
            GameState.Snake.Builder one = GameState.Snake.newBuilder();
            one.setPlayerId(id);
            ArrayList<GameState.Coord> coords = gameFields.initSnake2Msg(snake);
            for (int i = 0; i < coords.size(); i++)
                one.addPoints(coords.get(i));
            one.setHeadDirection(Direction.forNumber(gameFields.directionSnake(id)));
            one.setState(gameFields.snakeStateMap.get(id));
            state.addSnakes(one);
        });
        gameFields.apples.forEach(dimension -> {
            GameState.Coord.Builder one = GameState.Coord.newBuilder();
            one.setX(dimension.width);
            one.setY(dimension.height);
            state.addFoods(one);
        });
        state.setConfig(initGameConfig());
        state.setPlayers(initGamePlayers());
        return state.build();
    }

    GamePlayers initGamePlayers() {
        GamePlayers.Builder pls = GamePlayers.newBuilder();
        for (Map.Entry<Integer, GamePlayer.Builder> player : gameFields.scores.entrySet()) {
            String playAddress;
            int port;
            if (player.getKey() == myID) {
                playAddress = "";
                port = myPort;
            } else {
                InetSocketAddress address = findAddressId(player.getKey());
                if (address!=null) {
                    playAddress = address.getAddress().getHostAddress();
                    port = address.getPort();
                }
                else {
                    playAddress = "";
                    port=0;
                }
            }
            player.getValue().setPort(port).setIpAddress(playAddress);
            pls.addPlayers(player.getValue());
        }
        if (myPort==5553) {
            System.out.println(gameFields.scores.get(myID).getRole().getNumber());
        }
        return pls.build();
    }

    InetSocketAddress findAddressId(int id) {
        InetSocketAddress address = null;
        for (Map.Entry<InetSocketAddress, Integer> player :
                addressMap.entrySet()) {
            if (player.getValue() == id)
                address = player.getKey();
        }
        return address;
    }

    GameConfig initGameConfig() {
        GameConfig.Builder config = GameConfig.newBuilder();
        config.setWidth(gameFields.column);
        config.setHeight(gameFields.str);
        config.setFoodStatic(gameFields.begApple);
        config.setFoodPerPlayer(gameFields.coefApple);
        config.setStateDelayMs((int) gameTime);
        config.setDeadFoodProb(gameFields.foodProb);
        config.setPingDelayMs(ping);
        config.setNodeTimeoutMs(nodeTimeout);
        return config.build();
    }

    private void initPanel() {
        panelButton = new JPanel();
        panelButton.setPreferredSize(new Dimension(700, 50));
        JButton newGame = new JButton("New Game");
        newGame.setFocusable(false);
        JButton quit = new JButton("Quit");
        quit.setFocusable(false);
        newGame.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int foodBeg = gameFields.begApple;
                int foodPlay = gameFields.coefApple;
                float foodVer = gameFields.foodProb;
                String h = JOptionPane.showInputDialog(panelButton, "Write number of strings", "Size Fields", JOptionPane.QUESTION_MESSAGE);
                if (h == null) {
                    return;
                }
                String w = JOptionPane.showInputDialog(panelButton, "Write number of columns", "Size Fields", JOptionPane.QUESTION_MESSAGE);
                if (w == null) {
                    return;
                }
                String foodStatic = JOptionPane.showInputDialog(panelButton, "Write number of food static", "Number Food", JOptionPane.QUESTION_MESSAGE);
                if (foodStatic == null) {
                    return;
                }
                String foodPerPlayer = JOptionPane.showInputDialog(panelButton, "Write number of food per player", "Number Food", JOptionPane.QUESTION_MESSAGE);
                if (foodPerPlayer == null) {
                    return;
                }
                String time = JOptionPane.showInputDialog(panelButton, "Write time of move", "Number Food", JOptionPane.QUESTION_MESSAGE);
                if (time == null) {
                    return;
                }
                String foodProb = JOptionPane.showInputDialog(panelButton, "Write food prob", "Food of Snake", JOptionPane.QUESTION_MESSAGE);
                if (foodProb == null) {
                    return;
                }
                String ping = JOptionPane.showInputDialog(panelButton, "Write ping time", "Number Food", JOptionPane.QUESTION_MESSAGE);
                if (ping == null) {
                    return;
                }
                String node = JOptionPane.showInputDialog(panelButton, "Write time death", "Number Food", JOptionPane.QUESTION_MESSAGE);
                if (node == null) {
                    return;
                }
                String name = JOptionPane.showInputDialog(panelButton, "Write your name", "Name", JOptionPane.QUESTION_MESSAGE);
                if (name == null) {
                    return;
                }
                gameGo = false;
                if (!"".equals(h))
                    sizeField.height = Integer.parseInt(h);
                if (!"".equals(w))
                    sizeField.width = Integer.parseInt(w);
                if (!"".equals(foodStatic))
                    foodBeg = Integer.parseInt(foodStatic);
                if (!"".equals(foodPerPlayer))
                    foodPlay = Integer.parseInt(foodPerPlayer);
                if (!"".equals(foodProb))
                    foodVer = Float.parseFloat(foodProb);
                if (!"".equals(time))
                    gameTime = Integer.parseInt(time);
                if (!"".equals(ping))
                    Game.this.ping = Integer.parseInt(ping);
                if (!"".equals(time))
                    Game.this.nodeTimeout = Integer.parseInt(time);
                if (sizeField.width < 5)
                    sizeField.width = 5;
                if (sizeField.height < 5)
                    sizeField.height = 5;
                gameFields = new Fields(sizeField.height, sizeField.width, foodBeg, foodPlay, foodVer, name);
                myID = 1;
                receiver.steerSnakes.put(myID, -1);
                initScores();
                typePlay = 1;
                initInfo();
                synchronized (gameState) {
                    repaint();
                    gameState.notifyAll();
                }
                gameGo = true;
            }
        });
        quit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sender.sendRole(3);
                typePlay = 3;
            }
        });
        panelButton.add(newGame);
        panelButton.add(quit);
        panelGame = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                synchronized (gameState) {
                    if(sizeField.width!=gameFields.column || sizeField.height!=gameFields.str){
                        sizeField.width = gameFields.column;
                        sizeField.height = gameFields.str;
                    }
                    Dimension IMGSize = new Dimension(gameSize.width / sizeField.width - 1, gameSize.height / sizeField.height - 1);
                    for (Dimension one : gameFields.freeFields)
                        g.drawImage(initImage(0), one.width * IMGSize.width, one.height * IMGSize.height, IMGSize.width, IMGSize.height, this);
                    for (Dimension one : gameFields.apples)
                        g.drawImage(initImage(-1), one.width * IMGSize.width, one.height * IMGSize.height, IMGSize.width, IMGSize.height, this);
                    for (Map.Entry<Integer, ArrayList<Dimension>> oneSnake : gameFields.snakes.entrySet()) {
                        ArrayList<Dimension> snake = oneSnake.getValue();
                        for (int i = 0; i < snake.size(); i++) {
                            Dimension one = snake.get(i);
                            int cur = (myID == oneSnake.getKey()) ? 1 : 2;
                            int IMGid = (i == 0) ? 100 + cur : cur;
                            g.drawImage(initImage(IMGid), one.width * IMGSize.width, one.height * IMGSize.height, IMGSize.width, IMGSize.height, this);
                        }
                    }
                }
            }
        };
        panelGame.setPreferredSize(gameSize);
        panelGame.setFocusable(true);
        panelGame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case 65: {
                        if (typePlay == 1) {
                            receiver.steerSnakes.put(myID, 3);
                        }
                        if (typePlay == 0 || typePlay == 2) {
                            byte[] msg = GameMessage.newBuilder().setSteer(GameMessage.SteerMsg.newBuilder().setDirection(Direction.LEFT)).setMsgSeq(msgSeq.incrementAndGet()).build().toByteArray();
                            try {
                                datagramSocket.send(new DatagramPacket(msg, msg.length, masterAddress));
                                sender.lastSend.put(masterAddress, 0);
                            } catch (IOException ignored) {
                            }
                        }
                        break;
                    }
                    case 87: {
                        if (typePlay == 1) {
                            receiver.steerSnakes.put(myID, 1);
                        }
                        if (typePlay == 0 || typePlay == 2) {
                            byte[] msg = GameMessage.newBuilder().setSteer(GameMessage.SteerMsg.newBuilder().setDirection(Direction.UP)).setMsgSeq(msgSeq.incrementAndGet()).build().toByteArray();
                            try {
                                datagramSocket.send(new DatagramPacket(msg, msg.length, masterAddress));
                                sender.lastSend.put(masterAddress, 0);
                            } catch (IOException ignored) {
                            }
                        }
                        break;
                    }
                    case 68: {
                        if (typePlay == 1) {
                            receiver.steerSnakes.put(myID, 4);
                        }
                        if (typePlay == 0 || typePlay == 2) {
                            byte[] msg = GameMessage.newBuilder().setSteer(GameMessage.SteerMsg.newBuilder().setDirection(Direction.RIGHT)).setMsgSeq(msgSeq.incrementAndGet()).build().toByteArray();
                            try {
                                datagramSocket.send(new DatagramPacket(msg, msg.length, masterAddress));
                                sender.lastSend.put(masterAddress, 0);
                            } catch (IOException ignored) {
                            }
                        }
                        break;
                    }
                    case 83: {
                        if (typePlay == 1) {
                            receiver.steerSnakes.put(myID, 2);
                        }
                        if (typePlay == 0 || typePlay == 2) {
                            byte[] msg = GameMessage.newBuilder().setSteer(GameMessage.SteerMsg.newBuilder().setDirection(Direction.DOWN)).setMsgSeq(msgSeq.incrementAndGet()).build().toByteArray();
                            try {
                                datagramSocket.send(new DatagramPacket(msg, msg.length, masterAddress));
                                sender.lastSend.put(masterAddress, 0);
                            } catch (IOException ignored) {
                            }
                        }
                        break;
                    }
                }
            }
        });
        panelScores = new JPanel();
        panelScores.setPreferredSize(new Dimension(100, 600));
        GridBagLayout layout = new GridBagLayout();
        setLayout(layout);
        GridBagConstraints panelConstraint = new GridBagConstraints();
        panelConstraint.weightx = 3;
        panelConstraint.weighty = 2;
        initGridConstraints(panelConstraint, 0, 0, 2, 1);
        getContentPane().add(panelButton, panelConstraint);
        initGridConstraints(panelConstraint, 0, 1, 1, 1);
        getContentPane().add(panelScores, panelConstraint);
        initGridConstraints(panelConstraint, 1, 1, 1, 1);
        getContentPane().add(panelGame, panelConstraint);
        initGridConstraints(panelConstraint, 2, 0, 1, 1);
        panelInfo = new JPanel();
        panelInfo.setPreferredSize(new Dimension(300, 150));
        getContentPane().add(panelInfo, panelConstraint);
        initGridConstraints(panelConstraint, 2, 1, 1, 1);
        panelCurGames = new JPanel();
        panelCurGames.setLayout(new GridBagLayout());
        panelCurGames.setPreferredSize(new Dimension(300, 600));
        getContentPane().add(panelCurGames, panelConstraint);

    }

    void initInfo() {
        panelInfo.removeAll();
        panelInfo.add(new JLabel("Ведущий:" + ((typePlay == 1) ? gameFields.master : masterName)));
        panelInfo.add(new JLabel("Размер:" + gameFields.column + "x" + gameFields.str));
        panelInfo.add(new JLabel("Еда:" + gameFields.begApple + "+" + gameFields.coefApple + "x"));
    }

    void initCurGames() {
        panelCurGames.removeAll();
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.weightx = 5;
        constraints.weighty = receiver.curGames.size();
        int index = 0;
        for (Map.Entry<InetSocketAddress, GameMessage.AnnouncementMsg> game :
                receiver.curGames.entrySet()) {
            if(myPort==5550)
                System.out.println(game.getKey());
            String master = null;
            for (GamePlayer player : game.getValue().getPlayers().getPlayersList())
                if (player.getRole().getNumber() == 1) {
                    master = player.getName();
                    break;
                }
            JLabel masters = new JLabel(master + "[" + game.getKey().getHostString() + "]");
            JLabel plays = new JLabel(Integer.toString(game.getValue().getPlayers().getPlayersCount()));
            JLabel size = new JLabel(game.getValue().getConfig().getWidth() + "x" + game.getValue().getConfig().getHeight());
            JLabel food = new JLabel(game.getValue().getConfig().getFoodStatic() + "+" + game.getValue().getConfig().getFoodPerPlayer() + "x");
            JButton enter = new JButton("Enter");
            enter.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String name = JOptionPane.showInputDialog(panelCurGames, "Введите Имя", "Имя", JOptionPane.QUESTION_MESSAGE);
                    if (name == null || "".equals(name))
                        return;
                    gameGo = false;
                    byte[] msg = GameMessage.newBuilder().setJoin(GameMessage.JoinMsg.newBuilder().setName(name).build()).setMsgSeq(msgSeq.incrementAndGet()).build().toByteArray();
                    try {
                        datagramSocket.send(new DatagramPacket(msg, msg.length, game.getKey()));
                        masterAddress = game.getKey();
                        sender.lastSend.put(masterAddress, 0);
                    } catch (IOException ex) {
                    }
                }
            });
            initGridConstraints(constraints, 0, index, 1, 1);
            panelCurGames.add(masters, constraints);
            initGridConstraints(constraints, 1, index, 1, 1);
            panelCurGames.add(plays, constraints);
            initGridConstraints(constraints, 2, index, 1, 1);
            panelCurGames.add(size, constraints);
            initGridConstraints(constraints, 3, index, 1, 1);
            panelCurGames.add(food, constraints);
            initGridConstraints(constraints, 4, index, 1, 1);
            panelCurGames.add(enter, constraints);
            index++;
        }
        panelCurGames.revalidate();
    }

    void initScores() {
        panelScores.removeAll();
        GridBagConstraints constraints = new GridBagConstraints();
        Label scores = new Label("Рейтинг");
        int number = 0;
        GridBagLayout scoreLayout = new GridBagLayout();
        panelScores.setLayout(scoreLayout);
        constraints.weightx = 0;
        constraints.weighty = 0;
        initGridConstraints(constraints, 0, 0, 1, 1);
        panelScores.add(scores, constraints);
        for (Map.Entry<Integer, GamePlayer.Builder> score :
                gameFields.scores.entrySet()) {
            number++;
            initGridConstraints(constraints, 0, number, 1, 1);
            scores = new Label(number + ". " + score.getValue().getName() + " : " + score.getValue().getScore());
            if (score.getKey() == myID) {
                scores.setText(scores.getText() + " /ce i");
            }
            panelScores.add(scores, constraints);
        }
        panelScores.revalidate();
    }

    void initGridConstraints(GridBagConstraints constraints, int gx, int gy, int gw, int gh) {
        constraints.gridx = gx;
        constraints.gridy = gy;
        constraints.gridwidth = gw;
        constraints.gridheight = gh;
    }


    private Image initImage(int id) {
        String file = "D:/My/Network_Java_P/SnakeNet/fields/";
        switch (id) {
            case 0:
                return new ImageIcon(file + "field.png").getImage();
            case -1:
                return new ImageIcon(file + "apple.png").getImage();
            default:
                return new ImageIcon(file + id + ".png").getImage();
        }
    }

    class Sender extends Thread {
        MulticastSocket multicastSocket;
        DatagramSocket datagramSocket;
        final List<Map.Entry<InetSocketAddress, Map.Entry<GameMessage, Integer>>> queueMsg = new ArrayList<>();
        InetSocketAddress deputy = null;
        Map<InetSocketAddress, Integer> lastSend = new HashMap<>();

        Sender(MulticastSocket mult, DatagramSocket data) {
            multicastSocket = mult;
            datagramSocket = data;
        }

        void updateTime(int time) {
            synchronized (queueMsg) {
                for (Map.Entry<InetSocketAddress, Map.Entry<GameMessage, Integer>> one : queueMsg) {
                    one.getValue().setValue(one.getValue().getValue() + time);
                }
            }
            synchronized (lastSend) {
                for (Map.Entry<InetSocketAddress, Integer> one : lastSend.entrySet()) {
                    one.setValue(one.getValue() + time);
                }
            }
            synchronized (timeMapPlayers) {
                if (typePlay != 1) {
                    for (Map.Entry<InetSocketAddress, Integer> one : timeMapPlayers.entrySet())
                        one.setValue(one.getValue() + time);
                }
                if (typePlay == 1) {
                    for (Iterator<Map.Entry<InetSocketAddress, Integer>> iterator = timeMapPlayers.entrySet().iterator(); iterator.hasNext(); ) {
                        Map.Entry<InetSocketAddress, Integer> one = iterator.next();
                        one.setValue(one.getValue() + time);
                        if (one.getValue() > nodeTimeout) {
                            if (typePlay == 1 && deputy != null && deputy.equals(one.getKey()))
                                changeDeputy();
                            synchronized (queueMsg) {
                                queueMsg.removeIf((key) -> key.getKey().equals(one.getKey()));
                            }
                            gameFields.snakeStateMap.put(addressMap.get(one.getKey()), GameState.Snake.SnakeState.ZOMBIE);
                            addressMap.remove(one.getKey());
                            timeMapPlayers.remove(one.getKey());
                        }
                    }
                }
                if (masterAddress != null && typePlay == 2 && timeMapPlayers.size() > 0 && timeMapPlayers.get(masterAddress) > nodeTimeout) {
                    timeMapPlayers.remove(masterAddress);
                    changeMaster();
                }
            }
        }

        void sendRole(int role) {
            byte[] msg = GameMessage.newBuilder()
                    .setRoleChange(GameMessage.RoleChangeMsg.newBuilder()
                            .setSenderRole(NodeRole.forNumber(role)).build())
                    .setMsgSeq(msgSeq.incrementAndGet()).setSenderId(myID).build().toByteArray();
            if (typePlay == 1)
                for (InetSocketAddress address : addressMap.keySet()) {
                    try {
                        datagramSocket.send(new DatagramPacket(msg, msg.length, address));
                        sender.lastSend.put(address, 0);
                    } catch (IOException ignored) {
                    }
                }
            else
                try {
                    datagramSocket.send(new DatagramPacket(msg, msg.length, masterAddress));
                    sender.lastSend.put(masterAddress, 0);
                } catch (IOException ignored) {
                }
        }

        void changeMaster() {
            if (typePlay == 2) {
                gameFields.master = gameFields.scores.get(myID).getName();
                gameFields.scores.get(myID).setRole(NodeRole.MASTER);
                for (Integer hm : gameFields.snakes.keySet()) {
                    receiver.steerSnakes.put(hm, -1);
                }
                gameGo = true;
                typePlay = 1;
                for (Map.Entry<Integer, GamePlayer.Builder> player : gameFields.scores.entrySet()) {
                    if (player.getValue().getRole().getNumber() == 1)
                        player.getValue().setRole(NodeRole.VIEWER);
                    InetSocketAddress address = new InetSocketAddress(player.getValue().getIpAddress(), player.getValue().getPort());
                    try {
                        if (!masterAddress.equals(address) && !address.equals(new InetSocketAddress(InetAddress.getLocalHost(), myPort))) {
                            addressMap.put(address, player.getValue().getId());
                        }
                    } catch (Exception ignored) {
                    }
                    synchronized (timeMapPlayers) {
                        timeMapPlayers.put(address, 0);
                    }
                    byte[] msg = GameMessage.newBuilder().setMsgSeq(msgSeq.incrementAndGet()).setRoleChange
                            (GameMessage.RoleChangeMsg.newBuilder().setSenderRole(NodeRole.MASTER).build())
                            .setSenderId(myID)
                            .build().toByteArray();
                    try {
                        datagramSocket.send(new DatagramPacket(msg, msg.length, address));
                        sender.lastSend.put(address, 0);
                    } catch (IOException ignored) {
                    }
                }
                masterAddress = null;
                changeDeputy();
            }
            gameFields.scores.get(myID).setRole(NodeRole.MASTER);
        }

        void changeDeputy() {
            for (GamePlayer.Builder player : gameFields.scores.values()) {
                if (player.getRole().getNumber() == 0) {
                    deputy = new InetSocketAddress(player.getIpAddress(), player.getPort());
                    player.setRole(NodeRole.DEPUTY);
                    byte[] msg = GameMessage.newBuilder().setMsgSeq(msgSeq.incrementAndGet()).setRoleChange
                            (GameMessage.RoleChangeMsg.newBuilder().setReceiverRole(NodeRole.DEPUTY).build())
                            .build().toByteArray();
                    try {
                        datagramSocket.send(new DatagramPacket(msg, msg.length, deputy));
                        //queueMsg.add(Map.Entry< Map.Entry<>>)
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        @Override
        public void run() {
            try {
                long time = System.currentTimeMillis();
                long annTime = System.currentTimeMillis();
                while (true) {
                    for (Map.Entry<InetSocketAddress, Integer> one : lastSend.entrySet()) {
                        if (one.getValue() > ping) {
                            byte[] msg = GameMessage.newBuilder().setMsgSeq(msgSeq.incrementAndGet())
                                    .setPing(GameMessage.PingMsg.newBuilder().build()).build().toByteArray();
                            datagramSocket.send(new DatagramPacket(msg, msg.length, one.getKey()));
                            one.setValue(0);
                        }
                    }
                    if (typePlay == 1 && System.currentTimeMillis() - annTime > 1000) {
                        byte[] msg = GameMessage.newBuilder().setMsgSeq(msgSeq.incrementAndGet())
                                .setAnnouncement(GameMessage.AnnouncementMsg.newBuilder()
                                        .setConfig(initGameConfig())
                                        .setPlayers(initGamePlayers())).build().toByteArray();
                        multicastSocket.send(new DatagramPacket(msg, msg.length, multiAddress, 9192));
                        annTime = System.currentTimeMillis();
                    }
                    synchronized (queueMsg) {
                        for (Map.Entry<InetSocketAddress, Map.Entry<GameMessage, Integer>> one : queueMsg) {
                            if (one.getValue().getValue() > ping) {
                                byte[] msg = one.getValue().getKey().toByteArray();
                                datagramSocket.send(new DatagramPacket(msg, msg.length, one.getKey()));
                                sender.lastSend.put(one.getKey(), 0);
                            }
                        }
                    }
                    updateTime((int) (System.currentTimeMillis() - time));
                    time = System.currentTimeMillis();
                }
            } catch (Exception err) {
            }
        }
    }

    class Receiver extends Thread {
        MulticastSocket multicastSocket;
        DatagramSocket datagramSocket;
        Map<InetSocketAddress, GameMessage.AnnouncementMsg> curGames = new HashMap<>();
        Map<InetSocketAddress, Integer> timeGames = new HashMap<>();
        Map<Integer, Integer> steerSnakes = new HashMap<>();

        Receiver(MulticastSocket multicast, DatagramSocket data) {
            multicastSocket = multicast;
            datagramSocket = data;
        }


        void sendAckMsg(InetSocketAddress address, long seq) {
            byte[] msg = GameMessage.newBuilder()
                    .setAck(GameMessage.AckMsg.newBuilder().build()).setMsgSeq(seq).build().toByteArray();
            try {
                datagramSocket.send(new DatagramPacket(msg, msg.length, address));
                sender.lastSend.put(address, 0);
            } catch (Exception err) {
            }
        }

        void sendGameState() {
            byte[] msg = GameMessage.newBuilder().setMsgSeq(msgSeq
                    .incrementAndGet()).setState(GameMessage.StateMsg.newBuilder()
                    .setState(initStateMsg(stateOrder)).build()).build().toByteArray();
            for (InetSocketAddress address : addressMap.keySet()) {
                try {
                    datagramSocket.send(new DatagramPacket(msg, msg.length, address));
                    sender.lastSend.put(address, 0);
                } catch (IOException e) {
                }
            }
        }

        @Override
        public void run() {
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(new byte[4096], 4096);
                InetSocketAddress address;
                GameMessage gameMessage;
                try {
                    datagramSocket.receive(receivePacket);
                    address = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
                    gameMessage = GameMessage.parseFrom(ByteString.copyFrom(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength()));
                    synchronized (timeMapPlayers) {
                        timeMapPlayers.put(address, 0);
                    }
                    switch (gameMessage.getTypeCase()) {
                        case ACK: {
                            synchronized (sender.queueMsg) {
                                for (Iterator<Map.Entry<InetSocketAddress, Map.Entry<GameMessage, Integer>>> iterator = sender.queueMsg.iterator(); iterator.hasNext(); ) {
                                    Map.Entry<InetSocketAddress, Map.Entry<GameMessage, Integer>> one = iterator.next();
                                    if (address.equals(one.getKey()) && one.getValue().getKey().getMsgSeq() == gameMessage.getMsgSeq())
                                        iterator.remove();
                                }
                            }
                            break;
                        }
                        case JOIN: {
                            sendAckMsg(address, gameMessage.getMsgSeq());
                            int id;
                            if ((id = gameFields.setSnake(gameMessage.getJoin().getName())) != 0) {
                                synchronized (gameState) {
                                    byte[] msg = GameMessage.newBuilder()
                                            .setRoleChange(GameMessage.RoleChangeMsg.newBuilder()
                                                    .setSenderRole(NodeRole.MASTER)
                                                    .setReceiverRole((sender.deputy == null) ? NodeRole.DEPUTY : NodeRole.NORMAL).build())
                                            .setReceiverId(id).setMsgSeq(msgSeq.incrementAndGet()).build().toByteArray();
                                    datagramSocket.send(new DatagramPacket(msg, msg.length, address));
                                    sender.lastSend.put(address, 0);
                                    steerSnakes.put(id, -1);
                                    if (sender.deputy == null) {
                                        sender.deputy = address;
                                        gameFields.scores.get(id).setRole(NodeRole.DEPUTY);
                                    }
                                    gameFields.scores.get(id).setIpAddress(address.getAddress().getHostAddress());
                                    gameFields.scores.get(id).setPort(address.getPort());
                                    addressMap.put(address, id);
                                    timeMapPlayers.put(address, 0);
                                    synchronized (gameState) {
                                        sendGameState();
                                    }
                                    initScores();
                                    repaint();
                                }
                            } else {
                                byte[] msg = GameMessage.newBuilder()
                                        .setError(GameMessage.ErrorMsg.newBuilder()
                                                .setErrorMessage("Нет места на поле").build()).setMsgSeq(msgSeq.incrementAndGet()).build().toByteArray();
                                datagramSocket.send(new DatagramPacket(msg, msg.length, address));
                                sender.lastSend.put(address, 0);
                            }
                            break;
                        }
                        case ERROR: {
                            sendAckMsg(address, gameMessage.getMsgSeq());
                            JOptionPane.showConfirmDialog(panelCurGames, gameMessage.getError().getErrorMessage(), "Error", JOptionPane.DEFAULT_OPTION);
                            break;
                        }
                        case STATE: {
                            sendAckMsg(address, gameMessage.getMsgSeq());
                            if (masterName == null) {
                                for (GamePlayer player : gameMessage.getState().getState().getPlayers().getPlayersList())
                                    if (player.getRole().getNumber() == 1)
                                        masterName = player.getName();
                                initInfo();
                            }
                            gameFields = new Fields(gameMessage.getState().getState(), masterName);
                            synchronized (gameState) {
                                gameGo = true;
                                gameState.notifyAll();
                            }
                            initScores();
                            synchronized (gameState) {
                                repaint();
                            }
                            break;
                        }
                        case PING: {
                            sendAckMsg(address, gameMessage.getMsgSeq());
                            break;
                        }
                        case STEER: {
                            sendAckMsg(address, gameMessage.getMsgSeq());
                            int id = addressMap.get(address);
                            int direction = gameFields.directionSnake(id);
                            int steer = gameMessage.getSteer().getDirection().getNumber();
                            if (direction * steer == 2 || direction * steer == 12) //UP - 1 DOWN - 2 Left - 3 Right - 4 (1*2=2 ; 3*4=12)
                                break;
                            steerSnakes.put(id, steer);
                            break;
                        }
                        case ROLE_CHANGE: {
                            sendAckMsg(address, gameMessage.getMsgSeq());
                            if (!address.equals(masterAddress) && gameMessage.getRoleChange().getSenderRole() == NodeRole.MASTER) {
                                masterName = gameFields.scores.get(gameMessage.getSenderId()).getName();
                                masterAddress = address;
                                initInfo();
                                break;
                            }
                            NodeRole senderRole = gameMessage.getRoleChange().getSenderRole();
                            if (typePlay == 2 && senderRole.getNumber() == 3) {
                                sender.changeMaster();
                                addressMap.put(address, gameMessage.getSenderId());
                            }

                            if (typePlay == 1) {
                                int id = gameMessage.getSenderId();
                                gameFields.scores.get(id).setRole(senderRole);
                                if (senderRole.getNumber() == 3) {
                                    gameFields.snakeStateMap.put(id, GameState.Snake.SnakeState.ZOMBIE);
                                }
                            }
                            NodeRole receiverRole = gameMessage.getRoleChange().getReceiverRole();
                            if (typePlay == 0 || typePlay == 2) {
                                typePlay = receiverRole.getNumber();
                            }
                            if (typePlay == -1 || typePlay == 3) {
                                myID = gameMessage.getReceiverId();
                                typePlay = gameMessage.getRoleChange().getReceiverRole().getNumber();
                            }
                            break;
                        }
                        default: {
                        }
                    }
                } catch (Exception err) {

                }
                try {
                    if (typePlay == 3 || typePlay == -1) {
                        multicastSocket.receive(receivePacket);
                        gameMessage = GameMessage.parseFrom(ByteString.copyFrom(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength()));
                        int rPort = 0;
                        for (GamePlayer player : gameMessage.getAnnouncement().getPlayers().getPlayersList())
                            if (player.getRole().getNumber() == 1)
                                rPort = player.getPort();
                        if(myPort==5550)
                            System.out.println(rPort);
                        if (!receivePacket.getAddress().equals(InetAddress.getLocalHost()) || myPort != rPort) {
                            address = new InetSocketAddress(receivePacket.getAddress(), rPort);
                            curGames.put(address, gameMessage.getAnnouncement());
                            timeGames.put(address, 0);
                            initCurGames();
                            synchronized (gameState) {
                                repaint();
                            }
                        }
                    }
                } catch (IOException e) {
                }

            }
        }
    }
}


