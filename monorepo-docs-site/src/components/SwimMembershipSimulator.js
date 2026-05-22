import React, { useState, useEffect, useRef, useCallback } from 'react';
import styles from './SwimMembershipSimulator.module.css';
import {
  createSim, advanceSim, captureSnapshot,
  crashNode, restoreNode, injectPropertyUpdate,
  addPartition, clearPartitions, setGossipExcluded,
} from './swimSimEngine';

// ── SVG node graph ────────────────────────────────────────────────────────────

const GRAPH_H = 380;
const NODE_R = 22;

function nodePositions(count, width) {
  const cx = width / 2;
  const cy = GRAPH_H / 2;
  const r = Math.min(cx, cy) * (count <= 4 ? 0.5 : count <= 8 ? 0.62 : 0.72);
  return Array.from({ length: count }, (_, i) => {
    const angle = (2 * Math.PI * i / count) - Math.PI / 2;
    return { x: cx + r * Math.cos(angle), y: cy + r * Math.sin(angle) };
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
  ['G~',  'var(--ifm-color-emphasis-400)', 'Gossip (routine)'],
  ['G↑',  'var(--viz-gossip-update)',      'Gossip (update)'],
  ['G✗',  'var(--viz-leader)',             'Gossip (failure)'],
  ['P→',  'var(--viz-msg-probe)',          'Probe'],
  ['PR→', '#e65100',                       'Indirect probe'],
  ['S⇄',  'var(--viz-msg-sync)',           'Sync'],
];

function NodeGraph({ snapshot, nodeCount, svgWidth }) {
  const positions = nodePositions(nodeCount, svgWidth);

  return (
    <svg width={svgWidth} height={GRAPH_H} className={styles.graph}>
      <defs>
        <marker id="swim-arrow-red" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
          <path d="M0,0 L0,6 L6,3 z" fill="var(--viz-leader)" />
        </marker>
      </defs>

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

      {/* Partition indicators */}
      {snapshot?.partitions.map(p => {
        const [a, b] = p.split('-').map(Number);
        if (a >= nodeCount || b >= nodeCount) return null;
        return (
          <line key={`p-${p}`}
            x1={positions[a].x} y1={positions[a].y}
            x2={positions[b].x} y2={positions[b].y}
            stroke="var(--viz-leader)" strokeWidth="2" strokeDasharray="5,4"
            markerEnd="url(#swim-arrow-red)" opacity="0.8"
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
            <circle r="10" fill={m.color} opacity={m.label === 'G~' ? 0.5 : 0.9} />
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
              <circle r={NODE_R + 5} fill="none"
                stroke="var(--viz-gossip-exclude)" strokeWidth="2" strokeDasharray="4,3" />
            )}
            <circle r={NODE_R}
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
          </g>
        );
      })}

      {/* Legend */}
      <g transform={`translate(${svgWidth - 136}, ${GRAPH_H - 126})`}>
        <rect width="128" height="118" rx="5"
          fill="var(--ifm-background-surface-color)"
          stroke="var(--ifm-color-emphasis-300)" />
        {LEGEND_ENTRIES.map(([label, color, desc], i) => (
          <g key={label} transform={`translate(6, ${8 + i * 17})`}>
            <circle cx="8" cy="6" r="7" fill={color} opacity="0.9" />
            <text x="8" y="9" textAnchor="middle" fontSize="5.5" fontWeight="700" fill="white">{label}</text>
            <text x="20" y="9" fontSize="8" fill="var(--ifm-color-emphasis-700)">{desc}</text>
          </g>
        ))}
      </g>
    </svg>
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
  failureTimeout: 10000,
  broadcastDisputes: true,
  notifySuspect: false,
  syncInterval: 10000,
};

const SPEEDS = [1, 5, 20];

// ── Main component ────────────────────────────────────────────────────────────

export default function SwimMembershipSimulator() {
  const [nodeCount, setNodeCount] = useState(6);
  const [params, setParams] = useState(DEFAULT_PARAMS);
  const [paused, setPaused] = useState(false);
  const [speed, setSpeed] = useState(5);
  const [paramsOpen, setParamsOpen] = useState(true);
  const [snapshot, setSnapshot] = useState(null);
  const [crashTarget, setCrashTarget]     = useState(0);
  const [restoreTarget, setRestoreTarget] = useState(0);
  const [propTarget, setPropTarget]       = useState(0);
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
    setSnapshot(captureSnapshot(simRef.current, performance.now()));
  }, [nodeCount]);

  useEffect(() => { paramsRef.current = params; }, [params]);

  useEffect(() => {
    const tick = (wallTime) => {
      if (simRef.current && !paused) {
        const targetSimTime = simRef.current.simTime + 10 * speed;
        advanceSim(simRef.current, targetSimTime, wallTime);
      }
      if (simRef.current) setSnapshot(captureSnapshot(simRef.current, performance.now()));
      rafRef.current = requestAnimationFrame(tick);
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, [paused, speed]);

  const step = useCallback(() => {
    if (!simRef.current) return;
    const targetSimTime = simRef.current.simTime + paramsRef.current.probeInterval;
    advanceSim(simRef.current, targetSimTime, performance.now());
    setSnapshot(captureSnapshot(simRef.current, performance.now()));
  }, []);

  const reset = useCallback(() => {
    simRef.current = createSim(nodeCount, paramsRef.current);
    setSnapshot(captureSnapshot(simRef.current, performance.now()));
  }, [nodeCount]);

  const nodeIds = Array.from({ length: nodeCount }, (_, i) => i);

  if (!snapshot) return null;

  const { simTime, roundCount, msgCount, firstDetectTime, convergeTime, hasPartitions } = snapshot;

  return (
    <div className={styles.widget}>
      {/* Parameters panel */}
      <div className={styles.paramsPanel}>
        <div className={styles.paramsPanelHeader} onClick={() => setParamsOpen(o => !o)}>
          {paramsOpen ? '▾' : '▸'} Parameters
        </div>
        {paramsOpen && (
          <div className={styles.paramsBody}>
            <div className={styles.paramGroup}>
              <div className={styles.paramGroupLabel}>Gossip</div>
              {[
                ['Interval (ms)', 'gossipInterval', 50, 2000, 50],
                ['Fanout', 'gossipFanout', 1, nodeCount - 1, 1],
              ].map(([label, key, min, max, s]) => (
                <div key={key} className={styles.paramRow}>
                  <span className={styles.paramLabel}>{label}</span>
                  <input type="range" className={styles.paramSlider}
                    min={min} max={max} step={s} value={params[key]}
                    onChange={e => setParams(p => ({ ...p, [key]: Number(e.target.value) }))} />
                  <span className={styles.paramVal}>{params[key]}</span>
                </div>
              ))}
              {[
                ['Broadcast updates', 'broadcastUpdates'],
              ].map(([label, key]) => (
                <div key={key} className={styles.paramRow}>
                  <span className={styles.paramLabel}>{label}</span>
                  <label className={styles.toggle}>
                    <input type="checkbox" checked={params[key]}
                      onChange={e => setParams(p => ({ ...p, [key]: e.target.checked }))} />
                    <div className={styles.toggleTrack} />
                    <div className={styles.toggleKnob} />
                  </label>
                </div>
              ))}
            </div>

            <div className={styles.paramGroup}>
              <div className={styles.paramGroupLabel}>Failure Detection</div>
              {[
                ['Probe interval (ms)', 'probeInterval', 100, 5000, 100],
                ['Probe timeout (ms)',  'probeTimeout',  50,  2000, 50],
                ['Suspect probes',      'suspectProbes', 1,   10,   1],
                ['Failure timeout (ms)','failureTimeout',1000,30000,1000],
              ].map(([label, key, min, max, s]) => (
                <div key={key} className={styles.paramRow}>
                  <span className={styles.paramLabel}>{label}</span>
                  <input type="range" className={styles.paramSlider}
                    min={min} max={max} step={s} value={params[key]}
                    onChange={e => setParams(p => ({ ...p, [key]: Number(e.target.value) }))} />
                  <span className={styles.paramVal}>{params[key]}</span>
                </div>
              ))}
              {[
                ['Broadcast disputes', 'broadcastDisputes'],
                ['Notify suspect',     'notifySuspect'],
              ].map(([label, key]) => (
                <div key={key} className={styles.paramRow}>
                  <span className={styles.paramLabel}>{label}</span>
                  <label className={styles.toggle}>
                    <input type="checkbox" checked={params[key]}
                      onChange={e => setParams(p => ({ ...p, [key]: e.target.checked }))} />
                    <div className={styles.toggleTrack} />
                    <div className={styles.toggleKnob} />
                  </label>
                </div>
              ))}
            </div>

            <div className={styles.paramGroup}>
              <div className={styles.paramGroupLabel}>Sync</div>
              <div className={styles.paramRow}>
                <span className={styles.paramLabel}>Sync interval (ms)</span>
                <input type="range" className={styles.paramSlider}
                  min={1000} max={60000} step={1000} value={params.syncInterval}
                  onChange={e => setParams(p => ({ ...p, syncInterval: Number(e.target.value) }))} />
                <span className={styles.paramVal}>{params.syncInterval}</span>
              </div>
              <div className={styles.paramGroupLabel} style={{ marginTop: 8 }}>Cluster</div>
              <div className={styles.paramRow}>
                <span className={styles.paramLabel}>Node count</span>
                <input type="range" className={styles.paramSlider}
                  min={2} max={16} step={1} value={nodeCount}
                  onChange={e => setNodeCount(Number(e.target.value))} />
                <span className={styles.paramVal}>{nodeCount}</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Graph + event log */}
      <div className={styles.graphRow}>
        <div className={styles.graphWrap} ref={svgRef}>
          <NodeGraph snapshot={snapshot} nodeCount={nodeCount} svgWidth={svgWidth || 600} />
        </div>
        <div className={styles.eventLog}>
          <div className={styles.eventLogTitle}>Event log</div>
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
        </div>
      </div>

      {/* Stats bar */}
      <div className={styles.statsBar}>
        {[
          ['Sim time', `${(simTime / 1000).toFixed(1)}s`],
          ['Rounds',   roundCount],
          ['Messages', msgCount],
          ['Detect',   firstDetectTime !== null ? `${(firstDetectTime / 1000).toFixed(2)}s` : '—'],
          ['Converge', convergeTime    !== null ? `${(convergeTime / 1000).toFixed(2)}s`    : '—'],
        ].map(([label, val]) => (
          <div key={label} className={styles.statItem}>
            <span className={styles.statLabel}>{label}</span>
            <span className={styles.statVal}>{val}</span>
          </div>
        ))}
        <div className={styles.statItem}>
          <span className={`${styles.chip} ${
            hasPartitions ? styles.chipWarn :
            convergeTime !== null ? styles.chipOk : styles.chipInfo
          }`}>
            {hasPartitions ? 'Partitioned ⚠' : convergeTime !== null ? 'Converged ✓' : 'Running…'}
          </span>
        </div>
      </div>

      {/* Controls bar */}
      <div className={styles.controlsBar}>
        <div className={styles.ctrlGroup}>
          <button type="button" className={`${styles.btn} ${!paused ? styles.btnActive : ''}`}
            onClick={() => setPaused(false)}>▶ Play</button>
          <button type="button" className={`${styles.btn} ${paused ? styles.btnActive : ''}`}
            onClick={() => setPaused(true)}>⏸ Pause</button>
          <button type="button" className={styles.btn} onClick={step}>⏭ Step</button>
        </div>
        <div className={styles.sep} />
        <div className={styles.ctrlGroup}>
          <span className={styles.ctrlGroupLabel}>Speed</span>
          <div className={styles.speedGroup}>
            {SPEEDS.map(s => (
              <button type="button" key={s}
                className={`${styles.speedBtn} ${speed === s ? styles.speedBtnActive : ''}`}
                onClick={() => setSpeed(s)}>×{s}</button>
            ))}
          </div>
        </div>
        <div className={styles.sep} />
        <div className={styles.ctrlGroup}>
          <span className={styles.ctrlGroupLabel}>Inject</span>
          <select className={styles.injectSelect} value={crashTarget}
            onChange={e => setCrashTarget(Number(e.target.value))}>
            {nodeIds.map(id => <option key={id} value={id}>N{id}</option>)}
          </select>
          <button type="button" className={`${styles.btn} ${styles.btnDanger}`}
            onClick={() => { if (simRef.current) crashNode(simRef.current, crashTarget); }}>
            Crash
          </button>
          <select className={styles.injectSelect} value={restoreTarget}
            onChange={e => setRestoreTarget(Number(e.target.value))}>
            {nodeIds.map(id => <option key={id} value={id}>N{id}</option>)}
          </select>
          <button type="button" className={styles.btn}
            onClick={() => { if (simRef.current) restoreNode(simRef.current, restoreTarget); }}>
            Restore
          </button>
        </div>
        <div className={styles.sep} />
        <div className={styles.ctrlGroup}>
          <select className={styles.injectSelect} value={propTarget}
            onChange={e => setPropTarget(Number(e.target.value))}>
            {nodeIds.map(id => <option key={id} value={id}>N{id}</option>)}
          </select>
          <button type="button" className={styles.btn}
            onClick={() => { if (simRef.current) injectPropertyUpdate(simRef.current, propTarget); }}>
            Prop update
          </button>
        </div>
        <div className={styles.sep} />
        <div className={styles.ctrlGroup}>
          <select className={styles.injectSelect} value={partFrom}
            onChange={e => setPartFrom(Number(e.target.value))}>
            {nodeIds.map(id => <option key={id} value={id}>N{id}</option>)}
          </select>
          <span style={{ fontSize: '0.75rem' }}>→</span>
          <select className={styles.injectSelect} value={partTo}
            onChange={e => setPartTo(Number(e.target.value))}>
            {nodeIds.map(id => <option key={id} value={id}>N{id}</option>)}
          </select>
          <button type="button" className={`${styles.btn} ${styles.btnDanger}`}
            onClick={() => { if (simRef.current && partFrom !== partTo) addPartition(simRef.current, partFrom, partTo); }}>
            Partition
          </button>
        </div>
        <div className={styles.sep} />
        <div className={styles.ctrlGroup}>
          <select className={styles.injectSelect} value={excludeTarget}
            onChange={e => setExcludeTarget(Number(e.target.value))}>
            {nodeIds.map(id => <option key={id} value={id}>N{id}</option>)}
          </select>
          <button type="button" className={styles.btn}
            onClick={() => {
              if (!simRef.current) return;
              const n = simRef.current.nodes.get(excludeTarget);
              if (n) setGossipExcluded(simRef.current, excludeTarget, !n.gossipExcluded);
            }}>
            Toggle gossip exclude
          </button>
        </div>
        <div className={styles.sep} />
        <button type="button" className={`${styles.btn} ${styles.btnDanger}`}
          onClick={() => { if (simRef.current) clearPartitions(simRef.current); }}>
          Clear partitions
        </button>
        <button type="button" className={styles.btn} onClick={reset}>Reset</button>
      </div>
    </div>
  );
}
