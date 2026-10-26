package com.alixandremz.pacman.web;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.alixandremz.pacman.game.Game;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
public class GameController {

    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpSession session) {
        SseEmitter emitter = new SseEmitter(0L);
        Client client = new Client(new Game(), emitter);
        Client old = clients.put(session.getId(), client);
        if (old != null) {
            old.emitter().complete();
        }
        Runnable remove = () -> clients.remove(session.getId(), client);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        return emitter;
    }

    @PostMapping("/api/dir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void direction(@RequestParam String dir, HttpSession session) {
        Client client = clients.get(session.getId());
        if (client != null) {
            synchronized (client.game()) {
                client.game().setDirection(dir);
            }
        }
    }

    @PostMapping("/api/restart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restart(HttpSession session) {
        Client client = clients.get(session.getId());
        if (client != null) {
            synchronized (client.game()) {
                client.game().restart();
            }
        }
    }

    @Scheduled(fixedRate = Game.TICK_MS)
    public void tick() {
        clients.forEach((id, client) -> {
            String payload;
            synchronized (client.game()) {
                client.game().tick();
                payload = toJson(client.game());
            }
            try {
                client.emitter().send(SseEmitter.event().data(payload));
            } catch (Exception e) {
                client.emitter().complete();
                clients.remove(id, client);
            }
        });
    }

    private String toJson(Game game) {
        StringBuilder b = new StringBuilder(1600);
        b.append("{\"status\":\"").append(game.status().name()).append("\",");
        b.append("\"score\":").append(game.score()).append(',');
        b.append("\"lives\":").append(game.lives()).append(',');
        b.append("\"frightened\":").append(game.frightened()).append(',');
        Game.Entity p = game.pacman();
        b.append("\"pacman\":{\"r\":").append(p.r()).append(",\"c\":").append(p.c())
                .append(",\"dir\":\"").append(p.dir().name()).append("\"},");
        b.append("\"ghosts\":[");
        List<Game.GhostView> ghosts = game.ghosts();
        for (int i = 0; i < ghosts.size(); i++) {
            Game.GhostView g = ghosts.get(i);
            if (i > 0) {
                b.append(',');
            }
            b.append("{\"r\":").append(g.r()).append(",\"c\":").append(g.c())
                    .append(",\"dir\":\"").append(g.dir().name()).append('"')
                    .append(",\"frightened\":").append(g.frightened())
                    .append(",\"respawning\":").append(g.respawning()).append('}');
        }
        b.append("],\"tiles\":[");
        List<String> tiles = game.tiles();
        for (int i = 0; i < tiles.size(); i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append('"').append(tiles.get(i)).append('"');
        }
        b.append("]}");
        return b.toString();
    }

    private record Client(Game game, SseEmitter emitter) {
    }
}
