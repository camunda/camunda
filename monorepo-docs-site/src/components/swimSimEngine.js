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

// ── Failure detection ─────────────────────────────────────────────────────────

function sendProbe(sim, from, to, wallTime) {
  if (isPartitioned(sim, from, to)) return;
  sim.msgCount++;
  sim.inFlightMessages.push({
    id: sim.nextMsgId++, from, to, label: 'P→', color: 'var(--viz-msg-probe)',
    startWallTime: wallTime, durationMs: 300,
    payload: { type: 'PROBE' },
  });
  sim.eventQueue.push({ type: 'MSG_DELIVER', simTime: sim.simTime + 1, from, to, payload: { type: 'PROBE' } });
}

function handleProbeTick(sim, node, wallTime) {
  // Probe one alive peer via shuffle + round-robin through probeOrder
  if (node.probeOrder.length > 0) {
    const idx = node.probeCounter % node.probeOrder.length;
    node.probeCounter++;
    const targetId = node.probeOrder[idx];
    const target = sim.nodes.get(targetId);
    if (target && target.state !== 'crashed') {
      sendProbe(sim, node.id, targetId, wallTime);
      sim.eventQueue.push({
        type: 'PROBE_TIMEOUT', simTime: sim.simTime + sim.params.probeTimeout,
        from: node.id, to: targetId,
      });
    }
  }
  // Also probe one dead node per tick (resurrection check).
  // If it responds, the PROBE_ACK handler detects it's in deadNodes and resurrects it.
  if (node.deadNodes.length > 0) {
    const idx = node.deadProbeCounter % node.deadNodes.length;
    node.deadProbeCounter++;
    const deadId = node.deadNodes[idx];
    sendProbe(sim, node.id, deadId, wallTime);
  }
  sim.eventQueue.push({ type: 'PROBE_TICK', simTime: sim.simTime + sim.params.probeInterval, nodeId: node.id });
}

function handleProbeTimeout(sim, event, wallTime) {
  const { from, to } = event;
  const prober = sim.nodes.get(from);
  if (!prober || prober.state === 'crashed') return;
  const target = sim.nodes.get(to);
  if (!target) return;

  // Send indirect probe requests to suspectProbes-1 random peers.
  // Each peer receives a PROBE_REQUEST carrying the suspect's ID.
  const indirectPeers = pickN(
    [...sim.nodes.values()]
      .filter(n => n.id !== from && n.id !== to && n.state === 'alive')
      .map(n => n.id),
    sim.params.suspectProbes - 1
  );

  if (indirectPeers.length === 0) {
    markSuspect(sim, prober, to, wallTime);
    return;
  }

  indirectPeers.forEach(pid => {
    if (isPartitioned(sim, from, pid)) return;
    sim.msgCount++;
    sim.inFlightMessages.push({
      id: sim.nextMsgId++, from, to: pid, label: 'PR→', color: '#e65100',
      startWallTime: wallTime, durationMs: 300,
      payload: { type: 'PROBE_REQUEST', suspect: to, coordinator: from },
    });
    sim.eventQueue.push({
      type: 'MSG_DELIVER', simTime: sim.simTime + 1, from, to: pid,
      payload: { type: 'PROBE_REQUEST', suspect: to, coordinator: from },
    });
  });

  sim.eventQueue.push({
    type: 'INDIRECT_TIMEOUT', simTime: sim.simTime + sim.params.probeTimeout * 2,
    from, to,
  });
}

function handleIndirectTimeout(sim, event, wallTime) {
  const { from, to } = event;
  const prober = sim.nodes.get(from);
  if (!prober || prober.state === 'crashed') return;
  markSuspect(sim, prober, to, wallTime);
}

function markSuspect(sim, prober, targetId, wallTime) {
  const view = prober.membershipView.get(targetId);
  if (!view || view.state !== 'alive') return;

  view.state = 'suspect';
  prober.suspectSince.set(targetId, sim.simTime);
  logEvent(sim, `N${prober.id} suspects N${targetId}`);

  if (sim.faultInjectedAt !== null && sim.firstDetectTime === null) {
    sim.firstDetectTime = sim.simTime - sim.faultInjectedAt;
  }

  const entry = { memberId: targetId, state: 'suspect', incarnation: view.incarnation };
  enqueueGossipUpdate(prober, entry);

  if (sim.params.broadcastDisputes) {
    [...sim.nodes.values()]
      .filter(n => n.id !== prober.id && n.state === 'alive' && !isPartitioned(sim, prober.id, n.id))
      .forEach(n => sendGossip(sim, prober.id, n.id, [entry], wallTime));
  }

  if (sim.params.notifySuspect) {
    sendProbe(sim, prober.id, targetId, wallTime);
  }

  sim.eventQueue.push({
    type: 'FAILURE_TIMEOUT', simTime: sim.simTime + sim.params.failureTimeout,
    from: prober.id, to: targetId,
  });
}

function handleFailureTimeout(sim, event) {
  const { from, to } = event;
  const prober = sim.nodes.get(from);
  if (!prober || prober.state === 'crashed') return;
  const view = prober.membershipView.get(to);
  if (!view || view.state !== 'suspect') return;

  view.state = 'dead';
  logEvent(sim, `N${from} declares N${to} DEAD`);

  prober.probeOrder = prober.probeOrder.filter(id => id !== to);
  shuffle(prober.probeOrder);
  if (!prober.deadNodes.includes(to)) prober.deadNodes.push(to);

  const entry = { memberId: to, state: 'dead', incarnation: view.incarnation };
  enqueueGossipUpdate(prober, entry);

  if (sim.params.broadcastDisputes) {
    [...sim.nodes.values()]
      .filter(n => n.id !== from && n.state === 'alive' && !isPartitioned(sim, from, n.id))
      .forEach(n => sendGossip(sim, from, n.id, [entry], 0));
  }
}

function handleProbeReceived(sim, event, wallTime) {
  const { from, to } = event;
  // `from` = prober, `to` = us (the probed node)
  const target = sim.nodes.get(to);
  if (!target || target.state === 'crashed' || isPartitioned(sim, to, from)) return;

  sim.msgCount++;
  sim.inFlightMessages.push({
    id: sim.nextMsgId++, from: to, to: from, label: 'P✓', color: 'var(--viz-gossip-update)',
    startWallTime: wallTime, durationMs: 300,
    payload: { type: 'PROBE_ACK' },
  });
  sim.eventQueue.push({
    type: 'MSG_DELIVER', simTime: sim.simTime + 1, from: to, to: from,
    payload: { type: 'PROBE_ACK' },
  });
}
}
