// swimSimEngine.js — SWIM membership protocol simulation engine
// No React imports. All state is plain JS objects mutated directly.

// ── Priority queue (min-heap by simTime) ─────────────────────────────────────

function createEventQueue() {
  const h = [];
  const swap = (i, j) => { [h[i], h[j]] = [h[j], h[i]]; };
  const up = (i) => {
    while (i > 0) {
      const p = (i - 1) >> 1;
      if (h[p].simTime <= h[i].simTime) break;
      swap(p, i); i = p;
    }
  };
  const down = (i) => {
    for (;;) {
      let s = i, l = 2*i+1, r = 2*i+2;
      if (l < h.length && h[l].simTime < h[s].simTime) s = l;
      if (r < h.length && h[r].simTime < h[s].simTime) s = r;
      if (s === i) break;
      swap(i, s); i = s;
    }
  };
  return {
    push(e) { h.push(e); up(h.length - 1); },
    pop() {
      if (!h.length) return null;
      const min = h[0];
      const last = h.pop();
      if (h.length) { h[0] = last; down(0); }
      return min;
    },
    peek() { return h[0] ?? null; },
    isEmpty() { return h.length === 0; },
    removeWhere(pred) {
      for (let i = h.length - 1; i >= 0; i--) {
        if (pred(h[i])) h.splice(i, 1);
      }
      for (let i = (h.length >> 1) - 1; i >= 0; i--) down(i);
    },
  };
}

// ── Utilities ─────────────────────────────────────────────────────────────────

export function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

function pick(arr, exclude = []) {
  const pool = arr.filter(x => !exclude.includes(x));
  if (!pool.length) return null;
  return pool[Math.floor(Math.random() * pool.length)];
}

function pickN(arr, n, exclude = []) {
  const pool = shuffle(arr.filter(x => !exclude.includes(x)));
  return pool.slice(0, n);
}

function isPartitioned(sim, from, to) {
  return sim.partitions.has(`${from}-${to}`);
}

function logEvent(sim, text) {
  sim.eventLog.unshift({ simTime: sim.simTime, text });
  if (sim.eventLog.length > 100) sim.eventLog.length = 100;
}

// ── Node state factory ────────────────────────────────────────────────────────

function createNode(id, nodeCount) {
  const peers = Array.from({ length: nodeCount }, (_, i) => i).filter(i => i !== id);
  return {
    id,
    state: 'alive',
    incarnationNumber: 0,
    propertyVersion: 0,
    membershipView: new Map(peers.map(p => [p, { state: 'alive', incarnation: 0, propertyVersion: 0 }])),
    gossipQueue: [],
    probeOrder: shuffle([...peers]),
    probeCounter: 0,
    deadNodes: [],
    deadProbeCounter: 0,
    suspectSince: new Map(),
    gossipExcluded: false,
  };
}

// ── Simulation state factory ──────────────────────────────────────────────────

export function createSim(nodeCount, params) {
  const nodes = new Map();
  for (let i = 0; i < nodeCount; i++) nodes.set(i, createNode(i, nodeCount));
  const sim = {
    nodes,
    partitions: new Set(),
    eventQueue: createEventQueue(),
    inFlightMessages: [],
    simTime: 0,
    roundCount: 0,
    msgCount: 0,
    eventLog: [],
    faultInjectedAt: null,
    firstDetectTime: null,
    convergeTime: null,
    nextMsgId: 0,
    params,
  };
  nodes.forEach((_, id) => {
    sim.eventQueue.push({ type: 'GOSSIP_TICK', simTime: params.gossipInterval, nodeId: id });
    sim.eventQueue.push({ type: 'PROBE_TICK',  simTime: params.probeInterval,  nodeId: id });
    sim.eventQueue.push({ type: 'SYNC_TICK',   simTime: params.syncInterval,   nodeId: id });
  });
  return sim;
}
