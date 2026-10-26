package com.alixandremz.pacman.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Game {

    public static final int TICK_MS = 100;
    public static final int COLS = 28;
    public static final int ROWS = 31;

    static final int READY_TICKS = 15;
    static final int FRIGHT_TICKS = 60;
    static final int RESPAWN_TICKS = 15;

    private static final String[] TEMPLATE = {
            "############################",
            "#............##............#",
            "#.####.#####.##.#####.####.#",
            "#o####.#####.##.#####.####o#",
            "#.####.#####.##.#####.####.#",
            "#..........................#",
            "#.####.##.########.##.####.#",
            "#.####.##.########.##.####.#",
            "#......##....##....##......#",
            "######.#####.##.#####.######",
            "######.#####.##.#####.######",
            "######.##..........##.######",
            "######.##.###--###.##.######",
            "######.##.#      #.##.######",
            "######....#      #....######",
            "######.##.#      #.##.######",
            "######.##.########.##.######",
            "######.##..........##.######",
            "######.##.########.##.######",
            "######.##.########.##.######",
            "#............##............#",
            "#.####.#####.##.#####.####.#",
            "#.####.#####.##.#####.####.#",
            "#o..##.......##.......##..o#",
            "###.##.##.########.##.##.###",
            "###.##.##.########.##.##.###",
            "#......##....##....##......#",
            "#.##########.##.##########.#",
            "#.##########.##.##########.#",
            "#..........................#",
            "############################"
    };

    private static final int PAC_START_R = 23;
    private static final int PAC_START_C = 6;
    private static final int[][] GHOST_STARTS = {{13, 12}, {13, 13}, {13, 14}, {13, 15}};
    private static final int[][] CORNERS = {{1, 26}, {1, 1}, {29, 26}, {29, 1}};
    private static final int DOOR_R = 12;
    private static final int DOOR_C = 13;
    private static final int HOUSE_TOP = 12;
    private static final int HOUSE_BOTTOM = 16;
    private static final int HOUSE_LEFT = 10;
    private static final int HOUSE_RIGHT = 17;

    public enum Dir {
        UP(-1, 0),
        DOWN(1, 0),
        LEFT(0, -1),
        RIGHT(0, 1);

        final int dr;
        final int dc;

        Dir(int dr, int dc) {
            this.dr = dr;
            this.dc = dc;
        }

        Dir opposite() {
            return switch (this) {
                case UP -> DOWN;
                case DOWN -> UP;
                case LEFT -> RIGHT;
                case RIGHT -> LEFT;
            };
        }
    }

    public enum Status {
        READY, PLAYING, WON, GAME_OVER
    }

    public record Entity(int r, int c, Dir dir) {
    }

    public record GhostView(int r, int c, Dir dir, boolean frightened, boolean respawning) {
    }

    private final char[][] walls = new char[ROWS][COLS];
    private final boolean[][] pellets = new boolean[ROWS][COLS];
    private final boolean[][] power = new boolean[ROWS][COLS];
    private final Random random = new Random();
    private final Ghost[] ghosts = new Ghost[4];

    private int pelletsLeft;
    private int pacR;
    private int pacC;
    private Dir pacDir;
    private Dir nextDir;
    private int score;
    private int lives;
    private int frightened;
    private int readyTicks;
    private int ticks;
    private Status status;

    public Game() {
        restart();
    }

    public void restart() {
        loadMaze();
        score = 0;
        lives = 3;
        readyTicks = READY_TICKS;
        ticks = 0;
        status = Status.READY;
        resetPositions();
    }

    private void loadMaze() {
        if (TEMPLATE.length != ROWS) {
            throw new IllegalStateException("Mapa precisa ter " + ROWS + " linhas");
        }
        pelletsLeft = 0;
        for (int r = 0; r < ROWS; r++) {
            String row = TEMPLATE[r];
            if (row.length() != COLS) {
                throw new IllegalStateException("Linha " + r + " precisa ter " + COLS + " colunas");
            }
            for (int c = 0; c < COLS; c++) {
                char ch = row.charAt(c);
                if (r == PAC_START_R && c == PAC_START_C) {
                    pellets[r][c] = false;
                    power[r][c] = false;
                    walls[r][c] = ' ';
                    continue;
                }
                pellets[r][c] = ch == '.';
                power[r][c] = ch == 'o';
                if (pellets[r][c] || power[r][c]) {
                    pelletsLeft++;
                }
                walls[r][c] = (ch == '#' || ch == '-') ? ch : ' ';
            }
        }
    }

    private void resetPositions() {
        pacR = PAC_START_R;
        pacC = PAC_START_C;
        pacDir = Dir.LEFT;
        nextDir = null;
        frightened = 0;
        for (int i = 0; i < ghosts.length; i++) {
            Ghost g = new Ghost(i);
            g.r = GHOST_STARTS[i][0];
            g.c = GHOST_STARTS[i][1];
            g.dir = i % 2 == 0 ? Dir.UP : Dir.DOWN;
            ghosts[i] = g;
        }
    }

    public void setDirection(String name) {
        try {
            nextDir = Dir.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void tick() {
        if (status == Status.WON || status == Status.GAME_OVER) {
            return;
        }
        ticks++;
        if (status == Status.READY) {
            if (--readyTicks <= 0) {
                status = Status.PLAYING;
            }
            return;
        }
        movePacman();
        if (frightened > 0) {
            frightened--;
        }
        moveGhosts();
        collide();
        if (status == Status.PLAYING && pelletsLeft == 0) {
            status = Status.WON;
        }
    }

    private void movePacman() {
        if (nextDir != null && canPacMove(pacR + nextDir.dr, pacC + nextDir.dc)) {
            pacDir = nextDir;
            nextDir = null;
        }
        if (canPacMove(pacR + pacDir.dr, pacC + pacDir.dc)) {
            pacR += pacDir.dr;
            pacC += pacDir.dc;
            eat();
        }
    }

    private void eat() {
        if (pellets[pacR][pacC]) {
            pellets[pacR][pacC] = false;
            pelletsLeft--;
            score += 10;
        }
        if (power[pacR][pacC]) {
            power[pacR][pacC] = false;
            pelletsLeft--;
            score += 50;
            frightened = FRIGHT_TICKS;
            for (Ghost g : ghosts) {
                if (g.respawn == 0 && canGhostMove(g.r + g.dir.opposite().dr, g.c + g.dir.opposite().dc)) {
                    g.dir = g.dir.opposite();
                }
            }
        }
    }

    private void moveGhosts() {
        for (Ghost g : ghosts) {
            if (g.respawn > 0) {
                g.respawn--;
                if (g.respawn == 0) {
                    g.r = GHOST_STARTS[g.id][0];
                    g.c = GHOST_STARTS[g.id][1];
                    g.dir = Dir.UP;
                }
                continue;
            }
            if ((ticks + g.id) % 5 == 0) {
                continue;
            }
            Dir d = chooseDir(g);
            if (d != null) {
                g.dir = d;
                g.r += d.dr;
                g.c += d.dc;
            }
        }
    }

    private Dir chooseDir(Ghost g) {
        List<Dir> options = new ArrayList<>(4);
        for (Dir d : Dir.values()) {
            if (canGhostMove(g.r + d.dr, g.c + d.dc)) {
                options.add(d);
            }
        }
        if (options.isEmpty()) {
            return null;
        }
        List<Dir> forward = new ArrayList<>(options);
        forward.remove(g.dir.opposite());
        if (forward.isEmpty()) {
            forward = options;
        }
        if (frightened > 0) {
            return forward.get(random.nextInt(forward.size()));
        }
        int[] target = target(g);
        Dir best = forward.get(0);
        int bestDist = Integer.MAX_VALUE;
        for (Dir d : forward) {
            int nr = g.r + d.dr;
            int nc = g.c + d.dc;
            int dist = (nr - target[0]) * (nr - target[0]) + (nc - target[1]) * (nc - target[1]);
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }

    private int[] target(Ghost g) {
        if (g.r >= HOUSE_TOP && g.r <= HOUSE_BOTTOM && g.c >= HOUSE_LEFT && g.c <= HOUSE_RIGHT) {
            return new int[]{DOOR_R, DOOR_C};
        }
        return switch (g.id) {
            case 0 -> new int[]{pacR, pacC};
            case 1 -> {
                Dir d = pacDir;
                yield new int[]{pacR + 4 * d.dr, pacC + 4 * d.dc};
            }
            case 2 -> {
                Dir d = pacDir;
                Ghost blinky = ghosts[0];
                int ar = pacR + 2 * d.dr;
                int ac = pacC + 2 * d.dc;
                yield new int[]{2 * ar - blinky.r, 2 * ac - blinky.c};
            }
            default -> Math.abs(g.r - pacR) + Math.abs(g.c - pacC) > 8
                    ? new int[]{pacR, pacC}
                    : CORNERS[g.id];
        };
    }

    private void collide() {
        for (Ghost g : ghosts) {
            if (g.respawn > 0 || g.r != pacR || g.c != pacC) {
                continue;
            }
            if (frightened > 0) {
                score += 200;
                g.respawn = RESPAWN_TICKS;
            } else {
                lives--;
                if (lives <= 0) {
                    status = Status.GAME_OVER;
                } else {
                    status = Status.READY;
                    readyTicks = READY_TICKS;
                    resetPositions();
                }
                return;
            }
        }
    }

    private boolean canPacMove(int r, int c) {
        return r >= 0 && r < ROWS && c >= 0 && c < COLS
                && walls[r][c] != '#' && walls[r][c] != '-';
    }

    private boolean canGhostMove(int r, int c) {
        return r >= 0 && r < ROWS && c >= 0 && c < COLS && walls[r][c] != '#';
    }

    public Status status() {
        return status;
    }

    public int score() {
        return score;
    }

    public int lives() {
        return lives;
    }

    public int frightened() {
        return frightened;
    }

    public Entity pacman() {
        return new Entity(pacR, pacC, pacDir);
    }

    public List<GhostView> ghosts() {
        List<GhostView> out = new ArrayList<>(ghosts.length);
        for (Ghost g : ghosts) {
            out.add(new GhostView(g.r, g.c, g.dir, frightened > 0, g.respawn > 0));
        }
        return out;
    }

    public List<String> tiles() {
        List<String> out = new ArrayList<>(ROWS);
        for (int r = 0; r < ROWS; r++) {
            StringBuilder sb = new StringBuilder(COLS);
            for (int c = 0; c < COLS; c++) {
                if (walls[r][c] == '#') {
                    sb.append('#');
                } else if (walls[r][c] == '-') {
                    sb.append('-');
                } else if (power[r][c]) {
                    sb.append('o');
                } else if (pellets[r][c]) {
                    sb.append('.');
                } else {
                    sb.append(' ');
                }
            }
            out.add(sb.toString());
        }
        return out;
    }

    private static final class Ghost {
        final int id;
        int r;
        int c;
        int respawn;
        Dir dir;

        Ghost(int id) {
            this.id = id;
        }
    }
}
