package com.alixandremz.pacman.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class GameTest {

    @Test
    void mapaTemTodasAsPastilhasAlcancaveis() {
        Game game = new Game();
        List<String> tiles = game.tiles();
        assertEquals(Game.ROWS, tiles.size());

        Game.Entity start = game.pacman();
        boolean[][] seen = new boolean[Game.ROWS][Game.COLS];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{start.r(), start.c()});
        seen[start.r()][start.c()] = true;
        int[][] moves = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] m : moves) {
                int r = cell[0] + m[0];
                int c = cell[1] + m[1];
                if (r < 0 || r >= Game.ROWS || c < 0 || c >= Game.COLS || seen[r][c]) {
                    continue;
                }
                char ch = tiles.get(r).charAt(c);
                if (ch == '#' || ch == '-') {
                    continue;
                }
                seen[r][c] = true;
                queue.add(new int[]{r, c});
            }
        }

        for (int r = 0; r < Game.ROWS; r++) {
            for (int c = 0; c < Game.COLS; c++) {
                char ch = tiles.get(r).charAt(c);
                if (ch == '.' || ch == 'o') {
                    assertTrue(seen[r][c], "Pastilha inalcancavel em " + r + "," + c);
                }
            }
        }
    }

    @Test
    void paredeBloqueiaMovimento() {
        Game game = new Game();
        for (int i = 0; i < Game.READY_TICKS; i++) {
            game.tick();
        }
        Game.Entity before = game.pacman();
        game.setDirection("LEFT");
        game.tick();
        Game.Entity after = game.pacman();
        assertEquals(before.r(), after.r());
        assertEquals(before.c(), after.c());
    }

    @Test
    void comerPastilhaSomaPontos() {
        Game game = new Game();
        for (int i = 0; i < Game.READY_TICKS; i++) {
            game.tick();
        }
        game.setDirection("RIGHT");
        game.tick();
        assertEquals(10, game.score());
        assertEquals(' ', game.tiles().get(game.pacman().r()).charAt(game.pacman().c()));
    }

    @Test
    void ninguemAtravessaParede() {
        Game game = new Game();
        Random random = new Random(42);
        Game.Dir[] dirs = Game.Dir.values();
        for (int i = 0; i < 600; i++) {
            if (i % 20 == 0) {
                game.setDirection(dirs[random.nextInt(dirs.length)].name());
            }
            game.tick();
            if (game.status() == Game.Status.GAME_OVER) {
                game.restart();
            }
            List<String> tiles = game.tiles();
            Game.Entity p = game.pacman();
            char pacTile = tiles.get(p.r()).charAt(p.c());
            assertTrue(pacTile != '#' && pacTile != '-', "Pac-Man dentro de parede em " + p);
            for (Game.GhostView g : game.ghosts()) {
                if (!g.respawning()) {
                    assertTrue(tiles.get(g.r()).charAt(g.c()) != '#', "Fantasma dentro de parede em " + g);
                }
            }
        }
    }
}
