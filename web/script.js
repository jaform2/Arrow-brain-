(function () {
  "use strict";

  // ==================== CONSTANTS & COLORS ====================
  const COLORS = {
    navyPrimary: "#0E2245",
    electricBlue: "#2563EB",
    skyAccent: "#0EA5E9",
    blueGlow: "#38BDF8",
    pureWhite: "#FFFFFF",
    slateSurface: "#EEF3FB",
    tileStroke: "rgba(217, 227, 242, 0.72)",
    gridDot: "#D5DFEE",
    stoneObstacle: "#64748B",
    stoneBorder: "#475569",
    coral: "#EF4444",
    gold: "#F59E0B",
    emerald: "#10B981",
    purple: "#7C3AED",
    outlineSubtle: "#D9E3F2",
  };

  const DIRECTIONS = {
    UP: { name: "UP", dx: 0, dy: -1 },
    DOWN: { name: "DOWN", dx: 0, dy: 1 },
    LEFT: { name: "LEFT", dx: -1, dy: 0 },
    RIGHT: { name: "RIGHT", dx: 1, dy: 0 },
  };
  const DIR_LIST = [DIRECTIONS.UP, DIRECTIONS.DOWN, DIRECTIONS.LEFT, DIRECTIONS.RIGHT];

  function oppositeDir(dir) {
    if (dir === DIRECTIONS.UP) return DIRECTIONS.DOWN;
    if (dir === DIRECTIONS.DOWN) return DIRECTIONS.UP;
    if (dir === DIRECTIONS.LEFT) return DIRECTIONS.RIGHT;
    return DIRECTIONS.LEFT;
  }

  const DIFFICULTY_TIERS = [
    { key: "EASY", title: "Easy", startLevel: 1, endLevel: 100, accent: "#10B981" },
    { key: "SLIGHTLY_HARDER", title: "Slightly Harder", startLevel: 101, endLevel: 200, accent: "#22C55E" },
    { key: "MEDIUM_HARD", title: "Medium-Hard", startLevel: 201, endLevel: 300, accent: "#0EA5E9" },
    { key: "HARD", title: "Hard", startLevel: 301, endLevel: 400, accent: "#2563EB" },
    { key: "VERY_CHALLENGING", title: "Very Challenging", startLevel: 401, endLevel: 500, accent: "#6366F1" },
    { key: "ADVANCED", title: "Advanced", startLevel: 501, endLevel: 600, accent: "#8B5CF6" },
    { key: "EXPERT", title: "Expert", startLevel: 601, endLevel: 700, accent: "#F59E0B" },
    { key: "EXTREMELY_DIFFICULT", title: "Extremely Difficult", startLevel: 701, endLevel: 800, accent: "#F97316" },
    { key: "MASTER", title: "Master", startLevel: 801, endLevel: 900, accent: "#EC4899" },
    { key: "ALMOST_IMPOSSIBLE", title: "Almost Impossible", startLevel: 901, endLevel: 999, accent: "#EF4444" },
    { key: "ULTIMATE_FINAL", title: "Ultimate Final", startLevel: 1000, endLevel: 1000, accent: "#0E2245" },
  ];
  const ENDLESS_TIER = {
    key: "ENDLESS",
    title: "Master Endless",
    startLevel: 1001,
    endLevel: Number.MAX_SAFE_INTEGER,
    accent: "#0E2245",
  };

  function tierForLevel(level) {
    for (let i = 0; i < DIFFICULTY_TIERS.length; i++) {
      const t = DIFFICULTY_TIERS[i];
      if (level <= t.endLevel) return t;
    }
    return ENDLESS_TIER;
  }

  function posKey(x, y) {
    return x + "," + y;
  }

  function todayDateKey() {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return y + "-" + m + "-" + day;
  }

  // ==================== DETERMINISTIC 64-BIT PRNG ====================
  const MASK_64 = (1n << 64n) - 1n;

  function mixSeed64(z0) {
    let z = (BigInt.asUintN(64, z0) + 0x5E3779B97F4A7C15n) & MASK_64;
    z = ((z ^ (z >> 30n)) * 0x3F58476D1CE4E5B9n) & MASK_64;
    z = ((z ^ (z >> 27n)) * 0x44D049BB133111EBn) & MASK_64;
    return (z ^ (z >> 31n)) & MASK_64;
  }

  function createRng(seedBigInt) {
    // SplitMix64 generator matching deterministic integer/float sampling
    let state = BigInt.asUintN(64, seedBigInt);
    function next64() {
      state = (state + 0x9E3779B97F4A7C15n) & MASK_64;
      let z = state;
      z = ((z ^ (z >> 30n)) * 0xBF58476D1CE4E5B9n) & MASK_64;
      z = ((z ^ (z >> 27n)) * 0x94D049BB133111EBn) & MASK_64;
      return (z ^ (z >> 31n)) & MASK_64;
    }
    return {
      nextInt(bound) {
        if (bound <= 1) return 0;
        const val = next64() % BigInt(bound);
        return Number(val);
      },
      nextDouble() {
        const top53 = Number(next64() >> 11n);
        return top53 / 9007199254740992.0;
      },
      shuffle(arr) {
        for (let i = arr.length - 1; i > 0; i--) {
          const j = this.nextInt(i + 1);
          const tmp = arr[i];
          arr[i] = arr[j];
          arr[j] = tmp;
        }
        return arr;
      },
    };
  }

  // ==================== PUZZLE SOLVER ====================
  const PuzzleSolver = {
    findFirstBlocker(arrow, remainingArrows, obstacleSet, cols, rows) {
      const occupiedByOthers = new Set();
      for (let i = 0; i < remainingArrows.length; i++) {
        const other = remainingArrows[i];
        if (other.id !== arrow.id) {
          for (let c = 0; c < other.cells.length; c++) {
            const cell = other.cells[c];
            occupiedByOthers.add(posKey(cell.x, cell.y));
          }
        }
      }

      const head = arrow.cells[arrow.cells.length - 1];
      let cx = head.x + arrow.direction.dx;
      let cy = head.y + arrow.direction.dy;
      while (cx >= 0 && cx < cols && cy >= 0 && cy < rows) {
        const key = posKey(cx, cy);
        if (obstacleSet.has(key) || occupiedByOthers.has(key)) {
          return { x: cx, y: cy };
        }
        cx += arrow.direction.dx;
        cy += arrow.direction.dy;
      }
      return null;
    },

    getClearArrows(remainingArrows, obstacleSet, cols, rows) {
      if (remainingArrows.length === 0) return [];
      const cellOwner = new Map();
      for (let i = 0; i < remainingArrows.length; i++) {
        const arrow = remainingArrows[i];
        for (let c = 0; c < arrow.cells.length; c++) {
          const cell = arrow.cells[c];
          cellOwner.set(posKey(cell.x, cell.y), arrow.id);
        }
      }

      const clearList = [];
      for (let i = 0; i < remainingArrows.length; i++) {
        const arrow = remainingArrows[i];
        const head = arrow.cells[arrow.cells.length - 1];
        let cx = head.x + arrow.direction.dx;
        let cy = head.y + arrow.direction.dy;
        let blocked = false;
        while (cx >= 0 && cx < cols && cy >= 0 && cy < rows) {
          const key = posKey(cx, cy);
          const owner = cellOwner.get(key);
          if (obstacleSet.has(key) || (owner !== undefined && owner !== arrow.id)) {
            blocked = true;
            break;
          }
          cx += arrow.direction.dx;
          cy += arrow.direction.dy;
        }
        if (!blocked) clearList.push(arrow);
      }
      return clearList;
    },

    findBestHintArrow(remainingArrows, obstacleSet, cols, rows) {
      const clearArrows = this.getClearArrows(remainingArrows, obstacleSet, cols, rows);
      if (clearArrows.length === 0) return null;
      if (clearArrows.length === 1) return clearArrows[0];

      const baseClearCount = clearArrows.length;
      let bestArrow = clearArrows[0];
      let bestGain = -1;

      for (let i = 0; i < clearArrows.length; i++) {
        const candidate = clearArrows[i];
        const nextBoard = remainingArrows.filter((a) => a.id !== candidate.id);
        const nextClearCount = this.getClearArrows(nextBoard, obstacleSet, cols, rows).length;
        const gain = nextClearCount - (baseClearCount - 1);
        if (gain > bestGain) {
          bestGain = gain;
          bestArrow = candidate;
        }
      }
      return bestArrow;
    },

    solveFullOrder(level) {
      let remaining = level.arrows.slice();
      const solution = [];
      while (remaining.length > 0) {
        const clear = this.getClearArrows(remaining, level.obstacleSet, level.cols, level.rows);
        if (clear.length === 0) return null;
        const chosen = clear[0];
        solution.push(chosen.id);
        remaining = remaining.filter((a) => a.id !== chosen.id);
      }
      return solution;
    },
  };

  // ==================== SILHOUETTE LIBRARY (1,000 UNIQUE ARTISTIC SHAPES) ====================
  const SilhouetteLibrary = (function () {
    const shapeNames = [
      "Heart",
      "Butterfly",
      "Star",
      "Blossom",
      "Diamond Gem",
      "Royal Crown",
      "Space Rocket",
      "Wise Owl",
      "Cat Crest",
      "Ocean Fish",
      "Knight Shield",
      "Crescent Moon",
      "Hourglass",
      "Sacred Tree",
      "Golden Key",
      "Sea Anchor",
      "Hex Crystal",
      "Angel Wings",
      "Forest Leaf",
      "Trophy Cup",
      "Sunburst",
      "Castle Tower",
      "Hero Sword",
      "Temple Bell",
      "Lotus",
      "Fox Mask",
      "Spiral Vortex",
      "Four-Leaf Clover",
      "Thunder Crest",
      "Royal Chalice",
      "Snowflake",
      "Phoenix Crest",
    ];

    function biject1000(index0) {
      const norm = ((index0 % 1000) + 1000) % 1000;
      return (norm * 379 + 173) % 1000;
    }

    function gridDimensionsForLevel(level) {
      if (level === 1000) return { cols: 13, rows: 13 };
      if (level <= 50) return { cols: 6, rows: 6 };
      if (level <= 100) return { cols: 7, rows: 6 };
      if (level <= 200) return { cols: 7, rows: 7 };
      if (level <= 300) return { cols: 8, rows: 8 };
      if (level <= 400) return { cols: 9, rows: 8 };
      if (level <= 500) return { cols: 9, rows: 9 };
      if (level <= 600) return { cols: 10, rows: 9 };
      if (level <= 700) return { cols: 10, rows: 10 };
      if (level <= 800) return { cols: 11, rows: 10 };
      if (level <= 900) return { cols: 12, rows: 11 };
      if (level <= 999) return { cols: 13, rows: 12 };
      return { cols: 13, rows: 13 };
    }

    function parseKey(k) {
      const idx = k.indexOf(",");
      return { x: Number(k.slice(0, idx)), y: Number(k.slice(idx + 1)) };
    }

    function fourNeighbors(x, y, cols, rows) {
      const out = [];
      if (x > 0) out.push({ x: x - 1, y });
      if (x < cols - 1) out.push({ x: x + 1, y });
      if (y > 0) out.push({ x, y: y - 1 });
      if (y < rows - 1) out.push({ x, y: y + 1 });
      return out;
    }

    function isFourConnected(cellSet) {
      if (cellSet.size === 0) return false;
      const firstKey = cellSet.values().next().value;
      const visited = new Set([firstKey]);
      const queue = [parseKey(firstKey)];
      let head = 0;
      while (head < queue.length) {
        const cur = queue[head++];
        const nbs = [
          posKey(cur.x - 1, cur.y),
          posKey(cur.x + 1, cur.y),
          posKey(cur.x, cur.y - 1),
          posKey(cur.x, cur.y + 1),
        ];
        for (let i = 0; i < 4; i++) {
          const nk = nbs[i];
          if (cellSet.has(nk) && !visited.has(nk)) {
            visited.add(nk);
            queue.push(parseKey(nk));
          }
        }
      }
      return visited.size === cellSet.size;
    }

    function largestComponent(cellSet) {
      const remaining = new Set(cellSet);
      let best = new Set();
      while (remaining.size > 0) {
        const startKey = remaining.values().next().value;
        remaining.delete(startKey);
        const comp = new Set([startKey]);
        const queue = [parseKey(startKey)];
        let head = 0;
        while (head < queue.length) {
          const cur = queue[head++];
          const nbs = [
            posKey(cur.x - 1, cur.y),
            posKey(cur.x + 1, cur.y),
            posKey(cur.x, cur.y - 1),
            posKey(cur.x, cur.y + 1),
          ];
          for (let i = 0; i < 4; i++) {
            const nk = nbs[i];
            if (remaining.has(nk)) {
              remaining.delete(nk);
              comp.add(nk);
              queue.push(parseKey(nk));
            }
          }
        }
        if (comp.size > best.size) best = comp;
      }
      return best;
    }

    function isInsideShape(shapeIdx, nx, ny, gx, gy) {
      const ax = Math.abs(nx);
      const ay = Math.abs(ny);
      const r = Math.hypot(nx, ny);
      const theta = Math.atan2(ny, nx);

      switch (shapeIdx) {
        case 0: {
          const hy = -ny * 1.05 + 0.16;
          const hx = nx * 1.05;
          const v = hx * hx + hy * hy - 0.86;
          return v * v * v - hx * hx * hy * hy * hy <= 0.1;
        }
        case 1: {
          const upperWing =
            ax >= 0.12 && ny >= -0.95 && ny <= 0.18 && Math.hypot(ax - 0.56, ny + 0.36) <= 0.58;
          const lowerWing =
            ax >= 0.12 && ny >= 0.02 && ny <= 0.96 && Math.hypot(ax - 0.48, ny - 0.48) <= 0.46;
          const body = ax <= 0.2 && ny >= -0.78 && ny <= 0.88;
          return upperWing || lowerWing || body;
        }
        case 2: {
          const angle = theta + Math.PI / 2.0;
          return r <= 0.72 + 0.36 * Math.cos(5.0 * angle);
        }
        case 3:
          return r <= 0.74 + 0.3 * Math.cos(6.0 * theta);
        case 4: {
          const topBevel = ny >= -0.85 && ny <= -0.22 && ax <= 0.68 + (ny + 0.85) * 0.65;
          const bottomCone = ny > -0.22 && ny <= 0.98 && ax <= (0.98 - ny) * 0.9;
          return topBevel || bottomCone;
        }
        case 5: {
          const base = ny >= 0.1 && ny <= 0.86 && ax <= 0.94;
          const leftPeak =
            ny >= -0.88 && ny <= 0.15 && nx <= -0.34 && nx >= -0.96 && ny >= -0.88 + Math.abs(nx + 0.72) * 1.35;
          const midPeak = ny >= -0.98 && ny <= 0.15 && ax <= 0.38 && ny >= -0.98 + ax * 1.65;
          const rightPeak =
            ny >= -0.88 && ny <= 0.15 && nx >= 0.34 && nx <= 0.96 && ny >= -0.88 + Math.abs(nx - 0.72) * 1.35;
          return base || leftPeak || midPeak || rightPeak;
        }
        case 6: {
          const nose = ny <= -0.32 && ax <= (ny + 1.08) * 0.65;
          const fuselage = ny > -0.32 && ny <= 0.6 && ax <= 0.45;
          const fins =
            ny >= 0.16 && ny <= 0.9 && ax >= 0.3 && ax <= 0.94 && ny >= 0.16 + (ax - 0.3) * 0.48;
          const exhaust = ny > 0.6 && ny <= 0.95 && ax <= 0.26;
          return nose || fuselage || fins || exhaust;
        }
        case 7: {
          const ears =
            ny >= -0.98 && ny <= -0.42 && ax >= 0.22 && ax <= 0.85 && ny >= -0.98 + Math.abs(ax - 0.55) * 1.05;
          const headBody =
            ny >= -0.65 && ny <= 0.82 && Math.hypot(nx * 1.04, (ny - 0.08) * 0.92) <= 0.88;
          const feet = ny >= 0.72 && ny <= 0.98 && ax >= 0.15 && ax <= 0.58;
          return ears || headBody || feet;
        }
        case 8: {
          const ears =
            ny >= -0.98 && ny <= -0.24 && ax >= 0.22 && ax <= 0.9 && ny >= -0.98 + Math.abs(ax - 0.62) * 1.25;
          const face = ny >= -0.48 && ny <= 0.88 && Math.hypot(nx, (ny - 0.2) * 1.06) <= 0.9;
          return ears || face;
        }
        case 9: {
          const body =
            nx >= -0.45 && nx <= 0.96 && Math.hypot((nx - 0.22) * 0.88, ny * 1.18) <= 0.82;
          const tail = nx < -0.28 && nx >= -0.98 && ay <= 0.24 + (-0.28 - nx) * 1.12;
          const dorsal = ny >= -0.94 && ny <= -0.38 && nx >= -0.18 && nx <= 0.4;
          return body || tail || dorsal;
        }
        case 10: {
          const top = ny >= -0.94 && ny <= 0.08 && ax <= 0.9 - Math.max(0, -ny - 0.55) * 0.45;
          const bottom = ny > 0.08 && ny <= 0.98 && ax <= (0.98 - ny) * 0.98;
          return top || bottom;
        }
        case 11: {
          const outer = Math.hypot(nx + 0.12, ny) <= 0.96;
          const innerCut = Math.hypot(nx - 0.42, ny) <= 0.62;
          return outer && !innerCut;
        }
        case 12:
          return ay <= 0.95 && ax <= 0.28 + ay * 0.74;
        case 13: {
          const topTier = ny >= -0.98 && ny <= -0.18 && ax <= (ny + 1.0) * 0.78;
          const midTier = ny >= -0.42 && ny <= 0.46 && ax <= (ny + 0.58) * 0.98;
          const trunk = ny >= 0.35 && ny <= 0.98 && ax <= 0.3;
          return topTier || midTier || trunk;
        }
        case 14: {
          const bow = ny >= -0.96 && ny <= -0.05 && Math.hypot(nx, ny + 0.5) <= 0.56;
          const shaft = ny >= -0.15 && ny <= 0.96 && ax <= 0.26;
          const teeth = ((ny >= 0.42 && ny <= 0.66) || (ny >= 0.74 && ny <= 0.96)) && nx >= 0.18 && nx <= 0.68;
          return bow || shaft || teeth;
        }
        case 15: {
          const ring = ny >= -0.96 && ny <= -0.4 && Math.hypot(nx, ny + 0.68) <= 0.32;
          const stem = ny >= -0.55 && ny <= 0.9 && ax <= 0.22;
          const bar = ny >= -0.42 && ny <= -0.14 && ax <= 0.72;
          const d = Math.hypot(nx, ny - 0.15);
          const fluke = ny >= 0.16 && d >= 0.54 && d <= 0.96 && ny <= 0.94;
          return ring || stem || bar || fluke;
        }
        case 16:
          return ax * 0.866 + ay * 0.5 <= 0.96 && ax <= 0.88;
        case 17: {
          const spread = ax >= 0.08 && ay <= 0.76 && Math.hypot(ax - 0.5, ny + 0.06) <= 0.7;
          const notch = ax <= 0.1 && ny > 0.38;
          return (spread || (ax <= 0.26 && ny >= -0.58 && ny <= 0.68)) && !notch;
        }
        case 18: {
          const blade = Math.hypot(nx * 1.2, ny * 0.9) <= 0.92 && ax <= (1.05 - ny) * 0.96;
          const stem = ny >= 0.62 && ax <= 0.2;
          return blade || stem;
        }
        case 19: {
          const bowl = ny >= -0.94 && ny <= 0.2 && ax <= 0.4 + (0.2 - ny) * 0.48;
          const handles = ny >= -0.8 && ny <= -0.15 && ax >= 0.54 && ax <= 0.96;
          const stem = ny >= 0.14 && ny <= 0.7 && ax <= 0.26;
          const base = ny >= 0.64 && ny <= 0.96 && ax <= 0.64;
          return bowl || handles || stem || base;
        }
        case 20:
          return r <= 0.72 + 0.3 * Math.cos(8.0 * theta);
        case 21: {
          const battlements = ny >= -0.96 && ny <= -0.42 && (ax <= 0.25 || (ax >= 0.45 && ax <= 0.94));
          const towerBody = ny >= -0.46 && ny <= 0.94 && ax <= 0.82;
          const gateCut = ny >= 0.5 && ax <= 0.2;
          return (battlements || towerBody) && !gateCut;
        }
        case 22: {
          const blade = ny >= -0.98 && ny <= 0.34 && ax <= Math.min(0.28, (ny + 1.06) * 0.45);
          const guard = ny >= 0.24 && ny <= 0.5 && ax <= 0.86;
          const grip = ny >= 0.44 && ny <= 0.96 && ax <= 0.22;
          return blade || guard || grip;
        }
        case 23: {
          const dome = ny >= -0.84 && ny <= 0.54 && ax <= 0.52 + (ny + 0.84) * 0.32;
          const rim = ny >= 0.4 && ny <= 0.74 && ax <= 0.94;
          const clapper = ny >= 0.7 && ny <= 0.96 && ax <= 0.26;
          const crown = ny >= -0.98 && ny <= -0.76 && ax <= 0.28;
          return dome || rim || clapper || crown;
        }
        case 24: {
          const centerPetal = ax <= 0.38 && ny >= -0.96 && ny <= 0.74;
          const sidePetals =
            ny >= -0.54 && ny <= 0.7 && ax >= 0.22 && ax <= 0.96 && Math.hypot(ax - 0.5, ny - 0.08) <= 0.56;
          const base = ny >= 0.52 && ny <= 0.9 && ax <= 0.76;
          return centerPetal || sidePetals || base;
        }
        case 25: {
          const ears =
            ny >= -0.98 && ny <= -0.24 && ax >= 0.22 && ax <= 0.94 && ny >= -0.98 + Math.abs(ax - 0.62) * 1.22;
          const cheeks = ny >= -0.36 && ny <= 0.4 && ax <= 0.96 - Math.max(0, ny) * 0.52;
          const snout = ny >= 0.34 && ny <= 0.96 && ax <= (1.0 - ny) * 0.96;
          return ears || cheeks || snout;
        }
        case 26: {
          const disk = r <= 0.98;
          const armCut = (gx + gy) % 3 === 0 && r >= 0.38 && r <= 0.72 && nx * ny < 0;
          return disk && !armCut;
        }
        case 27: {
          const leaf = Math.hypot(ax - 0.42, ay - 0.42) <= 0.54;
          const stem = ny >= 0.42 && ny <= 0.98 && ax <= 0.2;
          return leaf || stem;
        }
        case 28: {
          const topBolt = ny >= -0.98 && ny <= 0.1 && nx >= -0.38 - ny * 0.42 && nx <= 0.68 - ny * 0.42;
          const botBolt = ny >= -0.1 && ny <= 0.98 && nx >= -0.58 - ny * 0.32 && nx <= 0.45 - ny * 0.32;
          return topBolt || botBolt;
        }
        case 29: {
          const cup = ny >= -0.94 && ny <= 0.18 && ax <= 0.32 + (0.18 - ny) * 0.62;
          const stem = ny >= 0.12 && ny <= 0.7 && ax <= 0.22;
          const foot = ny >= 0.64 && ny <= 0.96 && ax <= 0.66;
          return cup || stem || foot;
        }
        case 30: {
          const cross = ax <= 0.26 || ay <= 0.26;
          const diag = Math.abs(ax - ay) <= 0.25 && r <= 0.98;
          return (cross || diag) && r <= 1.08;
        }
        default: {
          const wings =
            ax >= 0.12 && ny >= -0.8 && ny <= 0.5 && ax <= 0.98 && Math.abs(ny + ax * 0.28) <= 0.6;
          const spine = ax <= 0.3 && ny >= -0.96 && ny <= 0.96;
          return wings || spine;
        }
      }
    }

    function ensureConnectedAndSubstantial(activeSet, cols, rows) {
      const minRequired = Math.max(12, Math.floor((cols * rows * 48) / 100));
      if (activeSet.size === 0) {
        activeSet.add(posKey(Math.floor(cols / 2), Math.floor(rows / 2)));
      }
      if (!isFourConnected(activeSet)) {
        const largest = largestComponent(activeSet);
        activeSet.clear();
        largest.forEach((k) => activeSet.add(k));
      }

      const cx = (cols - 1) / 2.0;
      const cy = (rows - 1) / 2.0;
      const maxAllowed = cols * rows - 4;
      while (activeSet.size < minRequired && activeSet.size < maxAllowed) {
        const candidates = [];
        activeSet.forEach((k) => {
          const p = parseKey(k);
          const nbs = fourNeighbors(p.x, p.y, cols, rows);
          for (let i = 0; i < nbs.length; i++) {
            const nb = nbs[i];
            const nbKey = posKey(nb.x, nb.y);
            if (!activeSet.has(nbKey)) {
              const isCorner = (nb.x === 0 || nb.x === cols - 1) && (nb.y === 0 || nb.y === rows - 1);
              if (!isCorner) candidates.push(nb);
            }
          }
        });
        if (candidates.length === 0) break;
        let best = candidates[0];
        let bestDist = Math.hypot(best.x - cx, best.y - cy);
        for (let i = 1; i < candidates.length; i++) {
          const d = Math.hypot(candidates[i].x - cx, candidates[i].y - cy);
          if (d < bestDist) {
            bestDist = d;
            best = candidates[i];
          }
        }
        activeSet.add(posKey(best.x, best.y));
        if (activeSet.size < maxAllowed) {
          activeSet.add(posKey(cols - 1 - best.x, best.y));
        }
      }
    }

    function evaluateBaseShape(shapeIdx, cols, rows) {
      const activeSet = new Set();
      const cx = (cols - 1) / 2.0;
      const cy = (rows - 1) / 2.0;
      const rx = Math.max(1.0, (cols - 1) / 2.0);
      const ry = Math.max(1.0, (rows - 1) / 2.0);

      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
          const nx = (x - cx) / rx;
          const ny = (y - cy) / ry;
          if (isInsideShape(shapeIdx, nx, ny, x, y)) {
            activeSet.add(posKey(x, y));
          }
        }
      }
      ensureConnectedAndSubstantial(activeSet, cols, rows);

      const maxAllowed = cols * rows - 4;
      if (activeSet.size > maxAllowed) {
        const corners = [
          posKey(0, 0),
          posKey(cols - 1, 0),
          posKey(0, rows - 1),
          posKey(cols - 1, rows - 1),
        ];
        for (let i = 0; i < corners.length; i++) {
          if (activeSet.size <= maxAllowed) break;
          activeSet.delete(corners[i]);
        }
      }
      return activeSet;
    }

    function applySymmetricSculpt(baseSet, cols, rows, variantCode, levelNumber, salt) {
      const result = new Set(baseSet);
      const combinedKey =
        BigInt(variantCode) * 2654435761n + BigInt(levelNumber) * 97n + BigInt(salt) * 131n;

      const ops = [];
      const midX = Math.floor((cols - 1) / 2);
      for (let y = 0; y < rows; y++) {
        for (let x = 0; x <= midX; x++) {
          const mirrorX = cols - 1 - x;
          const k1 = posKey(x, y);
          const k2 = posKey(mirrorX, y);
          const in1 = baseSet.has(k1);
          const in2 = baseSet.has(k2);
          const nbs = fourNeighbors(x, y, cols, rows);
          if (in1 && in2) {
            if (nbs.some((nb) => !baseSet.has(posKey(nb.x, nb.y)))) {
              ops.push({ k1, k2, isAdd: false });
            }
          } else if (!in1 && !in2) {
            const isCorner = (x === 0 || x === cols - 1) && (y === 0 || y === rows - 1);
            if (!isCorner && nbs.some((nb) => baseSet.has(posKey(nb.x, nb.y)))) {
              ops.push({ k1, k2, isAdd: true });
            }
          }
        }
      }

      if (ops.length === 0) return result;

      const indexed = ops.map((op, i) => ({
        op,
        score: Number((BigInt(i + 1) * 1103515245n + combinedKey) & 0x7fffffffn),
      }));
      indexed.sort((a, b) => a.score - b.score);

      const minSize = Math.max(12, Math.floor((cols * rows * 44) / 100));
      const maxSize = cols * rows - 4;
      const maskBits = (variantCode ^ (salt * 7)) + (salt << 5);
      const toggleBudget = Math.min(indexed.length, 8);

      for (let i = 0; i < toggleBudget; i++) {
        const bitOn = ((maskBits >>> (i % 12)) & 1) !== 0;
        const saltBump = salt > 0 && i === salt % toggleBudget;
        if (!bitOn && !saltBump) continue;

        const op = indexed[i].op;
        if (op.isAdd) {
          if (result.size + 2 <= maxSize) {
            result.add(op.k1);
            result.add(op.k2);
          }
        } else {
          const removeCount = op.k1 === op.k2 ? 1 : 2;
          if (result.size - removeCount >= minSize) {
            result.delete(op.k1);
            result.delete(op.k2);
            if (!isFourConnected(result)) {
              result.add(op.k1);
              result.add(op.k2);
            }
          }
        }
      }

      if (salt >= 32) {
        const asym = [];
        for (let y = 0; y < rows; y++) {
          for (let x = 0; x < cols; x++) {
            const k = posKey(x, y);
            const isCorner = (x === 0 || x === cols - 1) && (y === 0 || y === rows - 1);
            const nbs = fourNeighbors(x, y, cols, rows);
            if (result.has(k)) {
              if (nbs.some((nb) => !result.has(posKey(nb.x, nb.y)))) {
                asym.push({ k, isAdd: false });
              }
            } else if (!isCorner && nbs.some((nb) => result.has(posKey(nb.x, nb.y)))) {
              asym.push({ k, isAdd: true });
            }
          }
        }
        if (asym.length > 0) {
          const pick = asym[(salt * 17 + levelNumber) % asym.length];
          if (pick.isAdd && result.size + 1 <= maxSize) {
            result.add(pick.k);
          } else if (!pick.isAdd && result.size - 1 >= minSize) {
            result.delete(pick.k);
            if (!isFourConnected(result)) result.add(pick.k);
          }
        }
      }

      return result;
    }

    function signatureOf(cols, rows, cellSet) {
      let out = cols + "x" + rows + ":";
      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
          if (cellSet.has(posKey(x, y))) {
            out += x + "," + y + ";";
          }
        }
      }
      return out;
    }

    function toSpec(cols, rows, cellSet, shapeName) {
      const activeCells = [];
      cellSet.forEach((k) => activeCells.push(parseKey(k)));
      return { cols, rows, activeCells, activeCellSet: cellSet, shapeName };
    }

    function buildUltimateLevel1000Silhouette() {
      const cols = 13;
      const rows = 13;
      const cellSet = new Set();
      const cx = 6.0;
      const cy = 6.0;
      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
          const dx = Math.abs(x - cx);
          const dy = y - cy;
          const inWing = dx >= 2.0 && Math.abs(dy + dx * 0.35) <= 2.9 && dx <= 6.0;
          const inCore = Math.hypot(dx, dy) <= 3.8;
          const inCrownTip =
            (y === 0 && (x === 6 || x === 3 || x === 9)) ||
            (y === 1 && (dx <= 1.5 || Math.abs(dx - 3.5) <= 0.6));
          const inTail = y >= 9 && dx <= (12 - y) * 1.25;
          const isJewelCutout =
            (x === 6 && y === 6) ||
            (Math.abs(x - 6) === 2 && y === 5) ||
            (Math.abs(x - 6) === 4 && y === 3);
          if ((inWing || inCore || inCrownTip || inTail) && !isJewelCutout) {
            cellSet.add(posKey(x, y));
          }
        }
      }
      return toSpec(cols, rows, cellSet, "Royal Phoenix");
    }

    let cachedRegistry = null;
    function getRegistry1000() {
      if (cachedRegistry) return cachedRegistry;
      const usedSignatures = new Set();
      const level1000Spec = buildUltimateLevel1000Silhouette();
      usedSignatures.add(
        signatureOf(level1000Spec.cols, level1000Spec.rows, level1000Spec.activeCellSet)
      );

      const list = new Array(1000);
      for (let idx = 0; idx < 1000; idx++) {
        const levelNum = idx + 1;
        if (levelNum === 1000) {
          list[idx] = level1000Spec;
          continue;
        }
        const dims = gridDimensionsForLevel(levelNum);
        const perm = biject1000(idx);
        const shapeIdx = perm % shapeNames.length;
        const variantCode = Math.floor(perm / shapeNames.length);
        const baseSet = evaluateBaseShape(shapeIdx, dims.cols, dims.rows);

        let chosenSet = null;
        for (let salt = 0; salt < 256; salt++) {
          const sculpted = applySymmetricSculpt(
            baseSet,
            dims.cols,
            dims.rows,
            variantCode,
            levelNum,
            salt
          );
          const sig = signatureOf(dims.cols, dims.rows, sculpted);
          if (!usedSignatures.has(sig)) {
            usedSignatures.add(sig);
            chosenSet = sculpted;
            break;
          }
        }

        if (!chosenSet) {
          const fallback = new Set(baseSet);
          const minCells = Math.max(12, Math.floor((dims.cols * dims.rows * 44) / 100));
          outer: for (let y = 0; y < dims.rows; y++) {
            for (let x = 0; x < dims.cols; x++) {
              const k = posKey(x, y);
              if (fallback.has(k) && fallback.size > minCells) {
                const trial = new Set(fallback);
                trial.delete(k);
                if (isFourConnected(trial) && trial.size <= dims.cols * dims.rows - 4) {
                  const sig = signatureOf(dims.cols, dims.rows, trial);
                  if (!usedSignatures.has(sig)) {
                    usedSignatures.add(sig);
                    chosenSet = trial;
                    break outer;
                  }
                }
              }
            }
          }
          if (!chosenSet) chosenSet = fallback;
        }

        list[idx] = toSpec(dims.cols, dims.rows, chosenSet, shapeNames[shapeIdx]);
      }
      cachedRegistry = list;
      return cachedRegistry;
    }

    return {
      biject1000,
      gridDimensionsForLevel,
      buildSilhouetteForLevel(levelNumber, cols, rows) {
        if (levelNumber >= 1 && levelNumber <= 1000) {
          const cached = getRegistry1000()[levelNumber - 1];
          if (cached.cols === cols && cached.rows === rows) return cached;
        }
        const perm = (levelNumber * 379 + 173) & 0x7fffffff;
        const shapeIdx = perm % shapeNames.length;
        const variantCode = Math.floor(perm / shapeNames.length) & 0xff;
        const baseSet = evaluateBaseShape(shapeIdx, cols, rows);
        const sculpted = applySymmetricSculpt(baseSet, cols, rows, variantCode, levelNumber, 0);
        return toSpec(cols, rows, sculpted, shapeNames[shapeIdx]);
      },
      buildDailySilhouette(seedHashBigInt, cols, rows) {
        const idx = Number((seedHashBigInt & 0x7fffffffn) % BigInt(shapeNames.length));
        const variant = Number((seedHashBigInt >> 8n) & 0xffn);
        const levelSalt = Number(seedHashBigInt & 0xffffn);
        const baseSet = evaluateBaseShape(idx, cols, rows);
        const sculpted = applySymmetricSculpt(baseSet, cols, rows, variant, levelSalt, 0);
        return toSpec(cols, rows, sculpted, "Daily " + shapeNames[idx]);
      },
    };
  })();

  // ==================== LEVEL GENERATOR (1,000+ ARTISTIC LEVELS & DAILY) ====================
  const LevelGenerator = {
    paramsForLevel(level) {
      if (level === 1000) {
        return {
          cols: 13,
          rows: 13,
          targetArrows: 52,
          minSegmentLength: 1,
          maxSegmentLength: 5,
          bendProbability: 0.78,
          obstacleCount: 4,
          minBlockingRatio: 0.78,
          maxInitialOpenArrows: 2,
        };
      }

      const dims = SilhouetteLibrary.gridDimensionsForLevel(level);
      const p = Math.max(0, Math.min(999, level - 1)) / 999.0;
      const targetArrows = Math.floor(5 + p * 43);
      let maxSegmentLength = 5;
      if (level <= 40) maxSegmentLength = 2;
      else if (level <= 150) maxSegmentLength = 3;
      else if (level <= 400) maxSegmentLength = 4;

      let obstacleCount = 5;
      if (level <= 100) obstacleCount = 0;
      else if (level <= 250) obstacleCount = 1;
      else if (level <= 450) obstacleCount = 2;
      else if (level <= 700) obstacleCount = 3;
      else if (level <= 900) obstacleCount = 4;

      let maxInitialOpenArrows = 2;
      if (level <= 100) maxInitialOpenArrows = 6;
      else if (level <= 300) maxInitialOpenArrows = 5;
      else if (level <= 600) maxInitialOpenArrows = 4;
      else if (level <= 850) maxInitialOpenArrows = 3;

      return {
        cols: dims.cols,
        rows: dims.rows,
        targetArrows,
        minSegmentLength: 1,
        maxSegmentLength,
        bendProbability: Math.max(0.2, Math.min(0.78, 0.2 + p * 0.58)),
        obstacleCount,
        minBlockingRatio: Math.max(0.25, Math.min(0.78, 0.25 + p * 0.53)),
        maxInitialOpenArrows,
      };
    },

    generateLevel(levelNumber) {
      const safeLevel = Math.max(1, levelNumber);
      const tier = tierForLevel(safeLevel);
      const params = this.paramsForLevel(safeLevel);
      const silhouette = SilhouetteLibrary.buildSilhouetteForLevel(
        safeLevel,
        params.cols,
        params.rows
      );
      const seed = mixSeed64(BigInt(safeLevel) * 0x5E3779B97F4A7C15n);
      return this.buildBestPuzzle(safeLevel, tier, silhouette, params, seed, false, null);
    },

    generateDailyChallenge(dateKey) {
      let hash = 1125899906842597n;
      for (let i = 0; i < dateKey.length; i++) {
        hash = (hash * 31n + BigInt(dateKey.charCodeAt(i))) & MASK_64;
      }
      const seed = mixSeed64(hash ^ 0x517CC1B727220A95n);
      const silhouette = SilhouetteLibrary.buildDailySilhouette(hash, 8, 8);
      const params = {
        cols: 8,
        rows: 8,
        targetArrows: 26,
        minSegmentLength: 1,
        maxSegmentLength: 4,
        bendProbability: 0.6,
        obstacleCount: 2,
        minBlockingRatio: 0.55,
        maxInitialOpenArrows: 4,
      };
      return this.buildBestPuzzle(0, DIFFICULTY_TIERS[3], silhouette, params, seed, true, dateKey);
    },

    buildBestPuzzle(levelNumber, tier, silhouette, params, baseSeed, isDaily, dailyDateKey) {
      let bestCandidate = null;
      let bestScore = -1.0;

      for (let attempt = 0; attempt < 5; attempt++) {
        const rng = createRng(mixSeed64(baseSeed + BigInt(attempt * 7919)));
        const candidate = this.generateCandidate(
          levelNumber,
          tier,
          silhouette,
          params,
          rng,
          isDaily,
          dailyDateKey
        );

        if (!PuzzleSolver.solveFullOrder(candidate)) continue;

        const initialClear = PuzzleSolver.getClearArrows(
          candidate.arrows,
          candidate.obstacleSet,
          candidate.cols,
          candidate.rows
        ).length;
        const totalArrows = Math.max(1, candidate.arrows.length);
        const blockedFraction = (totalArrows - initialClear) / totalArrows;
        const bottleneckBonus = initialClear <= params.maxInitialOpenArrows ? 15.0 : 0.0;
        const score = candidate.arrows.length * 2.0 + blockedFraction * 25.0 + bottleneckBonus;

        if (score > bestScore) {
          bestScore = score;
          bestCandidate = candidate;
        }
        if (
          candidate.arrows.length >= params.targetArrows &&
          blockedFraction >= params.minBlockingRatio &&
          initialClear <= params.maxInitialOpenArrows
        ) {
          return candidate;
        }
      }

      return (
        bestCandidate ||
        this.fallbackLevelOnSilhouette(levelNumber, tier, silhouette, isDaily, dailyDateKey)
      );
    },

    generateCandidate(levelNumber, tier, silhouette, params, rng, isDaily, dailyDateKey) {
      const cols = silhouette.cols;
      const rows = silhouette.rows;
      const activeCells = silhouette.activeCells;
      const activeCellSet = silhouette.activeCellSet;

      const obstacles = [];
      const obstacleSet = new Set();
      if (params.obstacleCount > 0) {
        const interior = activeCells.filter(
          (pos) =>
            pos.x >= 1 &&
            pos.x < cols - 1 &&
            pos.y >= 1 &&
            pos.y < rows - 1 &&
            activeCellSet.has(posKey(pos.x - 1, pos.y)) &&
            activeCellSet.has(posKey(pos.x + 1, pos.y)) &&
            activeCellSet.has(posKey(pos.x, pos.y - 1)) &&
            activeCellSet.has(posKey(pos.x, pos.y + 1))
        );
        rng.shuffle(interior);
        for (let i = 0; i < interior.length; i++) {
          if (obstacles.length >= params.obstacleCount) break;
          const pos = interior[i];
          const hasNeighbor = obstacles.some(
            (o) => Math.abs(o.x - pos.x) + Math.abs(o.y - pos.y) <= 1
          );
          if (!hasNeighbor) {
            obstacles.push(pos);
            obstacleSet.add(posKey(pos.x, pos.y));
          }
        }
      }

      const occupied = new Set(obstacleSet);
      const placedArrows = [];
      const rayPressure = new Map();

      function addRayPressure(arrow) {
        const head = arrow.cells[arrow.cells.length - 1];
        let cx = head.x + arrow.direction.dx;
        let cy = head.y + arrow.direction.dy;
        while (cx >= 0 && cx < cols && cy >= 0 && cy < rows) {
          const k = posKey(cx, cy);
          if (activeCellSet.has(k)) {
            rayPressure.set(k, (rayPressure.get(k) || 0) + 1);
          }
          cx += arrow.direction.dx;
          cy += arrow.direction.dy;
        }
      }

      function isRayClearOfExisting(head, dir, selfSet) {
        let cx = head.x + dir.dx;
        let cy = head.y + dir.dy;
        while (cx >= 0 && cx < cols && cy >= 0 && cy < rows) {
          const k = posKey(cx, cy);
          if (obstacleSet.has(k) || occupied.has(k) || selfSet.has(k)) {
            return false;
          }
          cx += dir.dx;
          cy += dir.dy;
        }
        return true;
      }

      const emptySelf = new Set();
      let failures = 0;
      let nextId = 1;

      while (placedArrows.length < params.targetArrows && failures < 40) {
        const validHeads = [];
        for (let i = 0; i < activeCells.length; i++) {
          const pos = activeCells[i];
          const k = posKey(pos.x, pos.y);
          if (occupied.has(k)) continue;
          const pressure = rayPressure.get(k) || 0;
          for (let d = 0; d < DIR_LIST.length; d++) {
            const dir = DIR_LIST[d];
            if (isRayClearOfExisting(pos, dir, emptySelf)) {
              validHeads.push({ head: pos, dir, pressure });
            }
          }
        }

        if (validHeads.length === 0) break;

        rng.shuffle(validHeads);
        validHeads.sort((a, b) => b.pressure - a.pressure);

        const poolSize = Math.min(validHeads.length, placedArrows.length === 0 ? validHeads.length : 5);
        const pick = validHeads[rng.nextInt(poolSize)];

        const remainingEmpty = Math.max(1, activeCells.length - occupied.size);
        const arrowsNeeded = Math.max(1, params.targetArrows - placedArrows.length);
        const avgBudget = Math.max(1, Math.floor(remainingEmpty / arrowsNeeded));
        const effectiveMaxLen = Math.min(params.maxSegmentLength, avgBudget);
        const desiredLen =
          effectiveMaxLen <= params.minSegmentLength
            ? params.minSegmentLength
            : params.minSegmentLength + rng.nextInt(effectiveMaxLen - params.minSegmentLength + 1);

        const tailToHead = this.growRibbonBackwards(
          pick.head,
          pick.dir,
          desiredLen,
          params.bendProbability,
          cols,
          rows,
          activeCellSet,
          occupied,
          rayPressure,
          rng
        );

        const selfSet = new Set(tailToHead.map((c) => posKey(c.x, c.y)));
        if (!isRayClearOfExisting(pick.head, pick.dir, selfSet)) {
          failures++;
          continue;
        }

        const piece = { id: nextId++, cells: tailToHead, direction: pick.dir };
        placedArrows.push(piece);
        for (let c = 0; c < tailToHead.length; c++) {
          occupied.add(posKey(tailToHead[c].x, tailToHead[c].y));
        }
        addRayPressure(piece);
        failures = 0;
      }

      rng.shuffle(placedArrows);
      const shuffledArrows = placedArrows.map((a, idx) => ({
        id: idx + 1,
        cells: a.cells,
        direction: a.direction,
      }));

      return {
        levelNumber,
        cols,
        rows,
        activeCells,
        activeCellSet,
        shapeName: silhouette.shapeName,
        arrows: shuffledArrows,
        obstacles,
        obstacleSet,
        difficulty: tier,
        isDailyChallenge: isDaily,
        dailyDateKey,
      };
    },

    growRibbonBackwards(
      head,
      headDir,
      desiredLength,
      bendProb,
      cols,
      rows,
      activeCellSet,
      occupied,
      rayPressure,
      rng
    ) {
      const selfExitRay = new Set();
      let rx = head.x + headDir.dx;
      let ry = head.y + headDir.dy;
      while (rx >= 0 && rx < cols && ry >= 0 && ry < rows) {
        selfExitRay.add(posKey(rx, ry));
        rx += headDir.dx;
        ry += headDir.dy;
      }

      const reverseChain = [head];
      const used = new Set([posKey(head.x, head.y)]);
      let lastMoveDir = oppositeDir(headDir);

      for (let step = 1; step < desiredLength; step++) {
        const current = reverseChain[reverseChain.length - 1];
        const candidates = [];
        for (let d = 0; d < DIR_LIST.length; d++) {
          const moveDir = DIR_LIST[d];
          const nx = current.x + moveDir.dx;
          const ny = current.y + moveDir.dy;
          const nk = posKey(nx, ny);
          if (!activeCellSet.has(nk)) continue;
          if (occupied.has(nk) || used.has(nk) || selfExitRay.has(nk)) continue;
          candidates.push({ pos: { x: nx, y: ny }, moveDir });
        }

        if (candidates.length === 0) break;

        const straight = candidates.find((c) => c.moveDir === lastMoveDir);
        let chosen;
        if (straight && rng.nextDouble() >= bendProb) {
          chosen = straight;
        } else {
          const sorted = candidates
            .slice()
            .sort(
              (a, b) =>
                (rayPressure.get(posKey(b.pos.x, b.pos.y)) || 0) -
                (rayPressure.get(posKey(a.pos.x, a.pos.y)) || 0)
            );
          const top = sorted[0];
          if ((rayPressure.get(posKey(top.pos.x, top.pos.y)) || 0) > 0 && rng.nextDouble() < 0.78) {
            chosen = top;
          } else {
            chosen = candidates[rng.nextInt(candidates.length)];
          }
        }

        reverseChain.push(chosen.pos);
        used.add(posKey(chosen.pos.x, chosen.pos.y));
        lastMoveDir = chosen.moveDir;
      }

      return reverseChain.reverse();
    },

    fallbackLevelOnSilhouette(levelNumber, tier, silhouette, isDaily, dailyDateKey) {
      const arrows = silhouette.activeCells.slice(0, 4).map((pos, idx) => ({
        id: idx + 1,
        cells: [pos],
        direction: DIRECTIONS.UP,
      }));
      return {
        levelNumber,
        cols: silhouette.cols,
        rows: silhouette.rows,
        activeCells: silhouette.activeCells,
        activeCellSet: silhouette.activeCellSet,
        shapeName: silhouette.shapeName,
        arrows,
        obstacles: [],
        obstacleSet: new Set(),
        difficulty: tier,
        isDailyChallenge: isDaily,
        dailyDateKey,
      };
    },
  };

  // ==================== WEB AUDIO ENGINE ====================
  const AudioEngine = (function () {
    let ctx = null;
    let musicTimer = null;
    let musicStep = 0;
    let soundEnabled = true;
    let musicEnabled = true;
    let pageVisible = true;

    function ensureContext() {
      if (typeof window === "undefined") return null;
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return null;
      if (!ctx) ctx = new AudioCtx();
      if (ctx.state === "suspended") {
        ctx.resume().catch(() => {});
      }
      return ctx;
    }

    function playChime(freqs, durationSec, volume) {
      if (!soundEnabled) return;
      const c = ensureContext();
      if (!c) return;
      const now = c.currentTime;
      const noteLen = durationSec / freqs.length;

      freqs.forEach((freq, idx) => {
        const start = now + idx * noteLen;
        const osc1 = c.createOscillator();
        const osc2 = c.createOscillator();
        const gain = c.createGain();

        osc1.type = "sine";
        osc1.frequency.setValueAtTime(freq, start);
        osc2.type = "triangle";
        osc2.frequency.setValueAtTime(freq * 2, start);

        const g2 = c.createGain();
        g2.gain.setValueAtTime(0.2, start);
        osc2.connect(g2);
        osc1.connect(gain);
        g2.connect(gain);
        gain.connect(c.destination);

        gain.gain.setValueAtTime(0.0001, start);
        gain.gain.linearRampToValueAtTime(volume, start + 0.008);
        gain.gain.exponentialRampToValueAtTime(0.0001, start + noteLen + 0.08);

        osc1.start(start);
        osc2.start(start);
        osc1.stop(start + noteLen + 0.1);
        osc2.stop(start + noteLen + 0.1);
      });
    }

    function playChordPad(freqs) {
      const c = ensureContext();
      if (!c || c.state !== "running") return;
      const now = c.currentTime;
      freqs.forEach((freq) => {
        const osc = c.createOscillator();
        const gain = c.createGain();
        osc.type = "sine";
        osc.frequency.setValueAtTime(freq, now);
        gain.gain.setValueAtTime(0.0001, now);
        gain.gain.linearRampToValueAtTime(0.018, now + 0.7);
        gain.gain.linearRampToValueAtTime(0.0001, now + 1.95);
        osc.connect(gain);
        gain.connect(c.destination);
        osc.start(now);
        osc.stop(now + 2.0);
      });
    }

    const CHORDS = [
      [261.63, 329.63, 392.0, 493.88],
      [220.0, 261.63, 329.63, 392.0],
      [174.61, 261.63, 329.63, 440.0],
      [196.0, 293.66, 329.63, 392.0],
    ];

    function updateMusicLoop() {
      if (musicEnabled && pageVisible && ctx && ctx.state === "running") {
        if (!musicTimer) {
          playChordPad(CHORDS[musicStep % CHORDS.length]);
          musicStep++;
          musicTimer = setInterval(() => {
            if (musicEnabled && pageVisible) {
              playChordPad(CHORDS[musicStep % CHORDS.length]);
              musicStep++;
            }
          }, 2000);
        }
      } else if (musicTimer) {
        clearInterval(musicTimer);
        musicTimer = null;
      }
    }

    return {
      unlock() {
        ensureContext();
        updateMusicLoop();
      },
      setPrefs(sound, music) {
        soundEnabled = sound;
        musicEnabled = music;
        updateMusicLoop();
      },
      setVisibility(visible) {
        pageVisible = visible;
        updateMusicLoop();
      },
      playClick() {
        playChime([660.0], 0.04, 0.14);
      },
      playTap() {
        playChime([587.33], 0.05, 0.16);
      },
      playRemove() {
        playChime([523.25, 783.99], 0.14, 0.22);
      },
      playError() {
        playChime([185.0, 146.83], 0.16, 0.22);
      },
      playHint() {
        playChime([783.99, 1046.5, 1318.51], 0.24, 0.2);
      },
      playComplete() {
        playChime([523.25, 659.25, 783.99, 1046.5], 0.45, 0.25);
      },
      playGameOver() {
        playChime([392.0, 349.23, 293.66], 0.36, 0.22);
      },
    };
  })();

  // ==================== PERSISTENT SAVE STORAGE ====================
  const STORAGE_KEY = "arrow_brain_save_v1";

  function defaultSaveState() {
    return {
      highestUnlockedLevel: 1,
      currentLevel: 1,
      hintsRemaining: 3,
      livesRemaining: 3,
      soundEnabled: true,
      musicEnabled: true,
      livesSystemEnabled: true,
      completedLevels: {}, // { "1": { stars: 3, bestMoves: 4 } }
      dailyChallenges: {}, // { "2026-11-20": { stars: 3, moves: 12 } }
      savedBoards: {}, // { "level_3": { remainingIds: [..], movesUsed, mistakesUsed } }
    };
  }

  function loadSaveState() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return defaultSaveState();
      const parsed = JSON.parse(raw);
      return Object.assign(defaultSaveState(), parsed);
    } catch (_) {
      return defaultSaveState();
    }
  }

  function persistSaveState(state) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (_) {}
  }

  // ==================== UI & GAME STATE ====================
  let saveState = loadSaveState();
  let activeScreen = "menu";
  let selectedTierKey = tierForLevel(saveState.currentLevel).key;
  let snackbarTimer = null;
  let rewardProgressTimer = null;
  let activeRewardReason = null;
  let confettiAnimId = null;

  const boardRuntime = {
    levelNumber: 1,
    isDailyChallenge: false,
    dailyDateKey: null,
    puzzle: null,
    remainingArrows: [],
    flyingArrows: [],
    movesUsed: 0,
    mistakesUsed: 0,
    hintedArrowId: null,
    blockedArrowId: null,
    blockedTargetCell: null,
    blockedStartedAt: 0,
    isLevelComplete: false,
    isGameOver: false,
  };

  // ==================== DOM REFERENCES ====================
  const $ = (id) => (typeof document !== "undefined" ? document.getElementById(id) : null);

  let screens = {};

  // ==================== ICON HELPERS ====================
  const SVG_SOUND_ON =
    '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg>';
  const SVG_SOUND_OFF =
    '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/></svg>';
  const SVG_MUSIC_ON =
    '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/></svg>';
  const SVG_MUSIC_OFF =
    '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M4.27 3L3 4.27l9 9v.28c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4v-1.73L19.73 21 21 19.73 4.27 3zM14 7h4V3h-6v5.18l2 2z"/></svg>';
  const SVG_HEART =
    '<svg class="heart-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>';
  const SVG_HEART_LOST =
    '<svg class="heart-icon lost" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>';
  const SVG_STAR_FILLED =
    '<svg viewBox="0 0 24 24" fill="#F59E0B" aria-hidden="true"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>';
  const SVG_STAR_EMPTY =
    '<svg viewBox="0 0 24 24" fill="#CBD5E1" aria-hidden="true"><path d="M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z"/></svg>';

  // ==================== NAVIGATION ====================
  function showScreen(name) {
    activeScreen = name;
    Object.keys(screens).forEach((k) => {
      screens[k].classList.toggle("active", k === name);
    });
    if (name === "menu") renderMenu();
    if (name === "levels") renderLevelSelect();
    if (name === "settings") renderSettings();
    if (name === "game") {
      resizeBoardCanvas();
      renderGameHud();
    }
  }

  function showSnackbar(message) {
    const el = $("snackbar");
    el.textContent = message;
    el.classList.remove("hidden");
    clearTimeout(snackbarTimer);
    snackbarTimer = setTimeout(() => {
      el.classList.add("hidden");
    }, 2600);
  }

  // ==================== RENDER MENU ====================
  function totalStarsCount() {
    let sum = 0;
    Object.values(saveState.completedLevels).forEach((v) => {
      sum += v.stars || 0;
    });
    Object.values(saveState.dailyChallenges).forEach((v) => {
      sum += v.stars || 0;
    });
    return sum;
  }

  function renderMenu() {
    AudioEngine.setPrefs(saveState.soundEnabled, saveState.musicEnabled);

    const btnSound = $("btn-menu-sound");
    btnSound.innerHTML = saveState.soundEnabled ? SVG_SOUND_ON : SVG_SOUND_OFF;
    btnSound.classList.toggle("muted", !saveState.soundEnabled);

    const btnMusic = $("btn-menu-music");
    btnMusic.innerHTML = saveState.musicEnabled ? SVG_MUSIC_ON : SVG_MUSIC_OFF;
    btnMusic.classList.toggle("muted", !saveState.musicEnabled);

    const completedCount = Object.keys(saveState.completedLevels).length;
    const curLevel = saveState.currentLevel;
    const tier = tierForLevel(curLevel);

    $("stat-current-level").textContent = "Lv. " + curLevel;
    $("stat-completed").textContent = String(completedCount);
    $("stat-stars").textContent = "★ " + totalStarsCount();
    $("stat-hints").textContent = String(saveState.hintsRemaining);

    $("continue-title").textContent =
      completedCount === 0 && curLevel === 1 ? "Play Game" : "Continue Game";
    $("continue-subtitle").textContent = "Level " + curLevel + " • " + tier.title;

    const today = todayDateKey();
    const dailyDone = Boolean(saveState.dailyChallenges[today]);
    const dailyCard = $("btn-daily-challenge");
    dailyCard.classList.toggle("completed", dailyDone);
    $("daily-subtitle").textContent = dailyDone
      ? "Completed for " + today + " • Replay anytime"
      : today + " • Earn +2 Hints & Bonus Stars";
    $("daily-badge-text").textContent = dailyDone ? "Done" : "Play";
  }

  // ==================== RENDER LEVEL SELECT ====================
  function renderLevelSelect() {
    const highest = saveState.highestUnlockedLevel;
    const completedCount = Object.keys(saveState.completedLevels).length;

    $("levels-summary-subtitle").textContent =
      completedCount + " Completed • Highest: Lv. " + highest;
    $("jump-current-label").textContent = "Lv. " + highest;

    const tiers = highest > 1000 ? DIFFICULTY_TIERS.concat([ENDLESS_TIER]) : DIFFICULTY_TIERS;
    const chipsBar = $("tier-chips-bar");
    chipsBar.innerHTML = "";

    tiers.forEach((tier) => {
      const chip = document.createElement("button");
      chip.type = "button";
      chip.className = "tier-chip" + (tier.key === selectedTierKey ? " selected" : "");
      const range =
        tier.key === "ENDLESS"
          ? "1001+"
          : tier.startLevel === tier.endLevel
          ? String(tier.startLevel)
          : tier.startLevel + "–" + tier.endLevel;
      chip.innerHTML =
        '<span class="tier-dot" style="background:' +
        tier.accent +
        '"></span><span>' +
        tier.title +
        " (" +
        range +
        ")</span>";
      chip.addEventListener("click", () => {
        AudioEngine.unlock();
        AudioEngine.playClick();
        selectedTierKey = tier.key;
        renderLevelSelect();
      });
      chipsBar.appendChild(chip);
    });

    const activeTier =
      tiers.find((t) => t.key === selectedTierKey) || DIFFICULTY_TIERS[0];
    const endLevel =
      activeTier.key === "ENDLESS" ? Math.max(1050, highest + 20) : activeTier.endLevel;

    const grid = $("levels-grid");
    grid.innerHTML = "";
    const fragment = document.createDocumentFragment();

    for (let lvl = activeTier.startLevel; lvl <= endLevel; lvl++) {
      const info = saveState.completedLevels[String(lvl)];
      const isCompleted = Boolean(info);
      const isUnlocked = lvl <= highest;
      const isCurrent = lvl === highest;

      const tile = document.createElement("button");
      tile.type = "button";
      tile.className =
        "level-tile" +
        (isCurrent ? " current" : isCompleted ? " completed" : !isUnlocked ? " locked" : "");

      let badgeHtml = "";
      if (isCompleted) {
        badgeHtml =
          '<span class="tile-badge-corner check" aria-label="Completed">' +
          '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>' +
          "</span>";
      } else if (!isUnlocked) {
        badgeHtml =
          '<span class="tile-badge-corner lock" aria-label="Locked">' +
          '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/></svg>' +
          "</span>";
      }

      let subHtml = "";
      if (isCompleted) {
        let starsHtml = '<div class="tile-stars">';
        for (let s = 1; s <= 3; s++) {
          starsHtml +=
            '<svg viewBox="0 0 24 24" fill="' +
            (s <= info.stars ? "#F59E0B" : "#D9E3F2") +
            '"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>';
        }
        starsHtml += "</div>";
        subHtml = starsHtml;
      } else if (isCurrent) {
        subHtml = '<span class="tile-now">NOW</span>';
      }

      tile.innerHTML = badgeHtml + '<span class="tile-num">' + lvl + "</span>" + subHtml;
      tile.addEventListener("click", () => {
        AudioEngine.unlock();
        if (isUnlocked) {
          AudioEngine.playClick();
          startLevel(lvl, false);
        } else {
          AudioEngine.playError();
          showSnackbar("Complete Level " + highest + " to unlock Level " + lvl + "!");
        }
      });
      fragment.appendChild(tile);
    }

    grid.appendChild(fragment);
  }

  // ==================== RENDER SETTINGS ====================
  function renderSettings() {
    $("toggle-sound").checked = saveState.soundEnabled;
    $("toggle-music").checked = saveState.musicEnabled;
    $("toggle-lives").checked = saveState.livesSystemEnabled;

    const completedCount = Object.keys(saveState.completedLevels).length;
    $("settings-progress-summary").textContent =
      "Highest Unlocked: Level " +
      saveState.highestUnlockedLevel +
      " • Completed: " +
      completedCount +
      " • Stars: " +
      totalStarsCount();
  }

  // ==================== GAMEPLAY LOGIC ====================
  function currentBoardKey() {
    return boardRuntime.isDailyChallenge
      ? "daily_" + boardRuntime.dailyDateKey
      : "level_" + boardRuntime.levelNumber;
  }

  function startLevel(levelNumber, isDaily) {
    const dateKey = todayDateKey();
    const puzzle = isDaily
      ? LevelGenerator.generateDailyChallenge(dateKey)
      : LevelGenerator.generateLevel(levelNumber);

    boardRuntime.levelNumber = isDaily ? 0 : levelNumber;
    boardRuntime.isDailyChallenge = isDaily;
    boardRuntime.dailyDateKey = isDaily ? dateKey : null;
    boardRuntime.puzzle = puzzle;
    boardRuntime.flyingArrows = [];
    boardRuntime.hintedArrowId = null;
    boardRuntime.blockedArrowId = null;
    boardRuntime.blockedTargetCell = null;
    boardRuntime.isLevelComplete = false;
    boardRuntime.isGameOver = false;

    if (saveState.livesRemaining <= 0) {
      saveState.livesRemaining = 3;
      delete saveState.savedBoards[currentBoardKey()];
    }

    if (!isDaily) {
      saveState.currentLevel = Math.max(1, levelNumber);
      if (saveState.currentLevel > saveState.highestUnlockedLevel) {
        saveState.highestUnlockedLevel = saveState.currentLevel;
      }
    }
    persistSaveState(saveState);

    const key = currentBoardKey();
    const saved = saveState.savedBoards[key];
    if (saved && Array.isArray(saved.remainingIds) && saved.remainingIds.length > 0) {
      const idSet = new Set(saved.remainingIds);
      const filtered = puzzle.arrows.filter((a) => idSet.has(a.id));
      if (filtered.length > 0 && filtered.length < puzzle.arrows.length) {
        boardRuntime.remainingArrows = filtered;
        boardRuntime.movesUsed = saved.movesUsed || 0;
        boardRuntime.mistakesUsed = saved.mistakesUsed || 0;
      } else {
        boardRuntime.remainingArrows = puzzle.arrows.slice();
        boardRuntime.movesUsed = 0;
        boardRuntime.mistakesUsed = 0;
      }
    } else {
      boardRuntime.remainingArrows = puzzle.arrows.slice();
      boardRuntime.movesUsed = 0;
      boardRuntime.mistakesUsed = 0;
    }

    hideAllModals();
    showScreen("game");
  }

  function renderGameHud() {
    const puzzle = boardRuntime.puzzle;
    if (!puzzle) return;

    $("game-level-title").textContent = boardRuntime.isDailyChallenge
      ? "Daily Challenge"
      : "Level " + boardRuntime.levelNumber;

    $("game-tier-dot").style.background = puzzle.difficulty.accent;
    $("game-tier-meta").textContent =
      puzzle.shapeName +
      " • " +
      puzzle.difficulty.title +
      " • " +
      boardRuntime.remainingArrows.length +
      " left";

    const btnSound = $("btn-game-sound");
    btnSound.innerHTML = saveState.soundEnabled ? SVG_SOUND_ON : SVG_SOUND_OFF;
    btnSound.classList.toggle("muted", !saveState.soundEnabled);

    const heartsEl = $("hearts-container");
    if (saveState.livesSystemEnabled) {
      let html = "";
      for (let i = 1; i <= 3; i++) {
        html += i <= saveState.livesRemaining ? SVG_HEART : SVG_HEART_LOST;
      }
      heartsEl.innerHTML = html;
    } else {
      heartsEl.innerHTML =
        '<span class="zen-badge">' +
        '<svg class="icon-sm" viewBox="0 0 24 24" fill="currentColor"><path d="M18.6 6.62c-1.44 0-2.8.56-3.77 1.53L12 10.66 10.48 12h.01L7.8 14.39c-.64.64-1.49.99-2.4.99-1.87 0-3.39-1.51-3.39-3.38S3.53 8.62 5.4 8.62c.91 0 1.76.35 2.44 1.03l1.13 1 1.51-1.34L9.22 8.2A5.37 5.37 0 0 0 5.4 6.62C2.42 6.62 0 9.04 0 12s2.42 5.38 5.4 5.38c1.44 0 2.8-.56 3.77-1.53l2.83-2.5.01.01L13.52 12h-.01l2.69-2.39c.64-.64 1.49-.99 2.4-.99 1.87 0 3.39 1.51 3.39 3.38s-1.52 3.38-3.39 3.38c-.9 0-1.76-.35-2.44-1.03l-1.14-1.01-1.51 1.34 1.27 1.12a5.386 5.386 0 0 0 3.82 1.57c2.98 0 5.4-2.41 5.4-5.38s-2.42-5.37-5.4-5.37z"/></svg>' +
        "<span>Zen Mode</span></span>";
    }

    $("moves-counter").textContent = "Moves: " + boardRuntime.movesUsed;
    $("hint-button-label").textContent =
      saveState.hintsRemaining > 0 ? "Hint (" + saveState.hintsRemaining + ")" : "Get Hints";
  }

  function handleArrowTap(arrow) {
    if (boardRuntime.isLevelComplete || boardRuntime.isGameOver || !boardRuntime.puzzle) return;
    if (!boardRuntime.remainingArrows.some((a) => a.id === arrow.id)) return;

    AudioEngine.unlock();
    AudioEngine.playTap();

    const p = boardRuntime.puzzle;
    const blocker = PuzzleSolver.findFirstBlocker(
      arrow,
      boardRuntime.remainingArrows,
      p.obstacleSet,
      p.cols,
      p.rows
    );

    if (blocker === null) {
      removeValidArrow(arrow);
    } else {
      AudioEngine.playError();
      boardRuntime.mistakesUsed += 1;
      if (saveState.livesSystemEnabled) {
        saveState.livesRemaining = Math.max(0, saveState.livesRemaining - 1);
      }

      const outOfLives = saveState.livesSystemEnabled && saveState.livesRemaining <= 0;
      boardRuntime.blockedArrowId = arrow.id;
      boardRuntime.blockedTargetCell = blocker;
      boardRuntime.blockedStartedAt = performance.now();
      boardRuntime.isGameOver = outOfLives;

      if (!outOfLives) {
        saveState.savedBoards[currentBoardKey()] = {
          remainingIds: boardRuntime.remainingArrows.map((a) => a.id),
          movesUsed: boardRuntime.movesUsed,
          mistakesUsed: boardRuntime.mistakesUsed,
        };
      }
      persistSaveState(saveState);
      renderGameHud();

      if (outOfLives) {
        AudioEngine.playGameOver();
        showGameOverOverlay();
      } else {
        const snapTime = boardRuntime.blockedStartedAt;
        setTimeout(() => {
          if (boardRuntime.blockedStartedAt === snapTime) {
            boardRuntime.blockedArrowId = null;
            boardRuntime.blockedTargetCell = null;
          }
        }, 500);
      }
    }
  }

  function removeValidArrow(arrow) {
    const p = boardRuntime.puzzle;
    boardRuntime.remainingArrows = boardRuntime.remainingArrows.filter((a) => a.id !== arrow.id);
    boardRuntime.flyingArrows.push({ piece: arrow, startedAt: performance.now() });
    boardRuntime.movesUsed += 1;
    if (boardRuntime.hintedArrowId === arrow.id) {
      boardRuntime.hintedArrowId = null;
    }
    boardRuntime.blockedArrowId = null;
    boardRuntime.blockedTargetCell = null;

    AudioEngine.playRemove();

    const completed = boardRuntime.remainingArrows.length === 0;
    const earnedStars =
      boardRuntime.mistakesUsed === 0 ? 3 : boardRuntime.mistakesUsed === 1 ? 2 : 1;

    if (completed) {
      boardRuntime.isLevelComplete = true;
      delete saveState.savedBoards[currentBoardKey()];

      if (boardRuntime.isDailyChallenge && boardRuntime.dailyDateKey) {
        const prev = saveState.dailyChallenges[boardRuntime.dailyDateKey];
        const isFirst = !prev;
        saveState.dailyChallenges[boardRuntime.dailyDateKey] = {
          stars: Math.max(prev ? prev.stars : 0, earnedStars),
          moves: Math.min(prev ? prev.moves : Number.MAX_SAFE_INTEGER, boardRuntime.movesUsed),
        };
        if (isFirst) {
          saveState.hintsRemaining += 2;
        }
      } else {
        const lvlKey = String(p.levelNumber);
        const prev = saveState.completedLevels[lvlKey];
        saveState.completedLevels[lvlKey] = {
          stars: Math.max(prev ? prev.stars : 0, earnedStars),
          bestMoves: Math.min(
            prev ? prev.bestMoves : Number.MAX_SAFE_INTEGER,
            boardRuntime.movesUsed
          ),
        };
        const nextLevel = p.levelNumber + 1;
        if (nextLevel > saveState.highestUnlockedLevel) {
          saveState.highestUnlockedLevel = nextLevel;
        }
        saveState.currentLevel = nextLevel;
      }

      persistSaveState(saveState);
      renderGameHud();

      setTimeout(() => {
        AudioEngine.playComplete();
        showCompleteOverlay(earnedStars);
      }, 260);
    } else {
      saveState.savedBoards[currentBoardKey()] = {
        remainingIds: boardRuntime.remainingArrows.map((a) => a.id),
        movesUsed: boardRuntime.movesUsed,
        mistakesUsed: boardRuntime.mistakesUsed,
      };
      persistSaveState(saveState);
      renderGameHud();
    }
  }

  function requestHint() {
    if (boardRuntime.isLevelComplete || boardRuntime.isGameOver || !boardRuntime.puzzle) return;
    if (boardRuntime.remainingArrows.length === 0) return;

    AudioEngine.unlock();

    if (
      boardRuntime.hintedArrowId !== null &&
      boardRuntime.remainingArrows.some((a) => a.id === boardRuntime.hintedArrowId)
    ) {
      AudioEngine.playHint();
      return;
    }

    if (saveState.hintsRemaining <= 0) {
      AudioEngine.playClick();
      openRewardModal("HINTS");
      return;
    }

    const best = PuzzleSolver.findBestHintArrow(
      boardRuntime.remainingArrows,
      boardRuntime.puzzle.obstacleSet,
      boardRuntime.puzzle.cols,
      boardRuntime.puzzle.rows
    );
    if (!best) return;

    AudioEngine.playHint();
    boardRuntime.hintedArrowId = best.id;
    saveState.hintsRemaining = Math.max(0, saveState.hintsRemaining - 1);
    persistSaveState(saveState);
    renderGameHud();
  }

  function revealNextMove() {
    if (boardRuntime.isLevelComplete || boardRuntime.isGameOver || !boardRuntime.puzzle) return;
    if (boardRuntime.remainingArrows.length === 0) return;

    AudioEngine.unlock();

    if (saveState.hintsRemaining <= 0) {
      AudioEngine.playClick();
      openRewardModal("HINTS");
      return;
    }

    const best = PuzzleSolver.findBestHintArrow(
      boardRuntime.remainingArrows,
      boardRuntime.puzzle.obstacleSet,
      boardRuntime.puzzle.cols,
      boardRuntime.puzzle.rows
    );
    if (!best) return;

    saveState.hintsRemaining = Math.max(0, saveState.hintsRemaining - 1);
    persistSaveState(saveState);
    removeValidArrow(best);
  }

  function restartActiveLevel() {
    AudioEngine.unlock();
    AudioEngine.playClick();
    delete saveState.savedBoards[currentBoardKey()];
    saveState.livesRemaining = 3;
    persistSaveState(saveState);
    startLevel(
      boardRuntime.isDailyChallenge ? 1 : boardRuntime.levelNumber,
      boardRuntime.isDailyChallenge
    );
  }

  // ==================== MODALS & CONFETTI ====================
  function hideAllModals() {
    $("overlay-complete").classList.add("hidden");
    $("overlay-gameover").classList.add("hidden");
    $("overlay-reward").classList.add("hidden");
    $("overlay-reset-confirm").classList.add("hidden");
    stopConfetti();
    clearInterval(rewardProgressTimer);
  }

  function showCompleteOverlay(stars) {
    const isDaily = boardRuntime.isDailyChallenge;
    const lvl = boardRuntime.levelNumber;

    $("complete-subtitle").textContent = isDaily
      ? "Daily Challenge Completed!"
      : "Level " + lvl + " Completed!";

    let starsHtml = "";
    for (let i = 1; i <= 3; i++) {
      starsHtml += i <= stars ? SVG_STAR_FILLED : SVG_STAR_EMPTY;
    }
    $("complete-stars").innerHTML = starsHtml;

    $("complete-stats").textContent = isDaily
      ? "Solved in " + boardRuntime.movesUsed + " moves • +2 Bonus Hints!"
      : "Solved in " + boardRuntime.movesUsed + " moves • Level " + (lvl + 1) + " Unlocked!";

    const btnNext = $("btn-next-level");
    btnNext.classList.toggle("hidden", isDaily);
    $("next-level-label").textContent = "Next Level (" + (lvl + 1) + ")";

    $("overlay-complete").classList.remove("hidden");
    startConfetti();
  }

  function showGameOverOverlay() {
    $("gameover-subtitle").textContent = boardRuntime.isDailyChallenge
      ? "No hearts remaining on today's Daily Challenge."
      : "You ran out of hearts on Level " + boardRuntime.levelNumber + ".";
    $("overlay-gameover").classList.remove("hidden");
  }

  function openRewardModal(reason) {
    activeRewardReason = reason;
    const badge = $("reward-badge");
    badge.className = "modal-badge " + (reason === "HINTS" ? "badge-blue" : "badge-coral");
    badge.innerHTML =
      reason === "HINTS"
        ? '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z"/></svg>'
        : '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>';

    $("reward-heading").textContent =
      reason === "HINTS" ? "Brain Boost Bonus" : "Heart Refill Bonus";
    $("reward-body").textContent =
      reason === "HINTS"
        ? "Claim +3 free Hints to help unlock tricky arrow paths."
        : "Restore 3 Hearts and keep your current board progress!";

    const bar = $("reward-progress-bar");
    const btnClaim = $("btn-claim-reward");
    bar.style.width = "0%";
    btnClaim.disabled = true;
    btnClaim.textContent = "Preparing Reward...";

    $("overlay-reward").classList.remove("hidden");

    let step = 0;
    clearInterval(rewardProgressTimer);
    rewardProgressTimer = setInterval(() => {
      step++;
      const pct = Math.min(100, step * 5);
      bar.style.width = pct + "%";
      if (step >= 20) {
        clearInterval(rewardProgressTimer);
        btnClaim.disabled = false;
        btnClaim.textContent = reason === "HINTS" ? "Claim +3 Hints" : "Restore 3 Hearts";
      }
    }, 90);
  }

  function startConfetti() {
    const canvas = $("confetti-canvas");
    const ctx = canvas.getContext("2d");
    const dpr = window.devicePixelRatio || 1;
    canvas.width = window.innerWidth * dpr;
    canvas.height = window.innerHeight * dpr;

    const palette = [
      COLORS.electricBlue,
      COLORS.skyAccent,
      COLORS.gold,
      COLORS.emerald,
      COLORS.purple,
    ];
    const particles = Array.from({ length: 44 }, (_, i) => ({
      xFrac: ((i * 73) % 100) / 100,
      startY: -0.35 + ((i * 37) % 40) / 100,
      speed: 0.65 + ((i * 19) % 75) / 100,
      size: 6 + (i % 6),
      phase: i * 0.4,
      color: palette[i % palette.length],
      isCircle: i % 2 === 0,
    }));

    const t0 = performance.now();
    function frame(now) {
      const progress = ((now - t0) % 2800) / 2800;
      const w = canvas.width;
      const h = canvas.height;
      ctx.clearRect(0, 0, w, h);

      for (let i = 0; i < particles.length; i++) {
        const p = particles[i];
        const yFrac = (p.startY + progress * p.speed * 1.35) % 1.15;
        const xDrift = Math.sin(progress * 6.28 + p.phase) * 22 * dpr;
        const px = p.xFrac * w + xDrift;
        const py = yFrac * h;
        const sz = p.size * dpr;

        ctx.save();
        ctx.globalAlpha = 0.88;
        ctx.fillStyle = p.color;
        if (p.isCircle) {
          ctx.beginPath();
          ctx.arc(px, py, sz * 0.5, 0, Math.PI * 2);
          ctx.fill();
        } else {
          ctx.translate(px, py);
          ctx.rotate(progress * Math.PI * 2 + p.phase);
          ctx.fillRect(-sz * 0.5, -sz * 0.3, sz, sz * 0.6);
        }
        ctx.restore();
      }
      confettiAnimId = requestAnimationFrame(frame);
    }
    confettiAnimId = requestAnimationFrame(frame);
  }

  function stopConfetti() {
    if (confettiAnimId !== null) {
      cancelAnimationFrame(confettiAnimId);
      confettiAnimId = null;
    }
  }

  // ==================== CANVAS RENDERER ====================
  let puzzleCanvas = null;
  let boardCtx = null;
  let canvasCssWidth = 300;
  let canvasCssHeight = 300;

  function resizeBoardCanvas() {
    const puzzle = boardRuntime.puzzle;
    const stage = $("board-stage");
    if (!puzzle || !stage) return;

    const rect = stage.getBoundingClientRect();
    const pad = 44;
    const availW = Math.max(200, rect.width - pad);
    const availH = Math.max(200, rect.height - pad);
    const aspect = puzzle.cols / puzzle.rows;

    let targetW = availW / aspect <= availH ? availW : availH * aspect;
    targetW = Math.min(targetW, 560);
    const targetH = targetW / aspect;

    canvasCssWidth = Math.floor(targetW);
    canvasCssHeight = Math.floor(targetH);

    const dpr = window.devicePixelRatio || 1;
    puzzleCanvas.style.width = canvasCssWidth + "px";
    puzzleCanvas.style.height = canvasCssHeight + "px";
    puzzleCanvas.width = Math.floor(canvasCssWidth * dpr);
    puzzleCanvas.height = Math.floor(canvasCssHeight * dpr);
  }

  function drawRoundedRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
  }

  function drawArrowPiece(ctx, arrow, cellW, cellH, cellSize, color, glowColor, ox, oy, alpha) {
    const centers = arrow.cells.map((p) => ({
      x: (p.x + 0.5) * cellW + ox,
      y: (p.y + 0.5) * cellH + oy,
    }));
    const headCenter = centers[centers.length - 1];
    const strokeW = cellSize * 0.25;
    const headTipOffset = cellSize * 0.26;
    const headBackOffset = cellSize * 0.14;
    const headWing = cellSize * 0.23;

    const dir = arrow.direction;
    const perpX = -dir.dy;
    const perpY = dir.dx;

    function traceSpine() {
      ctx.beginPath();
      if (centers.length === 1) {
        ctx.moveTo(
          headCenter.x - dir.dx * cellSize * 0.24,
          headCenter.y - dir.dy * cellSize * 0.24
        );
        ctx.lineTo(
          headCenter.x + dir.dx * cellSize * 0.04,
          headCenter.y + dir.dy * cellSize * 0.04
        );
      } else {
        ctx.moveTo(centers[0].x, centers[0].y);
        for (let i = 1; i < centers.length; i++) {
          ctx.lineTo(centers[i].x, centers[i].y);
        }
        ctx.lineTo(
          headCenter.x + dir.dx * cellSize * 0.04,
          headCenter.y + dir.dy * cellSize * 0.04
        );
      }
    }

    const tipX = headCenter.x + dir.dx * headTipOffset;
    const tipY = headCenter.y + dir.dy * headTipOffset;
    const baseX = headCenter.x - dir.dx * headBackOffset;
    const baseY = headCenter.y - dir.dy * headBackOffset;
    const leftX = baseX + perpX * headWing;
    const leftY = baseY + perpY * headWing;
    const rightX = baseX - perpX * headWing;
    const rightY = baseY - perpY * headWing;

    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";

    if (glowColor) {
      ctx.strokeStyle = glowColor;
      ctx.lineWidth = strokeW * 1.95;
      traceSpine();
      ctx.stroke();

      ctx.fillStyle = glowColor;
      ctx.beginPath();
      ctx.arc(headCenter.x, headCenter.y, cellSize * 0.42, 0, Math.PI * 2);
      ctx.fill();
    }

    // Main arrow ribbon body
    ctx.strokeStyle = color;
    ctx.lineWidth = strokeW;
    traceSpine();
    ctx.stroke();

    // Subtle crisp inner highlight for depth
    if (centers.length > 1) {
      ctx.strokeStyle = "rgba(255, 255, 255, 0.14)";
      ctx.lineWidth = strokeW * 0.28;
      traceSpine();
      ctx.stroke();
    }

    // Filled arrowhead
    ctx.fillStyle = color;
    ctx.strokeStyle = color;
    ctx.lineWidth = cellSize * 0.05;
    ctx.beginPath();
    ctx.moveTo(tipX, tipY);
    ctx.lineTo(leftX, leftY);
    ctx.lineTo(rightX, rightY);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();

    ctx.restore();
  }

  function renderBoardFrame(now) {
    if (activeScreen === "game" && boardRuntime.puzzle) {
      const p = boardRuntime.puzzle;
      const dpr = window.devicePixelRatio || 1;
      const w = canvasCssWidth;
      const h = canvasCssHeight;
      const ctx = boardCtx;

      ctx.save();
      ctx.scale(dpr, dpr);
      ctx.clearRect(0, 0, w, h);

      const cellW = w / p.cols;
      const cellH = h / p.rows;
      const cellSize = Math.min(cellW, cellH);

      // 1. Artistic silhouette tiles & subtle grid dots (active cells only)
      const tilePad = cellSize * 0.055;
      const tileRad = cellSize * 0.2;
      for (let i = 0; i < p.activeCells.length; i++) {
        const pos = p.activeCells[i];
        const tx = pos.x * cellW + tilePad;
        const ty = pos.y * cellH + tilePad;
        const tw = cellW - tilePad * 2;
        const th = cellH - tilePad * 2;

        drawRoundedRect(ctx, tx, ty, tw, th, tileRad);
        ctx.fillStyle = COLORS.slateSurface;
        ctx.fill();
        ctx.strokeStyle = COLORS.tileStroke;
        ctx.lineWidth = cellSize * 0.03;
        ctx.stroke();

        ctx.fillStyle = COLORS.gridDot;
        ctx.beginPath();
        ctx.arc((pos.x + 0.5) * cellW, (pos.y + 0.5) * cellH, cellSize * 0.062, 0, Math.PI * 2);
        ctx.fill();
      }

      // 2. Stone obstacles
      for (let i = 0; i < p.obstacles.length; i++) {
        const obs = p.obstacles[i];
        const bx = obs.x * cellW + cellW * 0.14;
        const by = obs.y * cellH + cellH * 0.14;
        const bw = cellW * 0.72;
        const bh = cellH * 0.72;
        const rad = cellSize * 0.22;

        drawRoundedRect(ctx, bx, by, bw, bh, rad);
        ctx.fillStyle = COLORS.stoneObstacle;
        ctx.fill();
        ctx.strokeStyle = COLORS.stoneBorder;
        ctx.lineWidth = cellSize * 0.05;
        ctx.stroke();

        const cx = (obs.x + 0.5) * cellW;
        const cy = (obs.y + 0.5) * cellH;
        const r = cellSize * 0.14;
        ctx.strokeStyle = "rgba(255, 255, 255, 0.7)";
        ctx.lineWidth = cellSize * 0.065;
        ctx.lineCap = "round";
        ctx.beginPath();
        ctx.moveTo(cx - r, cy - r);
        ctx.lineTo(cx + r, cy + r);
        ctx.moveTo(cx + r, cy - r);
        ctx.lineTo(cx - r, cy + r);
        ctx.stroke();
      }

      // Hint pulse + dash phase
      const hintPulse = 0.5 + 0.5 * Math.sin((now / 900) * Math.PI * 2);
      const dashPhase = -((now / 700) * 40) % 40;

      // Shake progress
      const shakeElapsed = now - boardRuntime.blockedStartedAt;
      const shakeProgress = shakeElapsed < 360 ? shakeElapsed / 360 : 1;

      // 3. Remaining arrows
      for (let i = 0; i < boardRuntime.remainingArrows.length; i++) {
        const arrow = boardRuntime.remainingArrows[i];
        const isHinted = arrow.id === boardRuntime.hintedArrowId;
        const isBlocked = arrow.id === boardRuntime.blockedArrowId;

        let ox = 0;
        if (isBlocked && shakeProgress < 1) {
          const wave = Math.sin(shakeProgress * Math.PI * 5);
          ox = wave * cellSize * 0.12 * (1 - shakeProgress);
        }

        const color = isBlocked
          ? COLORS.coral
          : isHinted
          ? COLORS.electricBlue
          : COLORS.navyPrimary;

        if (isHinted) {
          const head = arrow.cells[arrow.cells.length - 1];
          const hx = (head.x + 0.5) * cellW;
          const hy = (head.y + 0.5) * cellH;
          let ex = hx;
          let ey = hy;
          if (arrow.direction === DIRECTIONS.UP) ey = 0;
          else if (arrow.direction === DIRECTIONS.DOWN) ey = h;
          else if (arrow.direction === DIRECTIONS.LEFT) ex = 0;
          else ex = w;

          ctx.save();
          ctx.strokeStyle = "rgba(14, 165, 233, " + (0.45 + 0.35 * hintPulse).toFixed(2) + ")";
          ctx.lineWidth = cellSize * 0.08;
          ctx.lineCap = "round";
          ctx.setLineDash([cellSize * 0.18, cellSize * 0.14]);
          ctx.lineDashOffset = dashPhase;
          ctx.beginPath();
          ctx.moveTo(hx, hy);
          ctx.lineTo(ex, ey);
          ctx.stroke();
          ctx.restore();
        }

        const glow = isHinted
          ? "rgba(56, 189, 248, " + (0.28 + 0.25 * hintPulse).toFixed(2) + ")"
          : null;

        drawArrowPiece(ctx, arrow, cellW, cellH, cellSize, color, glow, ox, 0, 1);
      }

      // 4. Flying exit arrows
      const activeFlying = [];
      for (let i = 0; i < boardRuntime.flyingArrows.length; i++) {
        const f = boardRuntime.flyingArrows[i];
        const t = Math.min(1, (now - f.startedAt) / 320);
        if (t < 1) {
          activeFlying.push(f);
          const dist = cellSize * 4.5 * t;
          const ox = f.piece.direction.dx * dist;
          const oy = f.piece.direction.dy * dist;
          const glow = "rgba(56, 189, 248, " + (0.25 * (1 - t)).toFixed(2) + ")";
          drawArrowPiece(
            ctx,
            f.piece,
            cellW,
            cellH,
            cellSize,
            COLORS.electricBlue,
            glow,
            ox,
            oy,
            Math.max(0, 1 - t)
          );
        }
      }
      boardRuntime.flyingArrows = activeFlying;

      // 5. Blocker target ring
      if (boardRuntime.blockedTargetCell && boardRuntime.blockedArrowId !== null) {
        const bx = (boardRuntime.blockedTargetCell.x + 0.5) * cellW;
        const by = (boardRuntime.blockedTargetCell.y + 0.5) * cellH;
        ctx.strokeStyle = "rgba(239, 68, 68, 0.55)";
        ctx.lineWidth = cellSize * 0.06;
        ctx.beginPath();
        ctx.arc(bx, by, cellSize * 0.36, 0, Math.PI * 2);
        ctx.stroke();
      }

      ctx.restore();
    }
    requestAnimationFrame(renderBoardFrame);
  }

  // ==================== EVENT BINDING ====================
  function bindEvents() {
    window.addEventListener("resize", () => {
      if (activeScreen === "game") resizeBoardCanvas();
    });

    document.addEventListener("visibilitychange", () => {
      AudioEngine.setVisibility(!document.hidden);
    });

    // Main Menu buttons
    $("btn-menu-sound").addEventListener("click", () => {
      AudioEngine.unlock();
      saveState.soundEnabled = !saveState.soundEnabled;
      AudioEngine.setPrefs(saveState.soundEnabled, saveState.musicEnabled);
      AudioEngine.playClick();
      persistSaveState(saveState);
      renderMenu();
    });

    $("btn-menu-music").addEventListener("click", () => {
      AudioEngine.unlock();
      saveState.musicEnabled = !saveState.musicEnabled;
      AudioEngine.setPrefs(saveState.soundEnabled, saveState.musicEnabled);
      AudioEngine.playClick();
      persistSaveState(saveState);
      renderMenu();
    });

    $("btn-menu-settings").addEventListener("click", () => {
      AudioEngine.unlock();
      AudioEngine.playClick();
      showScreen("settings");
    });

    $("btn-save-status-pill").addEventListener("click", () => {
      AudioEngine.unlock();
      AudioEngine.playClick();
      showScreen("settings");
    });

    $("btn-open-settings").addEventListener("click", () => {
      AudioEngine.unlock();
      AudioEngine.playClick();
      showScreen("settings");
    });

    $("btn-continue-game").addEventListener("click", () => {
      AudioEngine.unlock();
      AudioEngine.playClick();
      startLevel(Math.max(1, saveState.currentLevel), false);
    });

    $("btn-level-select").addEventListener("click", () => {
      AudioEngine.unlock();
      AudioEngine.playClick();
      selectedTierKey = tierForLevel(saveState.currentLevel).key;
      showScreen("levels");
    });

    $("btn-daily-challenge").addEventListener("click", () => {
      AudioEngine.unlock();
      AudioEngine.playClick();
      startLevel(1, true);
    });

    // Game Screen buttons
    $("btn-game-back").addEventListener("click", () => {
      AudioEngine.playClick();
      showScreen("menu");
    });

    $("btn-game-sound").addEventListener("click", () => {
      AudioEngine.unlock();
      saveState.soundEnabled = !saveState.soundEnabled;
      AudioEngine.setPrefs(saveState.soundEnabled, saveState.musicEnabled);
      AudioEngine.playClick();
      persistSaveState(saveState);
      renderGameHud();
    });

    $("btn-game-restart").addEventListener("click", restartActiveLevel);
    $("btn-hint").addEventListener("click", requestHint);
    $("btn-next-move").addEventListener("click", revealNextMove);

    // Canvas pointer tap
    puzzleCanvas.addEventListener("pointerdown", (ev) => {
      if (!boardRuntime.puzzle) return;
      const rect = puzzleCanvas.getBoundingClientRect();
      const x = ev.clientX - rect.left;
      const y = ev.clientY - rect.top;
      const p = boardRuntime.puzzle;
      const col = Math.max(0, Math.min(p.cols - 1, Math.floor((x / rect.width) * p.cols)));
      const row = Math.max(0, Math.min(p.rows - 1, Math.floor((y / rect.height) * p.rows)));

      const hit = boardRuntime.remainingArrows.find((a) =>
        a.cells.some((c) => c.x === col && c.y === row)
      );
      if (hit) {
        handleArrowTap(hit);
      }
    });

    // Level Select Screen buttons
    $("btn-levels-back").addEventListener("click", () => {
      AudioEngine.playClick();
      showScreen("menu");
    });

    $("btn-jump-current").addEventListener("click", () => {
      AudioEngine.unlock();
      AudioEngine.playClick();
      startLevel(saveState.highestUnlockedLevel, false);
    });

    // Settings Screen controls
    $("btn-settings-back").addEventListener("click", () => {
      AudioEngine.playClick();
      showScreen("menu");
    });

    $("toggle-sound").addEventListener("change", (ev) => {
      AudioEngine.unlock();
      saveState.soundEnabled = Boolean(ev.target.checked);
      AudioEngine.setPrefs(saveState.soundEnabled, saveState.musicEnabled);
      AudioEngine.playClick();
      persistSaveState(saveState);
    });

    $("toggle-music").addEventListener("change", (ev) => {
      AudioEngine.unlock();
      saveState.musicEnabled = Boolean(ev.target.checked);
      AudioEngine.setPrefs(saveState.soundEnabled, saveState.musicEnabled);
      AudioEngine.playClick();
      persistSaveState(saveState);
    });

    $("toggle-lives").addEventListener("change", (ev) => {
      AudioEngine.unlock();
      saveState.livesSystemEnabled = Boolean(ev.target.checked);
      AudioEngine.playClick();
      persistSaveState(saveState);
    });

    $("btn-reset-progress").addEventListener("click", () => {
      AudioEngine.playClick();
      $("overlay-reset-confirm").classList.remove("hidden");
    });

    $("btn-cancel-reset").addEventListener("click", () => {
      $("overlay-reset-confirm").classList.add("hidden");
    });

    $("btn-confirm-reset").addEventListener("click", () => {
      AudioEngine.playClick();
      const soundKeep = saveState.soundEnabled;
      const musicKeep = saveState.musicEnabled;
      const livesSysKeep = saveState.livesSystemEnabled;
      saveState = defaultSaveState();
      saveState.soundEnabled = soundKeep;
      saveState.musicEnabled = musicKeep;
      saveState.livesSystemEnabled = livesSysKeep;
      persistSaveState(saveState);
      $("overlay-reset-confirm").classList.add("hidden");
      renderSettings();
      showSnackbar("Progress reset to Level 1");
    });

    // Completion, Game Over & Reward Modal Buttons
    $("btn-next-level").addEventListener("click", () => {
      AudioEngine.playClick();
      const next = Math.max(1, boardRuntime.levelNumber + 1);
      startLevel(next, false);
    });

    $("btn-complete-menu").addEventListener("click", () => {
      AudioEngine.playClick();
      hideAllModals();
      showScreen("menu");
    });

    $("btn-retry-level").addEventListener("click", restartActiveLevel);

    $("btn-restore-lives").addEventListener("click", () => {
      AudioEngine.playClick();
      openRewardModal("LIVES");
    });

    $("btn-gameover-menu").addEventListener("click", () => {
      AudioEngine.playClick();
      hideAllModals();
      showScreen("menu");
    });

    $("btn-claim-reward").addEventListener("click", () => {
      if (!activeRewardReason) return;
      AudioEngine.playHint();
      if (activeRewardReason === "HINTS") {
        saveState.hintsRemaining += 3;
      } else if (activeRewardReason === "LIVES") {
        saveState.livesRemaining = 3;
        boardRuntime.isGameOver = false;
        $("overlay-gameover").classList.add("hidden");
      }
      activeRewardReason = null;
      persistSaveState(saveState);
      $("overlay-reward").classList.add("hidden");
      renderGameHud();
    });

    $("btn-dismiss-reward").addEventListener("click", () => {
      clearInterval(rewardProgressTimer);
      activeRewardReason = null;
      $("overlay-reward").classList.add("hidden");
    });
  }

  // ==================== EXPOSE TEST HOOKS & BOOTSTRAP ====================
  if (typeof module !== "undefined" && module.exports) {
    module.exports = {
      DIRECTIONS,
      DIFFICULTY_TIERS,
      tierForLevel,
      SilhouetteLibrary,
      PuzzleSolver,
      LevelGenerator,
    };
  }

  if (typeof document !== "undefined") {
    screens = {
      menu: $("screen-menu"),
      game: $("screen-game"),
      levels: $("screen-levels"),
      settings: $("screen-settings"),
    };
    puzzleCanvas = $("puzzle-canvas");
    boardCtx = puzzleCanvas.getContext("2d");
    bindEvents();
    renderMenu();
    requestAnimationFrame(renderBoardFrame);
  }
})();
