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

function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

function pick(arr) {
  if (!arr.length) return null;
  return arr[Math.floor(Math.random() * arr.length)];
}

function pickN(arr, n) {
  return shuffle([...arr]).slice(0, n);
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
    lastAckFrom: new Map(),       // nodeId → simTime; cancels pending PROBE/INDIRECT timeouts
    pendingIndirectFor: new Map(), // suspectId → coordinatorId; set while acting as indirect prober
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
    hasDiverged: false,   // true once any two alive nodes' views differ after a fault
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
        (entry.incarnation === existing.incarnation && stateRank(entry.state) > stateRank(existing.state)) ||
        (entry.incarnation === existing.incarnation && entry.state === existing.state &&
         (entry.propertyVersion ?? 0) > (existing.propertyVersion ?? 0))) {
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
  return pickN(alive, sim.params.gossipFanout);
}

function sendGossip(sim, from, to, entries, wallTime) {
  if (isPartitioned(sim, from, to)) return;
  sim.msgCount++;
  const hasFailure = entries.some(e => e.state === 'dead' || e.state === 'suspect');
  const label = hasFailure ? 'U⚠' : 'U↑';
  const color = hasFailure ? 'var(--viz-leader)' : 'var(--viz-gossip-update)';
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
  const maxSends = 1; // each node gossips each update once (to gossipFanout peers), matching Java
  const entries = [...node.gossipQueue]
    .sort((a, b) => a.sendCount - b.sendCount)
    .slice(0, 8);
  entries.forEach(e => e.sendCount++);
  node.gossipQueue = node.gossipQueue.filter(e => e.sendCount < maxSends);
  // Only gossip when there is something to propagate — matches real SWIM behavior
  if (entries.length > 0) {
    const targets = selectGossipTargets(sim, node);
    targets.forEach(tid => sendGossip(sim, node.id, tid, entries, wallTime));
  }
  sim.eventQueue.push({ type: 'GOSSIP_TICK', simTime: sim.simTime + sim.params.gossipInterval, nodeId: node.id });
}

function handleGossipReceived(sim, _from, to, entries, wallTime = 0) {
  const node = sim.nodes.get(to);
  if (!node || node.state === 'crashed') return;
  for (const entry of entries) {
    if (entry.memberId === to) {
      if (entry.state === 'suspect' && node.state === 'alive') {
        node.incarnationNumber++;
        logEvent(sim, `N${to} (self): incarnation → ${node.incarnationNumber} (refuting suspect via gossip)`);
        const correction = { memberId: to, state: 'alive', incarnation: node.incarnationNumber };
        enqueueGossipUpdate(node, correction);
        if (sim.params.broadcastDisputes) {
          [...sim.nodes.values()]
            .filter(n => n.id !== to && n.state === 'alive' && !isPartitioned(sim, to, n.id))
            .forEach(n => sendGossip(sim, to, n.id, [correction], wallTime));
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
      const prevState = view.state;
      view.state = entry.state;
      view.incarnation = entry.incarnation;
      if (entry.propertyVersion !== undefined) view.propertyVersion = entry.propertyVersion;
      enqueueGossipUpdate(node, entry);
      // Log the Java-equivalent event this state change would fire
      if (acceptNew && entry.state !== prevState) {
        if (entry.state === 'suspect') {
          logEvent(sim, `N${to} → N${entry.memberId}: REACHABILITY_CHANGED (suspect, via gossip)`);
        } else if (entry.state === 'dead') {
          logEvent(sim, `N${to} → N${entry.memberId}: REACHABILITY_CHANGED (dead) + MEMBER_REMOVED (via gossip)`);
        } else if (entry.state === 'alive' && prevState === 'dead') {
          // Member was removed when dead; re-discovering it as alive fires MEMBER_ADDED per peer
          logEvent(sim, `N${to} → N${entry.memberId}: MEMBER_ADDED (rediscovered via gossip)`);
        } else if (entry.state === 'alive' && prevState === 'suspect') {
          logEvent(sim, `N${to} → N${entry.memberId}: REACHABILITY_CHANGED (alive, via gossip)`);
        }
      } else if (propChanged) {
        logEvent(sim, `N${to} → N${entry.memberId}: METADATA_CHANGED (v${entry.propertyVersion})`);
      }
    }
  }
}

// ── Failure detection ─────────────────────────────────────────────────────────

function sendProbe(sim, from, to, wallTime) {
  // No partition check here — let the probe animate even if it will be dropped at delivery,
  // so the prober's attempt is visible and the PROBE_TIMEOUT can fire to trigger failure detection.
  sim.msgCount++;
  sim.inFlightMessages.push({
    id: sim.nextMsgId++, from, to, label: 'P→', color: 'var(--viz-msg-probe)',
    startWallTime: wallTime, durationMs: 300, replyDepth: 0,
    payload: { type: 'PROBE' },
  });
  // Include prober's current view of the target so the target can detect if it's suspected
  const probersView = sim.nodes.get(from)?.membershipView.get(to);
  sim.eventQueue.push({ type: 'MSG_DELIVER', simTime: sim.simTime + 1, from, to,
    payload: { type: 'PROBE', probeSentWallTime: wallTime, replyDepth: 0,
      probersViewOfTarget: probersView?.state } });
}

function handleProbeTick(sim, node, wallTime) {
  // Probe next peer via shuffle + round-robin through probeOrder (includes suspects, excludes dead)
  if (node.probeOrder.length > 0) {
    const idx = node.probeCounter % node.probeOrder.length;
    node.probeCounter++;
    const targetId = node.probeOrder[idx];
    // Use local membership view, not ground truth — faithful to the real protocol
    // where a node only learns about failures through timeouts, not omniscience
    const localView = node.membershipView.get(targetId);
    if (localView && localView.state !== 'dead') {
      sendProbe(sim, node.id, targetId, wallTime);
      sim.eventQueue.push({
        type: 'PROBE_TIMEOUT', simTime: sim.simTime + sim.params.probeTimeout,
        from: node.id, to: targetId, probeSentSimTime: sim.simTime, probeSentWallTime: wallTime,
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
  const { from, to, probeSentSimTime, probeSentWallTime } = event;
  const prober = sim.nodes.get(from);
  if (!prober || prober.state === 'crashed') return;
  // ACK received after probe was sent — cancel this timeout
  if ((prober.lastAckFrom.get(to) ?? -Infinity) >= probeSentSimTime) return;
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
      id: sim.nextMsgId++, from, to: pid, label: 'I→', color: 'var(--viz-msg-probe-indirect)',
      // replyDepth=1: I→ starts when P→ has visually arrived.
      // Only inherit probeSentWallTime if the probe is still fresh (< 800ms ago in wall time);
      // if stale (e.g. sim was playing then stepped much later), start from now instead.
      startWallTime: (wallTime - (probeSentWallTime ?? wallTime) < 800)
        ? (probeSentWallTime ?? wallTime)
        : wallTime,
      durationMs: 300, replyDepth: 1,
      payload: { type: 'PROBE_REQUEST', suspect: to, coordinator: from },
    });
    sim.eventQueue.push({
      type: 'MSG_DELIVER', simTime: sim.simTime + 1, from, to: pid,
      // Pass wallTime so the forwarded P→ starts animating only after I→ visually arrives
      payload: { type: 'PROBE_REQUEST', suspect: to, coordinator: from,
        indirectSentWallTime: (wallTime - (probeSentWallTime ?? wallTime) < 800)
          ? (probeSentWallTime ?? wallTime)
          : wallTime },
    });
  });

  sim.eventQueue.push({
    type: 'INDIRECT_TIMEOUT', simTime: sim.simTime + sim.params.probeTimeout * 2,
    from, to, probeSentSimTime: sim.simTime,
  });
}

function handleIndirectTimeout(sim, event, wallTime) {
  const { from, to, probeSentSimTime } = event;
  const prober = sim.nodes.get(from);
  if (!prober || prober.state === 'crashed') return;
  // Any indirect prober reported success — cancel
  if ((prober.lastAckFrom.get(to) ?? -Infinity) >= probeSentSimTime) return;
  markSuspect(sim, prober, to, wallTime);
}

function markSuspect(sim, prober, targetId, wallTime) {
  const view = prober.membershipView.get(targetId);
  if (!view || view.state !== 'alive') return;

  view.state = 'suspect';
  prober.suspectSince.set(targetId, sim.simTime);
  logEvent(sim, `N${prober.id} → N${targetId}: REACHABILITY_CHANGED (suspect)`);

  const entry = { memberId: targetId, state: 'suspect', incarnation: view.incarnation };
  enqueueGossipUpdate(prober, entry);

  // broadcastUpdates: prober immediately floods failure news to all peers (not just fanout)
  if (sim.params.broadcastUpdates) {
    [...sim.nodes.values()]
      .filter(n => n.id !== prober.id && n.state === 'alive' && !isPartitioned(sim, prober.id, n.id))
      .forEach(n => sendGossip(sim, prober.id, n.id, [entry], wallTime));
  }

  // notifySuspect: send the suspect state directly to the suspect so it can refute
  // In Java: gossip(swimMember, [swimMember.copy()]) — a direct unicast carrying suspect state
  if (sim.params.notifySuspect) {
    sendGossip(sim, prober.id, targetId, [entry], wallTime);
  }

  sim.eventQueue.push({
    type: 'FAILURE_TIMEOUT', simTime: sim.simTime + sim.params.failureTimeout,
    from: prober.id, to: targetId,
  });
}

function handleFailureTimeout(sim, event, wallTime) {
  const { from, to } = event;
  const prober = sim.nodes.get(from);
  if (!prober || prober.state === 'crashed') return;
  const view = prober.membershipView.get(to);
  if (!view || view.state !== 'suspect') return;

  view.state = 'dead';
  logEvent(sim, `N${from} → N${to}: REACHABILITY_CHANGED (dead) + MEMBER_REMOVED`);

  prober.probeOrder = prober.probeOrder.filter(id => id !== to);
  shuffle(prober.probeOrder);
  if (!prober.deadNodes.includes(to)) prober.deadNodes.push(to);

  const entry = { memberId: to, state: 'dead', incarnation: view.incarnation };
  enqueueGossipUpdate(prober, entry);

  // broadcastUpdates: immediately flood the dead news to all peers
  if (sim.params.broadcastUpdates) {
    [...sim.nodes.values()]
      .filter(n => n.id !== from && n.state === 'alive' && !isPartitioned(sim, from, n.id))
      .forEach(n => sendGossip(sim, from, n.id, [entry], wallTime));
  }
}

function handleProbeReceived(sim, event, wallTime) {
  const { from, to } = event;
  // `from` = prober, `to` = us (the probed node)
  const target = sim.nodes.get(to);
  if (!target || target.state === 'crashed' || isPartitioned(sim, to, from)) return;

  // broadcastDisputes: if the probe reveals the prober suspects us, refute by bumping incarnation
  // and broadcasting our alive state to all peers (Java: probe handler lines 629-635)
  const probersViewOfUs = event.payload?.probersViewOfTarget;
  if (probersViewOfUs === 'suspect') {
    target.incarnationNumber++;
    logEvent(sim, `N${to} (self): incarnation → ${target.incarnationNumber} (refuting suspect via probe)`);
    const refutation = { memberId: to, state: 'alive', incarnation: target.incarnationNumber };
    enqueueGossipUpdate(target, refutation);
    if (sim.params.broadcastDisputes) {
      [...sim.nodes.values()]
        .filter(n => n.id !== to && n.state === 'alive' && !isPartitioned(sim, to, n.id))
        .forEach(n => sendGossip(sim, to, n.id, [refutation], wallTime));
      logEvent(sim, `N${to} (self): REACHABILITY_CHANGED (alive) + broadcastDisputes`);
    }
  }

  sim.msgCount++;
  // isReply: true tells captureSnapshot to start this animation only after P→ has visually arrived
  const probeSentWallTime = event.payload?.probeSentWallTime ?? wallTime;
  const ackDepth = (event.payload?.replyDepth ?? 0) + 1;
  sim.inFlightMessages.push({
    id: sim.nextMsgId++, from: to, to: from, label: 'P✓', color: 'var(--viz-msg-probe-ack)',
    startWallTime: probeSentWallTime, durationMs: 300, replyDepth: ackDepth,
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
  const target = pick(node.probeOrder);
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
    if (entry.memberId !== to) handleGossipReceived(sim, from, to, [entry], wallTime);
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
    case 'GOSSIP': handleGossipReceived(sim, from, to, payload.entries, wallTime); break;
    case 'PROBE':  handleProbeReceived(sim, event, wallTime); break;
    case 'PROBE_ACK': {
      // `from` = node that ACKed (was probed), `to` = prober (direct or indirect)
      const prober = sim.nodes.get(to);
      if (!prober) break;

      // Record that `from` responded; cancels pending PROBE/INDIRECT timeouts
      prober.lastAckFrom.set(from, sim.simTime);

      // If we were acting as indirect prober for a coordinator, update coordinator too
      const coordId = prober.pendingIndirectFor.get(from);
      if (coordId !== undefined) {
        prober.pendingIndirectFor.delete(from);
        const coord = sim.nodes.get(coordId);
        if (coord && coord.state !== 'crashed') coord.lastAckFrom.set(from, sim.simTime);
      }

      // Resurrection check
      const deadIdx = prober.deadNodes.indexOf(from);
      if (deadIdx !== -1) {
        prober.deadNodes.splice(deadIdx, 1);
        const newIncarnation = (sim.nodes.get(from)?.incarnationNumber ?? 0) + 1;
        prober.membershipView.set(from, { state: 'alive', incarnation: newIncarnation, propertyVersion: 0 });
        if (!prober.probeOrder.includes(from)) { prober.probeOrder.push(from); shuffle(prober.probeOrder); }
        logEvent(sim, `N${to} → N${from}: MEMBER_REMOVED + MEMBER_ADDED (resurrected, incarnation ${newIncarnation})`);
        enqueueGossipUpdate(prober, { memberId: from, state: 'alive', incarnation: newIncarnation });
      } else {
        // Clear suspicion if already marked
        const view = prober.membershipView.get(from);
        if (view && view.state === 'suspect') {
          view.state = 'alive';
          prober.suspectSince.delete(from);
          logEvent(sim, `N${to} → N${from}: REACHABILITY_CHANGED (alive, refuted suspicion)`);
        }
      }
      break;
    }
    case 'PROBE_REQUEST': {
      // We are an indirect prober — probe `suspect` on behalf of `coordinator`
      const { suspect, coordinator, indirectSentWallTime } = payload;
      if (isPartitioned(sim, to, suspect)) break;
      // Track who we're probing for so we can forward the ACK result
      const indirectProber = sim.nodes.get(to);
      if (indirectProber) indirectProber.pendingIndirectFor.set(suspect, coordinator);
      sim.msgCount++;
      const baseWallTime = indirectSentWallTime ?? wallTime;
      sim.inFlightMessages.push({
        id: sim.nextMsgId++, from: to, to: suspect, label: 'P→', color: 'var(--viz-msg-probe)',
        // replyDepth 2: P→ is two hops from original — starts when I→ has visually arrived at N2
        startWallTime: baseWallTime, durationMs: 300, replyDepth: 2,
        payload: { type: 'PROBE' },
      });
      sim.eventQueue.push({
        type: 'MSG_DELIVER', simTime: sim.simTime + 1, from: to, to: suspect,
        // replyDepth 2 so P✓ gets ackDepth=3, starting after this forwarded P→ visually arrives
        payload: { type: 'PROBE', probeSentWallTime: baseWallTime, replyDepth: 2 },
      });
      break;
    }
    case 'SYNC':          handleSyncReceived(sim, from, to, payload.view, wallTime); break;
    case 'SYNC_RESPONSE': {
      for (const entry of payload.view) handleGossipReceived(sim, from, to, [entry], wallTime);
      break;
    }
  }
}

// ── Convergence ───────────────────────────────────────────────────────────────

function checkConvergence(sim) {
  if (sim.faultInjectedAt === null) return;
  const aliveNodes = [...sim.nodes.values()].filter(n => n.state === 'alive');
  if (aliveNodes.length < 2) return;

  // consistent: alive nodes agree with each other — drives diverge/converge detection.
  // settled:    for crashed nodes, all alive nodes also see them as 'dead' (not just consistent).
  //             "all agree the node is suspect" is consistent but not yet settled.
  let consistent = true;
  let settled = true;

  outer: for (const nodeX of sim.nodes.values()) {
    if (nodeX.state === 'alive') {
      // Every alive peer must see nodeX as alive with the current propertyVersion
      for (const nodeY of aliveNodes) {
        if (nodeY.id === nodeX.id) continue;
        const v = nodeY.membershipView.get(nodeX.id);
        if (!v || v.state !== 'alive' || (v.propertyVersion ?? 0) !== nodeX.propertyVersion) {
          consistent = false;
          settled = false;
          break outer;
        }
      }
    } else if (nodeX.state === 'crashed') {
      // Crashed nodes are no longer part of the cluster — don't compare against ground truth.
      // Instead check that alive nodes agree WITH EACH OTHER about this node.
      const ref = aliveNodes[0].membershipView.get(nodeX.id);
      if (!ref) continue;
      for (const nodeY of aliveNodes.slice(1)) {
        const v = nodeY.membershipView.get(nodeX.id);
        if (!v || v.state !== ref.state) { consistent = false; settled = false; break outer; }
      }
      // Consistent but still intermediate if peers haven't marked it dead yet
      if (ref.state !== 'dead') settled = false;
    }
  }

  if (!consistent) {
    if (!sim.hasDiverged) {
      sim.hasDiverged = true;
      sim.firstDetectTime = sim.simTime - sim.faultInjectedAt;
      logEvent(sim, 'Cluster views diverged');
    }
    sim.convergeTime = null;
    return;
  }

  // Consistent — converge only once views are also settled (crashed nodes seen as dead)
  if (sim.hasDiverged && settled && sim.convergeTime === null) {
    sim.convergeTime = sim.simTime - sim.faultInjectedAt;
    logEvent(sim, 'Cluster converged (all nodes agree)');
  }
}

// ── Fault injection ───────────────────────────────────────────────────────────

export function crashNode(sim, nodeId) {
  const node = sim.nodes.get(nodeId);
  if (!node || node.state === 'crashed') return;
  node.state = 'crashed';
  sim.eventQueue.removeWhere(e => e.nodeId === nodeId || e.from === nodeId);
  sim.inFlightMessages = sim.inFlightMessages.filter(m => m.from !== nodeId && m.to !== nodeId);
  sim.nodes.forEach(n => {
    // Keep crashed node in probeOrder so peers still probe it and PROBE_TIMEOUT fires
    // (failure detection requires seeing no ACK, not skipping the probe entirely)
    n.deadNodes = n.deadNodes.filter(id => id !== nodeId);
  });
  sim.faultInjectedAt = sim.simTime;
  sim.hasDiverged = false;
  sim.firstDetectTime = null;
  sim.convergeTime = null;
  logEvent(sim, `N${nodeId} crashed (fault injected)`);
}

export function restoreNode(sim, nodeId) {
  const node = sim.nodes.get(nodeId);
  if (!node || node.state !== 'crashed') return;
  node.state = 'alive';
  sim.nodes.forEach(n => {
    if (n.id !== nodeId && n.state === 'alive' && !n.probeOrder.includes(nodeId)) {
      n.probeOrder.push(nodeId);
    }
  });
  node.incarnationNumber++;
  node.suspectSince.clear();
  sim.eventQueue.push({ type: 'GOSSIP_TICK', simTime: sim.simTime + sim.params.gossipInterval, nodeId });
  sim.eventQueue.push({ type: 'PROBE_TICK',  simTime: sim.simTime + sim.params.probeInterval,  nodeId });
  sim.eventQueue.push({ type: 'SYNC_TICK',   simTime: sim.simTime + sim.params.syncInterval,   nodeId });
  // No explicit gossip on restore — matches Java join() behaviour.
  // The restored node will probe peers on its next PROBE_TICK; other nodes probe it via
  // their probeOrder or deadNodes resurrection check. Discovery propagates through probing.
  logEvent(sim, `N${nodeId} (self): MEMBER_ADDED (restored, incarnation ${node.incarnationNumber})`);
}

export function injectPropertyUpdate(sim, nodeId) {
  const node = sim.nodes.get(nodeId);
  if (!node || node.state !== 'alive') return;
  node.propertyVersion++;
  enqueueGossipUpdate(node, {
    memberId: nodeId, state: 'alive', incarnation: node.incarnationNumber,
    propertyVersion: node.propertyVersion,
  });
  sim.faultInjectedAt = sim.simTime;
  sim.hasDiverged = false;
  sim.firstDetectTime = null;
  sim.convergeTime = null;
  logEvent(sim, `N${nodeId} property update v${node.propertyVersion}`);
}

export function addPartition(sim, from, to) {
  sim.partitions.add(`${from}-${to}`);
  sim.partitions.add(`${to}-${from}`);
  logEvent(sim, `Link fault: N${from} ↔ N${to} blocked`);
}

export function logEventDirect(sim, text) {
  logEvent(sim, text);
}

export function setGossipExcluded(sim, nodeId, excluded) {
  const node = sim.nodes.get(nodeId);
  if (node) {
    node.gossipExcluded = excluded;
    logEvent(sim, `N${nodeId} gossip ${excluded ? 'excluded' : 'restored'}`);
  }
}

// ── Main simulation loop ──────────────────────────────────────────────────────

export function advanceSim(sim, targetSimTime, wallTime, uiSpeed = 1) {
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
      case 'FAILURE_TIMEOUT':  handleFailureTimeout(sim, event, wallTime); break;
      case 'SYNC_TICK':        if (node) handleSyncTick(sim, node, wallTime); break;
      case 'MSG_DELIVER':      handleMsgDeliver(sim, event, wallTime); break;
    }
    checkConvergence(sim);
  }
  sim.simTime = targetSimTime;
  const eff = (m) => Math.min(800, m.durationMs / uiSpeed);
  sim.inFlightMessages = sim.inFlightMessages.filter(m =>
    m.startWallTime > 0 && wallTime - m.startWallTime < eff(m) * ((m.replyDepth ?? 0) + 1) + 100
  );
}

// ── Snapshot (for React rendering) ───────────────────────────────────────────

export function captureSnapshot(sim, wallTime, uiSpeed = 1) {
  const animDuration = (m) => Math.min(800, m.durationMs / uiSpeed);
  return {
    simTime: sim.simTime,
    roundCount: sim.roundCount,
    msgCount: sim.msgCount,
    firstDetectTime: sim.firstDetectTime,
    convergeTime: sim.convergeTime,
    hasPartitions: sim.partitions.size > 0,
    hasDiverged: sim.hasDiverged,
    partitions: [...sim.partitions],
    eventLog: [...sim.eventLog],
    nodes: [...sim.nodes.values()].map(n => ({
      id: n.id,
      state: n.state,
      propertyVersion: n.propertyVersion,
      incarnationNumber: n.incarnationNumber,
      gossipExcluded: n.gossipExcluded,
      view: new Map([...n.membershipView.entries()]),
    })),
    messages: sim.inFlightMessages.map(m => {
      const eff = animDuration(m);
      const actualStart = m.startWallTime + (m.replyDepth ?? 0) * eff;
      const progress = Math.min(1, (wallTime - actualStart) / eff);
      return { ...m, progress };
    }).filter(m => m.progress >= 0 && m.progress <= 1),
  };
}
