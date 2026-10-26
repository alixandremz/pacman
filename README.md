# Pac-Man

Pac-Man jogável no navegador com a lógica do jogo em **Java + Spring Boot**. O servidor roda o
game loop, os fantasmas e a pontuação; o navegador só desenha o estado recebido e envia as
direções.

## Stack

- Java 21
- Spring Boot 4.1.1 (MVC + `SseEmitter`)
- Canvas 2D puro no front-end, sem bibliotecas e sem build

## Como rodar

```bash
./mvnw spring-boot:run
```

Abra http://localhost:8080.

Ou gere o jar:

```bash
./mvnw package
java -jar target/pacman-0.0.1-SNAPSHOT.jar
```

## Controles

- Setas ou `WASD`: mover
- `R`: reiniciar
- 3 vidas, 1 mapa, 4 fantasmas com modos de perseguição diferentes
- Pastilha grande deixa os fantasmas assustados por alguns segundos

## Como funciona

| Endpoint | Uso |
| --- | --- |
| `GET /stream` | SSE com o estado do jogo a 10 Hz (labirinto, pastilhas, jogador, fantasmas) |
| `POST /api/dir?dir=UP` | envia a direção desejada |
| `POST /api/restart` | reinicia a partida |

Cada sessão HTTP tem sua própria partida, guardada em memória. O `Game`
(`src/main/java/com/alixandremz/pacman/game/Game.java`) move tudo em passos de grade a 10 Hz.
O front-end interpola as posições entre os pacotes para o movimento ficar suave.

## O que aprendi com este projeto

- **Game loop no servidor**: o Java roda o estado a 10 Hz com `@Scheduled`; o navegador só desenha o
  que recebe. Aprendi na prática a diferença entre estado autoritativo e renderização.
- **SSE sem WebSocket**: usei `SseEmitter` para o servidor empurrar o estado e `POST` para os
  comandos. Deu menos dependência que WebSocket e foi suficiente para um jogo de ritmo fixo —
  também entendi quando SSE não bastaria (comunicação bidirecional de baixa latência).
- **Separar regra de jogo de transporte**: `Game` é Java puro, sem Spring, e por isso dá para testar
  sem subir servidor. O controller cuida de sessão, JSON e SSE.
- **Sessão HTTP como identidade**: cada `JSESSIONID` ganhou uma partida própria em memória, sem login
  nem banco de dados.
- **Interpolação no cliente**: 10 pacotes por segundo parece travado; interpolar as posições a 60 fps
  com `requestAnimationFrame` suavizou o movimento sem aumentar a taxa do servidor.
- **IA por alvo**: cada fantasma persegue algo diferente (posição atual, 4 células à frente, vetor
  com o Blinky, canto). Foi a parte que mais exigiu pensar em casos de borda.
- **Testar lógica de jogo**: BFS para provar que toda pastilha é alcançável e simulação de 600 ticks
  para garantir que ninguém atravessa parede — testes que pegam erro que olhar a tela não pega.
- **Spring Boot 4 + Java 21**: os nomes dos starters mudaram (`spring-boot-starter-webmvc`) e o
  `mvnw` evita depender de Maven instalado na máquina.
- **Trade-offs conscientes**: JSON montado à mão e estado em memória mantêm o projeto pequeno; sei o
  que trocaria se precisasse de ranking persistente ou de várias instâncias.

## Estrutura

```text
src/main/java/com/alixandremz/pacman/
├── PacmanApplication.java
├── game/Game.java          # mapa, movimento, IA dos fantasmas, colisões, pontuação
└── web/GameController.java # SSE, comandos e serialização do estado
src/main/resources/static/  # index.html, style.css, game.js
src/test/java/              # testes de mapa, colisão e movimento
```

## Testes

```bash
./mvnw test
```

Os testes verificam que todas as pastilhas são alcançáveis, que paredes bloqueiam o jogador,
que comer pastilha pontua e que ninguém atravessa parede em 600 ticks simulados.

## Limitações / próximos passos

- Estado em memória: reiniciar o servidor zera as partidas e não há ranking persistente.
- Um mapa fixo e sem som.
- Sem multiplayer online e sem controles de toque.
- Fantasmas usam uma IA clássica simplificada (sem os modos de dispersão alternados).
- Para ficar online de graça, o GitHub Pages não serve (é estático); use Render, Railway ou Fly.io
  ou rode localmente.
