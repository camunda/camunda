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

// ── Sync ──────────────────────────────────────────────────────────────────────

function serializeView(node) {
  return [...node.membershipView.entries()].map(([id, v]) => ({ memberId: id, ...v }));
}

function handleSyncTick(sim, node, wallTime) {
  const target = pick(node.probeOrder, []);
  if (target !== null && !isPartitioned(sim, node.id, target)) {
    sim.msgCount++;
    const view = serializeView(node);
    sim.inFlightMessages.push({
      id: sim.nextMsgId++, from: node.id, to: target, label: 'S⇄', color: 'var(--viz-msg-sync)',
      startWallTime: wallTime, durationMs: 400,
      payload: { type: 'SYNC', view },
    });
    sim.eventQueue.push({
      type: 'MSG_DELIVER', simTime: sim.simTime + 1, from: node.id, to: target,
      payload: { type: 'SYNC', view },
    });
  }
  sim.eventQueue.push({ type: 'SYNC_TICK', simTime: sim.simTime + sim.params.syncInterval, nodeId: node.id });
}

function handleSyncReceived(sim, from, to, view, wallTime) {
  const node = sim.nodes.get(to);
  if (!node || node.state === 'crashed') return;
  for (const entry of view) {
    if (entry.memberId !== to) handleGossipReceived(sim, from, to, [entry]);
  }
  if (!isPartitioned(sim, to, from)) {
    sim.msgCount++;
    const responseView = serializeView(node);
    sim.inFlightMessages.push({
      id: sim.nextMsgId++, from: to, to: from, label: 'S⇄', color: 'var(--viz-msg-sync)',
      startWallTime: wallTime, durationMs: 400,
      payload: { type: 'SYNC_RESPONSE', view: responseView },
    });
    sim.eventQueue.push({
      type: 'MSG_DELIVER', simTime: sim.simTime + 1, from: to, to: from,
      payload: { type: 'SYNC_RESPONSE', view: responseView },
    });
  }
}

// ── Message dispatch ──────────────────────────────────────────────────────────

function handleMsgDeliver(sim, event, wallTime) {
  const { from, to, payload } = event;
  const node = sim.nodes.get(to);
  if (!node || node.state === 'crashed' || isPartitioned(sim, from, to)) return;

  switch (payload.type) {
    case 'GOSSIP': handleGossipReceived(sim, from, to, payload.entries); break;
    case 'PROBE':  handleProbeReceived(sim, event, wallTime); break;
    case 'PROBE_ACK': {
      // `from` = node that ACKed (was probed), `to` = original prober
      const prober = sim.nodes.get(to);
      if (!prober) break;
      // Resurrection: prober had `from` in its deadNodes list
      const deadIdx = prober.deadNodes.indexOf(from);
      if (deadIdx !== -1) {
        prober.deadNodes.splice(deadIdx, 1);
        const newIncarnation = (sim.nodes.get(from)?.incarnationNumber ?? 0) + 1;
        prober.membershipView.set(from, { state: 'alive', incarnation: newIncarnation, propertyVersion: 0 });
        if (!prober.probeOrder.includes(from)) { prober.probeOrder.push(from); shuffle(prober.probeOrder); }
        logEvent(sim, `N${to}: N${from} resurrected`);
        enqueueGossipUpdate(prober, { memberId: from, state: 'alive', incarnation: newIncarnation });
      } else {
        // Normal ACK — clear any pending suspicion
        const view = prober.membershipView.get(from);
        if (view && view.state === 'suspect') {
          view.state = 'alive';
          prober.suspectSince.delete(from);
          logEvent(sim, `N${to} cleared suspicion of N${from}`);
        }
      }
      break;
    }
    case 'PROBE_REQUEST': {
      // We are an indirect prober — probe `suspect` on behalf of `coordinator`
      const { suspect } = payload;
      if (isPartitioned(sim, to, suspect)) break;
      sim.msgCount++;
      sim.inFlightMessages.push({
        id: sim.nextMsgId++, from: to, to: suspect, label: 'P→', color: 'var(--viz-msg-probe)',
        startWallTime: wallTime, durationMs: 300,
        payload: { type: 'PROBE' },
      });
      sim.eventQueue.push({
        type: 'MSG_DELIVER', simTime: sim.simTime + 1, from: to, to: suspect,
        payload: { type: 'PROBE' },
      });
      break;
    }
    case 'SYNC':          handleSyncReceived(sim, from, to, payload.view, wallTime); break;
    case 'SYNC_RESPONSE': {
      for (const entry of payload.view) handleGossipReceived(sim, from, to, [entry]);
      break;
    }
  }
}

// ── Convergence ───────────────────────────────────────────────────────────────

function checkConvergence(sim) {
  if (sim.convergeTime !== null || sim.faultInjectedAt === null) return;
  const aliveNodes = [...sim.nodes.values()].filter(n => n.state === 'alive');
  if (aliveNodes.length < 2) return;

  const ref = aliveNodes[0];
  for (const node of aliveNodes.slice(1)) {
    for (const [id, refEntry] of ref.membershipView.entries()) {
      const target = sim.nodes.get(id);
      if (!target || target.state === 'crashed') continue;
      const entry = node.membershipView.get(id);
      if (!entry || entry.state !== refEntry.state || (entry.propertyVersion ?? 0) !== (refEntry.propertyVersion ?? 0)) return;
    }
  }

  sim.convergeTime = sim.simTime - sim.faultInjectedAt;
  logEvent(sim, `Cluster converged (all nodes agree)`);
}

// ── Fault injection ───────────────────────────────────────────────────────────

export function crashNode(sim, nodeId) {
  const node = sim.nodes.get(nodeId);
  if (!node || node.state === 'crashed') return;
  node.state = 'crashed';
  sim.eventQueue.removeWhere(e => e.nodeId === nodeId);
  sim.inFlightMessages = sim.inFlightMessages.filter(m => m.from !== nodeId && m.to !== nodeId);
  sim.faultInjectedAt = sim.simTime;
  sim.firstDetectTime = null;
  sim.convergeTime = null;
  logEvent(sim, `N${nodeId} crashed`);
}

export function restoreNode(sim, nodeId) {
  const node = sim.nodes.get(nodeId);
  if (!node || node.state !== 'crashed') return;
  node.state = 'alive';
  node.incarnationNumber++;
  node.suspectSince.clear();
  sim.eventQueue.push({ type: 'GOSSIP_TICK', simTime: sim.simTime + sim.params.gossipInterval, nodeId });
  sim.eventQueue.push({ type: 'PROBE_TICK',  simTime: sim.simTime + sim.params.probeInterval,  nodeId });
  sim.eventQueue.push({ type: 'SYNC_TICK',   simTime: sim.simTime + sim.params.syncInterval,   nodeId });
  enqueueGossipUpdate(node, { memberId: nodeId, state: 'alive', incarnation: node.incarnationNumber });
  logEvent(sim, `N${nodeId} restored (incarnation ${node.incarnationNumber})`);
}

export function injectPropertyUpdate(sim, nodeId) {
  const node = sim.nodes.get(nodeId);
  if (!node || node.state !== 'alive') return;
  node.propertyVersion++;
  enqueueGossipUpdate(node, {
    memberId: nodeId, state: 'alive', incarnation: node.incarnationNumber,
    propertyVersion: node.propertyVersion, isPropertyUpdate: true,
  });
  sim.faultInjectedAt = sim.simTime;
  sim.firstDetectTime = null;
  sim.convergeTime = null;
  logEvent(sim, `N${nodeId} property update v${node.propertyVersion}`);
}

export function addPartition(sim, from, to) {
  sim.partitions.add(`${from}-${to}`);
  logEvent(sim, `Partition: N${from} → N${to} blocked`);
}

export function clearPartitions(sim) {
  sim.partitions.clear();
  logEvent(sim, 'All partitions cleared');
}

export function setGossipExcluded(sim, nodeId, excluded) {
  const node = sim.nodes.get(nodeId);
  if (node) {
    node.gossipExcluded = excluded;
    logEvent(sim, `N${nodeId} gossip ${excluded ? 'excluded' : 'restored'}`);
  }
}

// ── Main simulation loop ──────────────────────────────────────────────────────

export function advanceSim(sim, targetSimTime, wallTime) {
  while (!sim.eventQueue.isEmpty() && sim.eventQueue.peek().simTime <= targetSimTime) {
    const event = sim.eventQueue.pop();
    sim.simTime = event.simTime;
    const node = event.nodeId !== undefined ? sim.nodes.get(event.nodeId) : null;
    if (node && node.state === 'crashed' && event.type !== 'MSG_DELIVER') continue;

    switch (event.type) {
      case 'GOSSIP_TICK':      if (node) handleGossipTick(sim, node, wallTime); break;
      case 'PROBE_TICK':       if (node) handleProbeTick(sim, node, wallTime); break;
      case 'PROBE_TIMEOUT':    handleProbeTimeout(sim, event, wallTime); break;
      case 'INDIRECT_TIMEOUT': handleIndirectTimeout(sim, event, wallTime); break;
      case 'FAILURE_TIMEOUT':  handleFailureTimeout(sim, event); break;
      case 'SYNC_TICK':        if (node) handleSyncTick(sim, node, wallTime); break;
      case 'MSG_DELIVER':      handleMsgDeliver(sim, event, wallTime); break;
    }
    checkConvergence(sim);
  }
  sim.simTime = targetSimTime;
  sim.inFlightMessages = sim.inFlightMessages.filter(m => wallTime - m.startWallTime < m.durationMs + 100);
}

// ── Snapshot (for React rendering) ───────────────────────────────────────────

export function captureSnapshot(sim, wallTime) {
  return {
    simTime: sim.simTime,
    roundCount: sim.roundCount,
    msgCount: sim.msgCount,
    firstDetectTime: sim.firstDetectTime,
    convergeTime: sim.convergeTime,
    hasPartitions: sim.partitions.size > 0,
    partitions: [...sim.partitions],
    eventLog: [...sim.eventLog],
    nodes: [...sim.nodes.values()].map(n => ({
      id: n.id,
      state: n.state,
      propertyVersion: n.propertyVersion,
      gossipExcluded: n.gossipExcluded,
      view: new Map([...n.membershipView.entries()]),
    })),
    messages: sim.inFlightMessages.map(m => {
      const progress = Math.min(1, (wallTime - m.startWallTime) / m.durationMs);
      return { ...m, progress };
    }).filter(m => m.progress >= 0 && m.progress <= 1),
  };
}
