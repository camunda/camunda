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

// ── Gossip ────────────────────────────────────────────────────────────────────

function stateRank(state) {
  return state === 'alive' ? 0 : state === 'suspect' ? 1 : 2;
}

function enqueueGossipUpdate(node, entry) {
  const existing = node.gossipQueue.find(e => e.memberId === entry.memberId);
  if (existing) {
    if (entry.incarnation > existing.incarnation ||
        (entry.incarnation === existing.incarnation && stateRank(entry.state) > stateRank(existing.state))) {
      Object.assign(existing, entry, { sendCount: 0 });
    }
  } else {
    node.gossipQueue.push({ ...entry, sendCount: 0 });
  }
}

function selectGossipTargets(sim, node) {
  const alive = [...sim.nodes.values()].filter(n =>
    n.id !== node.id && n.state === 'alive' && !n.gossipExcluded
  ).map(n => n.id);
  if (sim.params.broadcastUpdates) return alive;
  return pickN(alive, sim.params.gossipFanout, []);
}

function sendGossip(sim, from, to, entries, wallTime) {
  if (isPartitioned(sim, from, to)) return;
  sim.msgCount++;
  const hasFailure = entries.some(e => e.state === 'dead' || e.state === 'suspect');
  const hasUpdate = entries.some(e => e.isPropertyUpdate);
  const label = hasFailure ? 'G✗' : hasUpdate ? 'G↑' : 'G~';
  const color = label === 'G✗' ? 'var(--viz-leader)'
    : label === 'G↑' ? 'var(--viz-gossip-update)'
    : 'var(--ifm-color-emphasis-400)';
  sim.inFlightMessages.push({
    id: sim.nextMsgId++, from, to, label, color,
    startWallTime: wallTime, durationMs: 300,
    payload: { type: 'GOSSIP', entries },
  });
  sim.eventQueue.push({
    type: 'MSG_DELIVER', simTime: sim.simTime + 1, from, to,
    payload: { type: 'GOSSIP', entries },
  });
}

function handleGossipTick(sim, node, wallTime) {
  sim.roundCount++;
  const maxSends = Math.ceil(Math.log2(sim.nodes.size + 1));
  const entries = [...node.gossipQueue]
    .sort((a, b) => a.sendCount - b.sendCount)
    .slice(0, 8);
  entries.forEach(e => e.sendCount++);
  node.gossipQueue = node.gossipQueue.filter(e => e.sendCount < maxSends);
  const targets = selectGossipTargets(sim, node);
  targets.forEach(tid => sendGossip(sim, node.id, tid, entries, wallTime));
  sim.eventQueue.push({ type: 'GOSSIP_TICK', simTime: sim.simTime + sim.params.gossipInterval, nodeId: node.id });
}

function handleGossipReceived(sim, from, to, entries) {
  const node = sim.nodes.get(to);
  if (!node || node.state === 'crashed') return;
  for (const entry of entries) {
    if (entry.memberId === to) {
      if (entry.state === 'suspect' && node.state === 'alive') {
        node.incarnationNumber++;
        const correction = { memberId: to, state: 'alive', incarnation: node.incarnationNumber };
        enqueueGossipUpdate(node, correction);
        if (sim.params.broadcastDisputes) {
          [...sim.nodes.values()]
            .filter(n => n.id !== to && n.state === 'alive' && !isPartitioned(sim, to, n.id))
            .forEach(n => sendGossip(sim, to, n.id, [correction], 0));
        }
      }
      continue;
    }
    const view = node.membershipView.get(entry.memberId);
    if (!view) continue;
    const acceptNew = entry.incarnation > view.incarnation ||
      (entry.incarnation === view.incarnation && stateRank(entry.state) > stateRank(view.state));
    const propChanged = entry.incarnation === view.incarnation &&
      entry.state === view.state &&
      entry.propertyVersion !== undefined &&
      entry.propertyVersion > (view.propertyVersion ?? 0);
    if (acceptNew || propChanged) {
      view.state = entry.state;
      view.incarnation = entry.incarnation;
      if (entry.propertyVersion !== undefined) view.propertyVersion = entry.propertyVersion;
      enqueueGossipUpdate(node, entry);
    }
  }
}
