import me.ippolitov.fit.snakes.SnakesProto;
import java.awt.*;
import java.util.*;

public class Fields {
    ArrayList<Dimension> freeFields = new ArrayList<>();
    ArrayList<Dimension> apples = new ArrayList<>();
    Map<Integer, ArrayList<Dimension>> snakes = new HashMap<>();
    final Map<Integer, SnakesProto.GamePlayer.Builder> scores = new HashMap<>();
    Map<Integer, SnakesProto.GameState.Snake.SnakeState> snakeStateMap = new HashMap<>();
    float foodProb;
    int begApple;
    int coefApple;
    int str;
    int column;
    String master;



    Fields(int h, int w, int b, int c,float fp, String name) {
        str = h;
        column = w;
        for (int i = 0; i < str; i++) {
            for (int j = 0; j < column; j++)
                freeFields.add(new Dimension(j, i));
        }
        begApple = b;
        coefApple = c;
        foodProb=fp;
        master = name;
        setSnake(name);
        scores.get(1).setRole(SnakesProto.NodeRole.MASTER);
    }

    ArrayList<SnakesProto.GameState.Coord> initSnake2Msg(ArrayList<Dimension> snake) {
        ArrayList<SnakesProto.GameState.Coord> coords = new ArrayList<>();
        Dimension prev = snake.get(0);
        SnakesProto.GameState.Coord.Builder coord = SnakesProto.GameState.Coord.newBuilder().setX(prev.width).setY(prev.height);
        coords.add(coord.build());
        int x = 0;
        int y = 0;
        for (int i = 1; i < snake.size(); i++) {
            int w = snake.get(i).width;
            int h = snake.get(i).height;
            if (w == prev.width && x==0) {
                if ((h > prev.height && !(prev.height == 0 && h == str - 1)) || (h == 0 && prev.height == str - 1))
                    y++;
                else
                    y--;
            }
            if (h == prev.height&&y==0) {
                if ((w > prev.width && !(prev.width == 0 && w == column - 1)) || (w == 0 && prev.width == column - 1))
                    x++;
                else
                    x--;
            }
            if ((w!=prev.width&&y!=0)||(h!=prev.height&&x!=0)) {
                coord = SnakesProto.GameState.Coord.newBuilder().setX(x).setY(y);
                coords.add(coord.build());
                prev = snake.get(--i);
                x = 0;
                y = 0;
            }
            else {
                prev=snake.get(i);
            }
        }
        coord = SnakesProto.GameState.Coord.newBuilder().setX(x).setY(y);
        coords.add(coord.build());
        return coords;
    }

    ArrayList<Dimension> initMsg2Snake(java.util.List<SnakesProto.GameState.Coord> coords) {
        Dimension one = new Dimension(coords.get(0).getX(), coords.get(0).getY());
        ArrayList<Dimension> snake = new ArrayList<>();
        snake.add(new Dimension(one.width, one.height));
        for (int i = 1; i < coords.size(); i++) {
            int dx = coords.get(i).getX();
            int dy = coords.get(i).getY();
            if (dx >= 0)
                for (int j = 0; j < dx; j++)
                    snake.add(new Dimension((one.width + j + 1)%column, one.height));
            else
                for (int j = 0; j > dx; j--){
                    int del = one.width + j - 1;
                    snake.add(new Dimension((del<0)?column+del:del, one.height));
                }
            if (dy >= 0)
                for (int j = 0; j < dy; j++)
                    snake.add(new Dimension(one.width, (one.height + j + 1)%str));
            else
                for (int j = 0; j > dy; j--) {
                    int del = one.height + j - 1;
                    snake.add(new Dimension(one.width, (del<0)?str+del:del));
                }
            one = snake.get(snake.size() - 1);
        }
        return snake;
    }

    Fields(SnakesProto.GameState state, String masterName) {
        master = masterName;
        state.getPlayers().getPlayersList().forEach(player -> scores.put(player.getId(), player.toBuilder()));
        state.getSnakesList().forEach(e -> snakeStateMap.put(e.getPlayerId(),e.getState()));
        str = state.getConfig().getHeight();
        column = state.getConfig().getWidth();
        begApple = state.getConfig().getFoodStatic();
        coefApple = (int) state.getConfig().getFoodPerPlayer();
        foodProb = state.getConfig().getDeadFoodProb();
        state.getFoodsList().forEach(coordish -> apples.add(new Dimension(coordish.getX(), coordish.getY())));
        state.getSnakesList().forEach(snake ->
            snakes.put(snake.getPlayerId(), initMsg2Snake(snake.getPointsList())));
        for (int i = 0; i < str; i++)
            for (int j = 0; j < column; j++) {
                Dimension dim = new Dimension(i,j);
                boolean hasSnakeDim=false;
                for(ArrayList<Dimension>one: snakes.values())
                    if(one.contains(dim))
                        hasSnakeDim=true;
                if (!hasSnakeDim && !apples.contains(dim)) {
                    freeFields.add(dim);
                }
            }
        for(SnakesProto.GamePlayer player:state.getPlayers().getPlayersList())
            if(player.getRole().getNumber()==1){
                master=player.getName();
                break;
            }
    }

    int setSnake(String name) {
        Dimension head = checkFields();
        if (head == null)
            return 0;
        int id = scores.size()+1;
        snakes.put(id, new ArrayList<>());
        freeFields.remove(head);
        snakes.get(id).add(head);
        Dimension tail;
        switch (new Random().nextInt(4)) {
            case 1: {
                tail = new Dimension(head.width - 1, head.height);
                break;
            }
            case 2: {
                tail = new Dimension(head.width + 1, head.height);
                break;
            }
            case 3: {
                tail = new Dimension(head.width, head.height - 1);
                break;
            }
            default: {
                tail = new Dimension(head.width, head.height + 1);
            }
        }
        freeFields.remove(tail);
        snakes.get(id).add(tail);
        scores.put(id, SnakesProto.GamePlayer.newBuilder().setId(id).setName(name).setScore(2).setRole(SnakesProto.NodeRole.NORMAL));
        snakeStateMap.put(id, SnakesProto.GameState.Snake.SnakeState.ALIVE);
        setApples();
        return id;
    }

    Dimension checkFields() {
        if (freeFields.size() + apples.size() < 25)
            return null;
        for (int i = 2; i < column - 2; i++)
            for (int j = 2; j < column - 2; j++)
                if (checkSqr(i, j))
                    return new Dimension(i, j);
        return null;
    }

    boolean checkSqr(int w, int h) {
        for (int i = w - 2; i <= w + 2; i++)
            for (int j = h - 2; j <= h + 2; j++)
                if (!freeFields.contains(new Dimension(i, j)) && !apples.contains(new Dimension(i, j)))
                    return false;
        return true;
    }

    void setApples() {
        for(int i = apples.size(); i < begApple + coefApple * snakes.size(); i++) {
            if (freeFields.size() > 0) {
                int index = new Random().nextInt(freeFields.size());
                apples.add(freeFields.get(index));
                freeFields.remove(index);
            }
        }
    }

    int directionSnake(int id){
        Dimension head = snakes.get(id).get(0);
        Dimension prev = snakes.get(id).get(1);
        return directionSnake(head,prev);
    }

    int directionSnake(Dimension head, Dimension prev) {
        if (head.width == prev.width) {
            if ((head.height < prev.height && !(head.height == 0 && prev.height == (str - 1)))
                    || (head.height == (str - 1) && prev.height == 0)) {
                return 1;
            } else {
                return 2;
            }
        } else {
            if ((head.width > prev.width && !(head.width == (column - 1) && prev.width == 0))
                    || (head.width == 0 && prev.width == (column - 1))) {
                return 4;
            } else {
                return 3;
            }
        }
    }

    int moveSnakes(Map<Integer,Integer> move) {
        int ret;
        synchronized (scores) {
            ret = 0;
            for (Map.Entry<Integer, ArrayList<Dimension>> player :
                    snakes.entrySet()) {
                int id=player.getKey();
                ArrayList<Dimension> snake = player.getValue();
                int direction;
                Dimension head = snake.get(0);
                Dimension prev = snake.get(1);
                Dimension tail = new Dimension(snake.get(snake.size() - 1));
                freeFields.add(tail);
                direction = directionSnake(head, prev);
                if (!(move.get(id) == -1 || move.get(id) * direction == 2 || move.get(id) * direction == 12))
                    direction = move.get(id);
                switch (direction) {
                    case 1: {
                        upSnake(snake);
                        break;
                    }
                    case 2: {
                        downSnake(snake);
                        break;
                    }
                    case 3: {
                        leftSnake(snake);
                        break;
                    }
                    default: {
                        rightSnake(snake);
                    }
                }
                freeFields.remove(head);
                for (Iterator<Dimension> iterator = apples.iterator(); iterator.hasNext(); ) {
                    Dimension apple = iterator.next();
                    if (head.equals(apple)) {
                        iterator.remove();
                        freeFields.remove(tail);
                        snake.add(tail);
                        scores.put(player.getKey(), scores.get(player.getKey()).setScore(scores.get(player.getKey()).getScore() + 1));
                        setApples();
                        ret = 1;
                        break;
                    }
                }
            }
            checkMovesDelete();
        }

        return ret;
    }

    void checkMovesDelete() {
        ArrayList<Integer> delIds = new ArrayList<>();
        for (Map.Entry<Integer, ArrayList<Dimension>> snakeH: snakes.entrySet()) {
            Dimension head = snakeH.getValue().get(0);
            int h=snakeH.getKey();
            outer: for (Map.Entry<Integer, ArrayList<Dimension>> snakeA: snakes.entrySet()) {
                ArrayList<Dimension> snake = snakeA.getValue();
                if (h == snakeA.getKey()) {
                    int j;
                    for (j = 4; j < snake.size(); j++) {
                        if (head.equals(snake.get(j))) {
                            delIds.add(h);
                            break outer;
                        }
                    }
                } else if (snake.contains(head)) {
                    delIds.add(h);
                    break;
                }
            }
        }
        for (Integer id : delIds
        ) {
            ArrayList<Dimension> snake = snakes.get(id);
            for (Dimension dim : snake) {
                float fp = new Random().nextFloat();
                if (!freeFields.contains(dim)&&fp>foodProb) {
                    freeFields.add(dim);
                }
                if(!apples.contains(dim)&&fp<=foodProb){
                    apples.add(dim);
                }
            }
            snakes.remove(id);
        }
    }

    private void upSnake(ArrayList<Dimension> snake) {
        Dimension prev = null;
        int index = 0;
        for (; index < snake.size(); index++) {
            Dimension current = snake.get(index);
            if (index == 0) {
                int h = current.height - 1;
                if (h < 0)
                    h = str - 1;
                prev = new Dimension(current.width, current.height);
                current.height = h;
            } else {
                swapDim(prev, current);
            }
        }
    }

    private void downSnake(ArrayList<Dimension> snake) {
        Dimension prev = null;
        int index = 0;
        for (; index < snake.size(); index++) {
            Dimension current = snake.get(index);
            if (index == 0) {
                int h = (current.height + 1) % str;
                prev = new Dimension(current.width, current.height);
                current.height = h;
            } else {
                swapDim(prev, current);
            }
        }
    }

    private void leftSnake(ArrayList<Dimension> snake) {
        Dimension prev = null;
        int index = 0;
        for (; index < snake.size(); index++) {
            Dimension current = snake.get(index);
            if (index == 0) {
                int w = current.width - 1;
                if (w < 0)
                    w = str - 1;
                prev = new Dimension(current.width, current.height);
                current.width = w;

            } else {
                swapDim(prev, current);
            }
        }
    }

    private void rightSnake(ArrayList<Dimension> snake) {
        Dimension prev = null;
        int index = 0;
        for (; index < snake.size(); index++) {
            Dimension current = snake.get(index);
            if (index == 0) {
                int w = (current.width + 1) % column;
                prev = new Dimension(current.width, current.height);
                current.width = w;
            } else {
                swapDim(prev, current);
            }
        }
    }

    void swapDim(Dimension a, Dimension b) {
        Dimension swap = new Dimension();
        swap.height = a.height;
        swap.width = a.width;
        a.height = b.height;
        a.width = b.width;
        b.height = swap.height;
        b.width = swap.width;
    }
}
