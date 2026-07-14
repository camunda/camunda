import React, { useState, useEffect, useRef, useCallback } from 'react';
import styles from './SwimMembershipSimulator.module.css';
import {
  createSim, advanceSim, captureSnapshot,
  crashNode, restoreNode, injectPropertyUpdate,
  addPartition, setGossipExcluded, logEventDirect,
} from './swimSimEngine';

// ── SVG node graph ────────────────────────────────────────────────────────────

function graphMetrics(svgWidth) {
  const mobile = svgWidth > 0 && svgWidth < 500;
  return {
    graphH: mobile ? 260 : 380,
    nodeR:  mobile ? 14  : 22,
  };
}

function nodePositions(count, width, graphH) {
  const cx = width / 2;
  const cy = graphH / 2;
  // Slightly tighter radius on mobile (higher factor) to fill the smaller canvas
  const r = Math.min(cx, cy) * (count <= 4 ? 0.58 : count <= 8 ? 0.68 : 0.76);
  return Array.from({ length: count }, (_, i) => {
    const angle = (2 * Math.PI * i / count) - Math.PI / 2;
    return {
      x: cx + r * Math.cos(angle),
      y: cy + r * Math.sin(angle),
      ux: Math.cos(angle),
      uy: Math.sin(angle),
    };
  });
}

function nodeColor(state) {
  switch (state) {
    case 'suspect': return 'var(--viz-suspect-light)';
    case 'dead':    return 'var(--viz-leader)';
    case 'crashed': return 'var(--ifm-color-emphasis-300)';
    default:        return 'var(--ifm-color-emphasis-100)';
  }
}

function nodeBorderColor(state) {
  switch (state) {
    case 'suspect': return 'var(--viz-suspect)';
    case 'dead':    return 'var(--viz-leader)';
    case 'crashed': return 'var(--ifm-color-emphasis-400)';
    default:        return 'var(--ifm-color-emphasis-600)';
  }
}

const LEGEND_ENTRIES = [
  ['P→',  'var(--viz-msg-probe)',          'Probe',                         'Direct probe sent to a peer to check it is alive'],
  ['P✓',  'var(--viz-msg-probe-ack)',      'Probe ACK',                     'Acknowledgement sent back by the probed node — confirms it is reachable'],
  ['I→',  'var(--viz-msg-probe-indirect)', 'Indirect probe',                'Probe request forwarded to another node — sent when a direct probe times out, to rule out false positives'],
  ['S⇄',  'var(--viz-msg-sync)',           'Sync',                          'Full membership state exchange with a random peer — anti-entropy mechanism that catches nodes which missed gossip'],
  ['U↑',  'var(--viz-gossip-update)',      'Update',                        'Gossip message carrying a non-failure update — property change (e.g. BrokerInfo) or alive-state refutation after an incarnation bump'],
  ['U⚠',  'var(--viz-leader)',             'Update (failure news: suspect/dead)', 'Gossip message carrying failure news — a node has been marked suspect or dead'],
];

function NodeGraph({ snapshot, svgWidth }) {
  const nodeCount = snapshot?.nodes.length ?? 0;
  const { graphH, nodeR } = graphMetrics(svgWidth);
  const positions = nodePositions(nodeCount, svgWidth, graphH);

  return (
    <svg width={svgWidth} height={graphH} className={styles.graph}>

      {/* Normal edges */}
      {Array.from({ length: nodeCount }, (_, i) =>
        Array.from({ length: nodeCount }, (_, j) => {
          if (j <= i) return null;
          const blocked = snapshot?.partitions.includes(`${i}-${j}`) || snapshot?.partitions.includes(`${j}-${i}`);
          if (blocked) return null;
          return (
            <line key={`e${i}-${j}`}
              x1={positions[i].x} y1={positions[i].y}
              x2={positions[j].x} y2={positions[j].y}
              stroke="var(--ifm-color-emphasis-200)" strokeWidth="1"
            />
          );
        })
      )}

      {/* Partition indicators — deduplicated (a<b) since faults are symmetric */}
      {snapshot?.partitions
        .filter(p => { const [a, b] = p.split('-').map(Number); return a < b; })
        .map(p => {
          const [a, b] = p.split('-').map(Number);
          if (a >= nodeCount || b >= nodeCount) return null;
          return (
            <line key={`p-${p}`}
              x1={positions[a].x} y1={positions[a].y}
              x2={positions[b].x} y2={positions[b].y}
              stroke="var(--viz-leader)" strokeWidth="2" strokeDasharray="5,4" opacity="0.8"
            />
          );
        })}

      {/* In-flight message circles */}
      {snapshot?.messages.map(m => {
        if (m.from >= nodeCount || m.to >= nodeCount) return null;
        const p1 = positions[m.from];
        const p2 = positions[m.to];
        const x = p1.x + (p2.x - p1.x) * m.progress;
        const y = p1.y + (p2.y - p1.y) * m.progress;
        return (
          <g key={m.id} transform={`translate(${x},${y})`}>
            <circle r="10" fill={m.color} opacity={0.9} />
            <text textAnchor="middle" dominantBaseline="central"
              fontSize="6.5" fontWeight="700" fill="white">{m.label}</text>
          </g>
        );
      })}

      {/* Nodes */}
      {snapshot?.nodes.map(n => {
        const pos = positions[n.id];
        return (
          <g key={n.id} transform={`translate(${pos.x},${pos.y})`}>
            {n.gossipExcluded && (
              <circle r={nodeR + 5} fill="none"
                stroke="var(--viz-gossip-exclude)" strokeWidth="2" strokeDasharray="4,3" />
            )}
            <circle r={nodeR}
              fill={nodeColor(n.state)}
              stroke={nodeBorderColor(n.state)}
              strokeWidth="2"
            />
            <text textAnchor="middle" y="-3"
              fontSize="11" fontWeight="700"
              fill={n.state === 'dead' ? 'white' : 'var(--ifm-color-emphasis-900)'}
              textDecoration={n.state === 'crashed' ? 'line-through' : 'none'}>
              N{n.id}
            </text>
            {n.propertyVersion > 0 && (
              <text textAnchor="middle" y="10"
                fontSize="8"
                fill={n.state === 'dead' ? 'white' : 'var(--ifm-color-emphasis-600)'}>
                v{n.propertyVersion}
              </text>
            )}
            {n.incarnationNumber > 0 && (
              <g transform={`translate(${Math.round(pos.ux * (nodeR + 10))},${Math.round(pos.uy * (nodeR + 10))})`}>
                <title>Incarnation {n.incarnationNumber} — bumps each time this node refutes a suspicion or restarts. Peers with a lower incarnation number will accept this node&apos;s alive state as more recent.</title>
                <circle r="9" fill="var(--ifm-background-surface-color)"
                  stroke="var(--ifm-color-emphasis-400)" strokeWidth="1.5" />
                <text textAnchor="middle" dominantBaseline="central"
                  fontSize="8" fontWeight="700" fill="var(--ifm-color-emphasis-700)">
                  {n.incarnationNumber}
                </text>
              </g>
            )}
          </g>
        );
      })}

    </svg>
  );
}

function Legend() {
  return (
    <div className={styles.legend}>
      {LEGEND_ENTRIES.map(([label, color, desc, tip]) => (
        <div key={label} className={styles.legendItem} title={tip}>
          <span className={styles.legendDot} style={{ background: color }}>{label}</span>
          <span className={styles.legendDesc}>{desc}</span>
        </div>
      ))}
      <div className={styles.legendSep} />
      <div className={styles.legendItem}
        title="Incarnation number — each node's monotonically increasing version counter. Bumps when a node refutes a suspicion or restarts. Peers use it to decide which state is more recent: higher incarnation wins.">
        <span className={styles.legendBadge}>3</span>
        <span className={styles.legendDesc}>Incarnation (shown when &gt; 0)</span>
      </div>
    </div>
  );
}

// ── Default parameters (match SwimMembershipProtocolConfig.java defaults) ─────

const DEFAULT_PARAMS = {
  gossipInterval: 250,
  gossipFanout: 2,
  broadcastUpdates: false,
  probeInterval: 1000,
  probeTimeout: 100,
  suspectProbes: 3,
  failureTimeout: 3000,
  broadcastDisputes: true,
  notifySuspect: false,
  syncInterval: 10000,
};

const SPEEDS = [0.25, 0.5, 1, 2];
const STEP_SIZES = [{ label: '50ms', ms: 50 }, { label: '250ms', ms: 250 }, { label: '1s', ms: 1000 }, { label: '5s', ms: 5000 }];

// ── Main component ────────────────────────────────────────────────────────────

export default function SwimMembershipSimulator() {
  const [nodeCount, setNodeCount] = useState(3);
  const [params, setParams] = useState(DEFAULT_PARAMS);
  const [paused, setPaused] = useState(true);
  const [speed, setSpeed] = useState(0.25);
  const [stepSize, setStepSize] = useState(1000);
  const isMobile = typeof window !== 'undefined' && window.innerWidth < 768;
  const [paramsOpen, setParamsOpen] = useState(!isMobile);
  const [eventLogOpen, setEventLogOpen] = useState(!isMobile);
  const [snapshot, setSnapshot] = useState(null);
  const [crashTarget, setCrashTarget] = useState(0);
  const [propTarget, setPropTarget]   = useState(0);
  const [partFrom, setPartFrom]           = useState(0);
  const [partTo, setPartTo]               = useState(1);
  const [excludeTarget, setExcludeTarget] = useState(0);

  const simRef    = useRef(null);
  const paramsRef = useRef(params);
  const rafRef    = useRef(null);
  const svgRef    = useRef(null);
  const [svgWidth, setSvgWidth] = useState(600);

  useEffect(() => {
    if (!svgRef.current) return;
    const ro = new ResizeObserver(([entry]) => setSvgWidth(entry.contentRect.width));
    ro.observe(svgRef.current);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    simRef.current = createSim(nodeCount, paramsRef.current);
    setSnapshot(captureSnapshot(simRef.current, performance.now(), speed));
  }, [nodeCount]);

  useEffect(() => {
    const prevParams = paramsRef.current;
    paramsRef.current = params;
    const sim = simRef.current;
    if (!sim) return;
    sim.params = params;
    // When an interval changes, cancel stale pending tick events and reschedule
    // from now — otherwise the old scheduled time would still be used
    const reschedule = (type, interval, prevInterval) => {
      if (interval === prevInterval) return;
      sim.eventQueue.removeWhere(e => e.type === type);
      sim.nodes.forEach((node, id) => {
        if (node.state !== 'crashed') {
          sim.eventQueue.push({ type, simTime: sim.simTime + interval, nodeId: id });
        }
      });
    };
    reschedule('GOSSIP_TICK', params.gossipInterval, prevParams.gossipInterval);
    reschedule('PROBE_TICK',  params.probeInterval,  prevParams.probeInterval);
    reschedule('SYNC_TICK',   params.syncInterval,   prevParams.syncInterval);
  }, [params]);

  useEffect(() => {
    const tick = (wallTime) => {
      const sim = simRef.current;
      if (sim) {
        if (!paused) {
          advanceSim(sim, sim.simTime + 10 * speed, wallTime, speed);
          setSnapshot(captureSnapshot(sim, wallTime, speed));
        } else {
          const eff = Math.min(800, 250 / speed);
          const hasAnimating = sim.inFlightMessages.some(m => {
            if (m.startWallTime <= 0) return false;
            const totalDuration = ((m.replyDepth ?? 0) + 1) * eff;
            return wallTime - m.startWallTime < totalDuration;
          });
          if (hasAnimating) setSnapshot(captureSnapshot(sim, wallTime, speed));
        }
      }
      rafRef.current = requestAnimationFrame(tick);
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, [paused, speed]);

  const stepSizeRef = useRef(stepSize);
  useEffect(() => { stepSizeRef.current = stepSize; }, [stepSize]);

  const step = useCallback(() => {
    if (!simRef.current) return;
    advanceSim(simRef.current, simRef.current.simTime + stepSizeRef.current, performance.now());
    setSnapshot(captureSnapshot(simRef.current, performance.now(), speed));
  }, [speed]);

  const reset = useCallback(() => {
    simRef.current = createSim(nodeCount, paramsRef.current);
    setSnapshot(captureSnapshot(simRef.current, performance.now(), speed));
  }, [nodeCount, speed]);

  const nodeIds = Array.from({ length: nodeCount }, (_, i) => i);

  if (!snapshot) return null;

  const { simTime, roundCount, msgCount, firstDetectTime, convergeTime, hasPartitions, hasDiverged } = snapshot;

  return (
    <div className={styles.widget}>
      {/* Parameters panel */}
      <div className={styles.paramsPanel}>
        <div className={styles.paramsPanelHeader} onClick={() => setParamsOpen(o => !o)}>
          {paramsOpen ? '▾' : '▸'} Parameters
          <span title="Hover any parameter name for details" style={{ marginLeft: 6, fontSize: '0.7rem', opacity: 0.6, cursor: 'help' }}>ⓘ</span>
        </div>
        {paramsOpen && (
          <div className={styles.paramsBody}>
            <div className={styles.paramGroup}>
              <div className={styles.paramGroupLabel}>Cluster</div>
              <div className={styles.paramRow}>
                <span className={styles.paramLabel} title="Number of nodes in the simulated cluster — resets the simulation">Node count</span>
                <div className={styles.paramRowControls}>
                  <input type="range" className={styles.paramSlider}
                    min={2} max={9} step={1} value={nodeCount}
                    onChange={e => setNodeCount(Number(e.target.value))} />
                  <span className={styles.paramVal}>{nodeCount}</span>
                </div>
              </div>
              <div className={styles.paramRow}>
                <span className={styles.paramLabel} title="Full membership state exchange with a random peer — anti-entropy safety net that catches nodes which missed gossip (e.g. after a partition heals)">Sync interval (ms)</span>
                <div className={styles.paramRowControls}>
                  <input type="range" className={styles.paramSlider}
                    min={1000} max={60000} step={1000} value={params.syncInterval}
                    onChange={e => setParams(p => ({ ...p, syncInterval: Number(e.target.value) }))} />
                  <span className={styles.paramVal}>{params.syncInterval}</span>
                </div>
              </div>
            </div>

            <div className={styles.paramGroup}>
              <div className={styles.paramGroupLabel}>Gossip</div>
              {[
                ['Interval (ms)', 'gossipInterval', 50, 2000, 50, 'How often each node sends pending membership updates to random peers'],
                ['Fanout', 'gossipFanout', 1, nodeCount - 1, 1, 'Number of random peers that receive gossip each interval — higher fanout spreads news faster but increases traffic'],
              ].map(([label, key, min, max, s, tip]) => (
                <div key={key} className={styles.paramRow}>
                  <span className={styles.paramLabel} title={tip}>{label}</span>
                  <div className={styles.paramRowControls}>
                    <input type="range" className={styles.paramSlider}
                      min={min} max={max} step={s} value={params[key]}
                      onChange={e => setParams(p => ({ ...p, [key]: Number(e.target.value) }))} />
                    <span className={styles.paramVal}>{params[key]}</span>
                  </div>
                </div>
              ))}
              {[['Broadcast updates', 'broadcastUpdates', 'Send ANY membership update (property changes, suspect, dead) to ALL peers immediately instead of spreading via fanout gossip — faster propagation, higher traffic']].map(([label, key, tip]) => (
                <div key={key} className={styles.paramRow}>
                  <div className={styles.paramRowControls}>
                    <span className={styles.paramLabel} title={tip}>{label}</span>
                    <label className={styles.toggle}>
                      <input type="checkbox" checked={params[key]}
                        onChange={e => setParams(p => ({ ...p, [key]: e.target.checked }))} />
                      <div className={styles.toggleTrack} />
                      <div className={styles.toggleKnob} />
                    </label>
                  </div>
                </div>
              ))}
            </div>

            <div className={styles.paramGroupWide}>
              <div className={styles.paramGroupLabel}>Failure Detection</div>
              <div className={styles.paramGroupInner}>
                {[
                  ['Probe interval (ms)', 'probeInterval', 100, 5000, 100, 'How often each node pings a random peer to check it is alive — lower = faster detection, more traffic'],
                  ['Probe timeout (ms)',  'probeTimeout',  50,  2000, 50,  'How long to wait for a probe ACK before triggering indirect probes — lower = faster but more false positives on slow networks'],
                  ['Suspect probes',      'suspectProbes', 1,   10,   1,   'Upper bound on total probers: 1 direct + up to (N-1) indirect. All must fail before the node is suspected. Fewer indirect probes are used when not enough alive peers are available — e.g. with 3 nodes and suspectProbes=3, only 1 indirect prober exists so suspicion requires 2 failures, not 3'],
                  ['Failure timeout (ms)','failureTimeout',1000,30000,1000,'How long a suspected node can stay unresponsive before being declared dead — gives it time to refute'],
                ].map(([label, key, min, max, s, tip]) => (
                  <div key={key} className={styles.paramRow} title={tip}>
                    <span className={styles.paramLabel}>{label}</span>
                    <div className={styles.paramRowControls}>
                      <input type="range" className={styles.paramSlider}
                        min={min} max={max} step={s} value={params[key]}
                        onChange={e => setParams(p => ({ ...p, [key]: Number(e.target.value) }))} />
                      <span className={styles.paramVal}>{params[key]}</span>
                    </div>
                  </div>
                ))}
                {[
                  ['Broadcast disputes', 'broadcastDisputes', 'When a node learns via a probe that it is being suspected, it immediately broadcasts its own alive state (with bumped incarnation) to ALL peers to refute the suspicion'],
                  ['Notify suspect',     'notifySuspect',     'Send the suspect state directly to the suspected node so it knows it is being suspected and can refute immediately'],
                ].map(([label, key, tip]) => (
                  <div key={key} className={styles.paramRow} title={tip}>
                    <div className={styles.paramRowControls}>
                      <span className={styles.paramLabel}>{label}</span>
                      <label className={styles.toggle}>
                        <input type="checkbox" checked={params[key]}
                          onChange={e => setParams(p => ({ ...p, [key]: e.target.checked }))} />
                        <div className={styles.toggleTrack} />
                        <div className={styles.toggleKnob} />
                      </label>
                    </div>
                  </div>
                ))}
              </div>
            </div>

          </div>
        )}
      </div>

      {/* Graph + event log */}
      <div className={`${styles.graphRow} ${!eventLogOpen ? styles.graphRowFull : ''}`}>
        <div className={styles.graphWrap} ref={svgRef}>
          <NodeGraph snapshot={snapshot} svgWidth={svgWidth || 600} />
        </div>
        <div className={`${styles.eventLog} ${!eventLogOpen ? styles.eventLogCollapsed : ''}`}
          onClick={!eventLogOpen ? () => setEventLogOpen(true) : undefined}
          title={!eventLogOpen ? 'Expand event log' : undefined}
          style={!eventLogOpen ? { cursor: 'pointer' } : undefined}>
          {!eventLogOpen ? (
            <span className={styles.eventLogCollapsedLabel}>📋 Events</span>
          ) : (<>
          <div className={styles.eventLogTitle}>
            <span className={styles.eventLogTitleText}
              title="Collapse event log"
              style={{ cursor: 'pointer', userSelect: 'none' }}
              onClick={() => setEventLogOpen(false)}>
              ▾ Event log
            </span>
            <button type="button" className={styles.clearLogBtn}
              title="Clear event log"
              onClick={() => { if (simRef.current) { simRef.current.eventLog = []; setSnapshot(captureSnapshot(simRef.current, performance.now())); } }}>
              🗑
            </button>
          </div>
          {snapshot.eventLog.length === 0 ? (
            <div style={{ color: 'var(--ifm-color-emphasis-500)', fontSize: '0.72rem' }}>
              No events yet — inject a fault to start.
            </div>
          ) : snapshot.eventLog.map((e, i) => (
            <div key={i} className={styles.eventEntry}>
              <span className={styles.eventTime}>{(e.simTime / 1000).toFixed(2)}s</span>
              <span>{e.text}</span>
            </div>
          ))}
          </>)}
        </div>
      </div>
      <Legend />

      {/* Stats bar */}
      <div className={styles.statsBar}>
        {[
          ['Sim time', `${(simTime / 1000).toFixed(1)}s`, 'Total simulated time elapsed — advances when running or stepping'],
          ['Messages', msgCount, 'Total messages sent since last reset, including probes, gossip, and sync'],
          ['Failure Detection', firstDetectTime !== null ? `${(firstDetectTime / 1000).toFixed(2)}s` : '—',
            'Time from fault injection until the first node\'s view differed from another\'s (e.g. one node suspected a crashed peer while others still saw it as alive). Driven by probe-interval, probe-timeout, and suspect-probes.'],
          ['Converge', convergeTime !== null ? `${(convergeTime / 1000).toFixed(2)}s` : '—',
            'Time from fault injection until all alive nodes hold an identical view of the cluster. Includes failure detection time plus the time for failure news to propagate via gossip, and for the failure-timeout to expire before declaring a node dead.'],
        ].map(([label, val, tip]) => (
          <div key={label} className={styles.statItem} title={tip}>
            <span className={styles.statLabel}>{label}</span>
            <span className={styles.statVal}>{val}</span>
          </div>
        ))}
        <div className={styles.statItem}>
          <span title="Cluster view state: Stable = all nodes agree, Diverged = views differ after a fault, Converged = all nodes agree again after a fault, Partitioned = active link faults"
            className={`${styles.chip} ${
            hasPartitions ? styles.chipWarn :
            convergeTime !== null ? styles.chipOk :
            hasDiverged ? styles.chipWarn :
            styles.chipInfo
          }`}>
            {hasPartitions ? 'Partitioned ⚠' :
             convergeTime !== null ? 'Converged ✓' :
             hasDiverged ? 'Diverged…' :
             'Stable'}
          </span>
        </div>
      </div>

      {/* Controls row */}
      <div className={styles.controlsBar}>
        <div className={styles.ctrlGroup}>
          <button type="button" title="Resume the simulation" className={`${styles.btn} ${!paused ? styles.btnActive : ''}`}
            onClick={() => setPaused(false)}>▶ Play</button>
          <button type="button" title="Pause the simulation" className={`${styles.btn} ${paused ? styles.btnActive : ''}`}
            onClick={() => setPaused(true)}>⏸ Pause</button>
          <button type="button" title={paused ? `Advance simulation by ${stepSize >= 1000 ? `${stepSize / 1000}s` : `${stepSize}ms`}` : 'Pause first to step'} className={styles.btn} disabled={!paused} onClick={step}>⏭ Step</button>
          <div className={styles.speedGroup}>
            {STEP_SIZES.map(({ label, ms }) => (
              <button type="button" key={ms} title={`Step by ${label} of simulated time`}
                className={`${styles.speedBtn} ${stepSize === ms ? styles.speedBtnActive : ''}`}
                onClick={() => setStepSize(ms)}>{label}</button>
            ))}
          </div>
        </div>
        <div className={styles.sep} />
        <div className={styles.ctrlGroup}>
          <span className={styles.ctrlGroupLabel}>Speed</span>
          <div className={styles.speedGroup}>
            {SPEEDS.map(s => (
              <button type="button" key={s} title={`Run at ${s}× real-time speed`}
                className={`${styles.speedBtn} ${speed === s ? styles.speedBtnActive : ''}`}
                onClick={() => setSpeed(s)}>{s}×</button>
            ))}
          </div>
        </div>
        <div className={styles.sep} />
        <div className={styles.ctrlGroup}>
          <span className={styles.ctrlGroupLabel} title="Publish a BrokerInfo-style property change on a node and watch it spread via gossip">Prop update</span>
          <select className={styles.injectSelect} value={propTarget}
            onChange={e => setPropTarget(Number(e.target.value))}>
            {nodeIds.map(id => <option key={id} value={id}>N{id}</option>)}
          </select>
          <button type="button" title="Publish a property update on the selected node — triggers a U↑ gossip wave"
            className={styles.btn}
            onClick={() => { if (simRef.current) injectPropertyUpdate(simRef.current, propTarget); }}>
            Update
          </button>
        </div>
      </div>

      {/* Faults row */}
      <div className={`${styles.controlsBar} ${styles.faultsBar}`}>
        <span className={styles.ctrlGroupLabel} title="Inject faults to observe how the cluster detects and recovers from failures">Faults</span>
        <div className={styles.sep} />
        <div className={styles.ctrlGroup} title="Crash a node to simulate a process failure — other nodes detect it via probe timeouts, then gossip the failure">
          <span className={styles.ctrlGroupLabel}>Node</span>
          <select className={styles.injectSelect} value={crashTarget}
            onChange={e => setCrashTarget(Number(e.target.value))}>
            {nodeIds.map(id => <option key={id} value={id}>N{id}</option>)}
          </select>
          {(() => {
            const isCrashed = snapshot.nodes.find(n => n.id === crashTarget)?.state === 'crashed';
            return (
              <button type="button"
                title={isCrashed ? 'Bring this node back online with a new incarnation number' : 'Remove this node from the network — triggers the suspect → dead pipeline on other nodes'}
                className={`${styles.btn} ${isCrashed ? styles.btnActive : styles.btnDanger}`}
                onClick={() => {
                  if (!simRef.current) return;
                  if (isCrashed) restoreNode(simRef.current, crashTarget);
                  else crashNode(simRef.current, crashTarget);
                }}>
                {isCrashed ? '↺ Restore' : '✕ Crash'}
              </button>
            );
          })()}
        </div>
        <div className={styles.sep} />
        <div className={styles.ctrlGroup} title="Block or restore a network link between two nodes — both directions are cut simultaneously">
          <span className={styles.ctrlGroupLabel}>Link fault</span>
          <select className={styles.injectSelect} value={partFrom}
            onChange={e => setPartFrom(Number(e.target.value))}>
            {nodeIds.map(id => <option key={id} value={id}>N{id}</option>)}
          </select>
          <select className={styles.injectSelect} value={partTo}
            onChange={e => setPartTo(Number(e.target.value))}>
            {nodeIds.map(id => <option key={id} value={id}>N{id}</option>)}
          </select>
          {(() => {
            const isBlocked = snapshot.partitions.includes(`${partFrom}-${partTo}`) ||
                              snapshot.partitions.includes(`${partTo}-${partFrom}`);
            return (
              <button type="button"
                title={isBlocked
                  ? `Restore the link between N${partFrom} and N${partTo}`
                  : `Block all messages between N${partFrom} and N${partTo} in both directions`}
                className={`${styles.btn} ${isBlocked ? styles.btnActive : styles.btnDanger}`}
                onClick={() => {
                  if (!simRef.current || partFrom === partTo) return;
                  if (isBlocked) {
                    simRef.current.partitions.delete(`${partFrom}-${partTo}`);
                    simRef.current.partitions.delete(`${partTo}-${partFrom}`);
                    logEventDirect(simRef.current, `Link N${partFrom} ↔ N${partTo} restored`);
                  } else {
                    addPartition(simRef.current, partFrom, partTo);
                  }
                }}>
                {isBlocked ? '↺ Restore link' : '✕ Cut link'}
              </button>
            );
          })()}
        </div>
        <div className={styles.sep} />
        <div className={styles.ctrlGroup} title="Simulate a node being consistently skipped in gossip fanout selection — it only receives updates via sync, showing sync-interval as the safety net">
          <span className={styles.ctrlGroupLabel}>Gossip exclude</span>
          <select className={styles.injectSelect} value={excludeTarget}
            onChange={e => setExcludeTarget(Number(e.target.value))}>
            {nodeIds.map(id => <option key={id} value={id}>N{id}</option>)}
          </select>
          {(() => {
            const isExcluded = snapshot.nodes.find(n => n.id === excludeTarget)?.gossipExcluded;
            return (
              <button type="button"
                title={isExcluded ? 'Re-include this node in gossip fanout selection' : 'Exclude this node from others\' gossip fanout — it will only receive updates when sync fires'}
                className={`${styles.btn} ${isExcluded ? styles.btnActive : styles.btnDanger}`}
                onClick={() => {
                  if (!simRef.current) return;
                  const n = simRef.current.nodes.get(excludeTarget);
                  if (n) setGossipExcluded(simRef.current, excludeTarget, !n.gossipExcluded);
                }}>
                {isExcluded ? '↺ Re-include' : 'Exclude'}
              </button>
            );
          })()}
        </div>
      </div>
    </div>
  );
}
