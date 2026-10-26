const canvas = document.getElementById('game');
const ctx = canvas.getContext('2d');
const COLS = 28;
const ROWS = 31;
const CELL = canvas.width / COLS;
const TICK = 100;

const overlay = document.getElementById('overlay');
const message = document.getElementById('message');
const scoreEl = document.getElementById('score');
const livesEl = document.getElementById('lives');
const restartBtn = document.getElementById('restart');

const GHOST_COLORS = ['#ef4444', '#f9a8d4', '#22d3ee', '#fb923c'];
const DIR_ANGLE = {RIGHT: 0, DOWN: Math.PI / 2, LEFT: Math.PI, UP: -Math.PI / 2};

let state = null;
const entities = new Map();

function currentPos(e, now) {
    const t = Math.min(1, (now - e.t0) / TICK);
    return {r: e.fr + (e.tr - e.fr) * t, c: e.fc + (e.tc - e.fc) * t};
}

function moveEntity(key, r, c, now) {
    let e = entities.get(key);
    if (!e) {
        entities.set(key, {fr: r, fc: c, tr: r, tc: c, t0: now});
        return;
    }
    const cur = currentPos(e, now);
    const jump = Math.abs(cur.r - r) + Math.abs(cur.c - c) > 1.5;
    e.fr = jump ? r : cur.r;
    e.fc = jump ? c : cur.c;
    e.tr = r;
    e.tc = c;
    e.t0 = now;
}

function applyState(next) {
    const now = performance.now();
    moveEntity('p', next.pacman.r, next.pacman.c, now);
    next.ghosts.forEach((g, i) => moveEntity('g' + i, g.r, g.c, now));
    state = next;
    scoreEl.textContent = next.score;
    livesEl.textContent = next.lives > 0 ? '♥'.repeat(next.lives) : '—';
    updateOverlay(next);
}

function updateOverlay(s) {
    const labels = {READY: 'PRONTO!', WON: 'VOCÊ VENCEU!', GAME_OVER: 'FIM DE JOGO'};
    const show = s.status !== 'PLAYING';
    overlay.classList.toggle('show', show);
    message.textContent = labels[s.status] || '';
    restartBtn.style.display = s.status === 'WON' || s.status === 'GAME_OVER' ? 'inline-block' : 'none';
}

const events = new EventSource('/stream');
events.onmessage = (event) => applyState(JSON.parse(event.data));
events.onerror = () => {
    overlay.classList.add('show');
    message.textContent = 'RECONECTANDO...';
    restartBtn.style.display = 'none';
};

function sendPath(path) {
    fetch(path, {method: 'POST'}).catch(() => {});
}

const KEY_DIRS = {
    ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT',
    w: 'UP', a: 'LEFT', s: 'DOWN', d: 'RIGHT',
    W: 'UP', A: 'LEFT', S: 'DOWN', D: 'RIGHT'
};

addEventListener('keydown', (event) => {
    if (event.key === 'r' || event.key === 'R') {
        sendPath('/api/restart');
        return;
    }
    const dir = KEY_DIRS[event.key];
    if (dir) {
        event.preventDefault();
        sendPath('/api/dir?dir=' + dir);
    }
});

restartBtn.addEventListener('click', () => sendPath('/api/restart'));

function draw(now) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const tiles = state.tiles;

    for (let r = 0; r < ROWS; r++) {
        for (let c = 0; c < COLS; c++) {
            const ch = tiles[r][c];
            if (ch === '#') {
                ctx.fillStyle = '#1d4ed8';
                ctx.beginPath();
                ctx.roundRect(c * CELL + 1, r * CELL + 1, CELL - 2, CELL - 2, 5);
                ctx.fill();
            } else if (ch === '-') {
                ctx.fillStyle = '#f9a8d4';
                ctx.fillRect(c * CELL + 2, r * CELL + CELL / 2 - 1, CELL - 4, 2);
            } else if (ch === '.') {
                ctx.fillStyle = '#fde68a';
                ctx.beginPath();
                ctx.arc(c * CELL + CELL / 2, r * CELL + CELL / 2, 2.5, 0, Math.PI * 2);
                ctx.fill();
            } else if (ch === 'o') {
                ctx.fillStyle = '#fde68a';
                ctx.beginPath();
                ctx.arc(c * CELL + CELL / 2, r * CELL + CELL / 2, 6, 0, Math.PI * 2);
                ctx.fill();
            }
        }
    }

    const p = currentPos(entities.get('p'), now);
    drawPacman(p, state.pacman.dir, now);

    state.ghosts.forEach((g, i) => {
        if (g.respawning) {
            return;
        }
        drawGhost(currentPos(entities.get('g' + i), now), g, i, now);
    });
}

function drawPacman(pos, dir, now) {
    const cx = pos.c * CELL + CELL / 2;
    const cy = pos.r * CELL + CELL / 2;
    const mouth = Math.abs(Math.sin(now / 120)) * 0.4 + 0.05;
    ctx.save();
    ctx.translate(cx, cy);
    ctx.rotate(DIR_ANGLE[dir] || 0);
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.arc(0, 0, CELL * 0.42, mouth, Math.PI * 2 - mouth);
    ctx.closePath();
    ctx.fillStyle = '#facc15';
    ctx.fill();
    ctx.restore();
}

function drawGhost(pos, ghost, index, now) {
    const cx = pos.c * CELL + CELL / 2;
    const cy = pos.r * CELL + CELL / 2;
    const rad = CELL * 0.4;
    const blinking = ghost.frightened && state.frightened < 20 && Math.floor(now / 200) % 2 === 0;
    const color = ghost.frightened ? (blinking ? '#f8fafc' : '#3b82f6') : GHOST_COLORS[index % 4];

    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(cx, cy - rad * 0.15, rad, Math.PI, 0);
    ctx.lineTo(cx + rad, cy + rad * 0.85);
    ctx.lineTo(cx + rad * 0.66, cy + rad * 0.55);
    ctx.lineTo(cx + rad * 0.33, cy + rad * 0.85);
    ctx.lineTo(cx, cy + rad * 0.55);
    ctx.lineTo(cx - rad * 0.33, cy + rad * 0.85);
    ctx.lineTo(cx - rad * 0.66, cy + rad * 0.55);
    ctx.lineTo(cx - rad, cy + rad * 0.85);
    ctx.closePath();
    ctx.fill();

    const look = {UP: -1, DOWN: 1, LEFT: -1, RIGHT: 1};
    const dx = (ghost.dir === 'LEFT' || ghost.dir === 'RIGHT') ? look[ghost.dir] * rad * 0.14 : 0;
    const dy = (ghost.dir === 'UP' || ghost.dir === 'DOWN') ? look[ghost.dir] * rad * 0.14 : 0;
    for (const side of [-1, 1]) {
        const ex = cx + side * rad * 0.36;
        const ey = cy - rad * 0.12;
        ctx.fillStyle = '#f8fafc';
        ctx.beginPath();
        ctx.arc(ex, ey, rad * 0.28, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = '#1e293b';
        ctx.beginPath();
        ctx.arc(ex + dx, ey + dy, rad * 0.14, 0, Math.PI * 2);
        ctx.fill();
    }
}

function loop(now) {
    if (state) {
        draw(now);
    }
    requestAnimationFrame(loop);
}

requestAnimationFrame(loop);
