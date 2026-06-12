import React, { useState, useMemo } from 'react';
import styles from './PartitionDistributionVisualizer.module.css';

// ── Algorithm (mirrors RoundRobinPartitionDistributor.java) ───────────────────

function distribute(brokers, partitions, rf) {
  const actualRf = Math.min(rf, brokers);
  return Array.from({ length: partitions }, (_, i) => ({
    id: i + 1,
    primary: i % brokers,
    members: Array.from({ length: actualRf }, (_, j) => (i + j) % brokers),
  }));
}

function getPriorities(partitionId, members, primary, clusterSize, rf) {
  const priorities = { [primary]: rf };
  const followers = members.filter((m) => m !== primary);
  if (Math.floor((partitionId - 1) / clusterSize) % 2 === 0) {
    let p = rf - 1;
    followers.forEach((m) => { priorities[m] = p--; });
  } else {
    let p = 1;
    followers.forEach((m) => { priorities[m] = p++; });
  }
  return priorities;
}

function computeStats(dist, brokers, rf) {
  return Array.from({ length: brokers }, (_, b) => {
    const leaderCount = dist.filter((p) => p.primary === b).length;
    const followerCount = dist.filter((p) => p.primary !== b && p.members.includes(b)).length;
    let nextLeaderCount = 0;
    dist.forEach((p) => {
      if (p.primary === b || !p.members.includes(b)) return;
      const pri = getPriorities(p.id, p.members, p.primary, brokers, rf);
      const maxFollowerPri = Math.max(
        ...p.members.filter((m) => m !== p.primary).map((m) => pri[m]),
      );
      if (pri[b] === maxFollowerPri) nextLeaderCount++;
    });
    return { broker: b, leaderCount, followerCount, total: leaderCount + followerCount, nextLeaderCount };
  });
}

// ── Component ─────────────────────────────────────────────────────────────────

export default function PartitionDistributionVisualizer() {
  const [brokers, setBrokers] = useState(3);
  const [partitions, setPartitions] = useState(3);
  const [rf, setRf] = useState(3);
  const [showPriority, setShowPriority] = useState(true);
  const [activeTab, setActiveTab] = useState('matrix');

  const effectiveRf = Math.min(rf, brokers);

  function handleBrokersChange(e) {
    const val = Number(e.target.value);
    setBrokers(val);
    if (rf > val) setRf(val);
  }

  const dist = useMemo(
    () => distribute(brokers, partitions, effectiveRf),
    [brokers, partitions, effectiveRf],
  );

  const stats = useMemo(
    () => computeStats(dist, brokers, effectiveRf),
    [dist, brokers, effectiveRf],
  );

  const totalReplicas = dist.length * effectiveRf;
  const leaderCounts = stats.map((s) => s.leaderCount);
  const isBalanced = Math.max(...leaderCounts) - Math.min(...leaderCounts) <= 1;
  const maxTotal = Math.max(...stats.map((s) => s.total), 1);

  return (
    <div className={styles.widget}>
      {/* Controls */}
      <div className={styles.controls}>
        <div className={styles.ctrl}>
          <label className={styles.ctrlLabel} htmlFor="pdv-brokers">Brokers</label>
          <div className={styles.ctrlRow}>
            <input
              type="range" id="pdv-brokers"
              min="1" max="32" value={brokers}
              onChange={handleBrokersChange}
            />
            <span className={styles.ctrlVal}>{brokers}</span>
          </div>
        </div>
        <div className={styles.ctrl}>
          <label className={styles.ctrlLabel} htmlFor="pdv-partitions">Partitions</label>
          <div className={styles.ctrlRow}>
            <input
              type="range" id="pdv-partitions"
              min="1" max="128" value={partitions}
              onChange={(e) => setPartitions(Number(e.target.value))}
            />
            <span className={styles.ctrlVal}>{partitions}</span>
          </div>
        </div>
        <div className={styles.ctrl}>
          <label className={styles.ctrlLabel} htmlFor="pdv-rf">Replication Factor</label>
          <div className={styles.ctrlRow}>
            <input
              type="range" id="pdv-rf"
              min="1" max={brokers} value={effectiveRf}
              onChange={(e) => setRf(Number(e.target.value))}
            />
            <span className={styles.ctrlVal}>{effectiveRf}</span>
          </div>
          <span className={styles.ctrlCap}>Capped to broker count</span>
        </div>
        <div className={styles.ctrlDivider} />
        <div className={styles.toggleWrap}>
          <span className={styles.toggleLabel}>Election Priority</span>
          <div className={styles.toggleRow}>
            <label className={styles.toggle}>
              <input
                type="checkbox"
                checked={showPriority}
                onChange={(e) => setShowPriority(e.target.checked)}
              />
              <div className={styles.toggleTrack} />
              <div className={styles.toggleKnob} />
            </label>
            <span className={styles.toggleDesc}>Show priority numbers</span>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className={styles.tabs}>
        <button
          type="button"
          className={`${styles.tab} ${activeTab === 'matrix' ? styles.tabActive : ''}`}
          onClick={() => setActiveTab('matrix')}
        >
          Matrix Table
        </button>
        <button
          type="button"
          className={`${styles.tab} ${activeTab === 'cards' ? styles.tabActive : ''}`}
          onClick={() => setActiveTab('cards')}
        >
          Partition Cards
        </button>
      </div>

      {/* Matrix panel */}
      {activeTab === 'matrix' && (
        <div className={styles.panel}>
          {partitions > 12 && (
            <div className={styles.scrollHint}>
              ↔ Scroll to see all {partitions} partitions — broker labels stay pinned
            </div>
          )}
          <div className={styles.matrixScroll}>
            {(() => {
              const priMaps = showPriority
                ? dist.map((p) => getPriorities(p.id, p.members, p.primary, brokers, p.members.length))
                : null;
              return (
                <table className={styles.matrix}>
                  <thead>
                    <tr>
                      <th className={styles.rl} />
                      {dist.map((p) => <th key={p.id}>P{p.id}</th>)}
                    </tr>
                  </thead>
                  <tbody>
                    {Array.from({ length: brokers }, (_, b) => (
                      <tr key={b}>
                        <td className={styles.rl}>Broker {b}</td>
                        {dist.map((p) => {
                          const isLeader = b === p.primary;
                          const isFollower = !isLeader && p.members.includes(b);
                          const pri = priMaps ? priMaps[p.id - 1] : null;
                          return (
                            <td key={p.id}>
                              {isLeader && (
                                <span className={styles.cellLeader}>
                                  L{showPriority && <span className={styles.cellPri}> ·{pri[b]}</span>}
                                </span>
                              )}
                              {isFollower && (
                                <span className={styles.cellFollower}>
                                  F{showPriority && <span className={styles.cellPri}> ·{pri[b]}</span>}
                                </span>
                              )}
                            </td>
                          );
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              );
            })()}
          </div>
          <div className={styles.legend}>
            <div className={styles.legItem}>
              <span className={styles.cellLeader} style={{ fontSize: '0.72rem', padding: '2px 6px' }}>L</span>
              {showPriority ? 'Leader (L·priority)' : 'Leader'}
            </div>
            <div className={styles.legItem}>
              <span className={styles.cellFollower} style={{ fontSize: '0.72rem', padding: '2px 6px' }}>F</span>
              {showPriority ? 'Follower (F·priority)' : 'Follower'}
            </div>
          </div>
        </div>
      )}

      {/* Cards panel */}
      {activeTab === 'cards' && (
        <div className={styles.panel}>
          <div className={styles.pcards}>
            {dist.map((p) => {
              const pri = showPriority
                ? getPriorities(p.id, p.members, p.primary, brokers, p.members.length)
                : null;
              return (
                <div key={p.id} className={styles.pcard}>
                  <div className={styles.pcardHead}>Partition {p.id}</div>
                  <div className={styles.pcardBody}>
                    {p.members.map((b) => {
                      const isLeader = b === p.primary;
                      return (
                        <div key={b} className={styles.roleRow}>
                          <span className={isLeader ? styles.rbadgeLeader : styles.rbadgeFollower}>
                            {isLeader ? 'Lead' : 'Fol'}
                          </span>
                          <span className={styles.bname}>B{b}</span>
                          {showPriority && <span className={styles.priVal}>·{pri[b]}</span>}
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Stats */}
      <hr className={styles.sep} />
      <div className={styles.sectionLabel}>Broker statistics</div>
      <div className={styles.panel}>
        <div className={styles.statsGrid}>
          {stats.map((s) => (
            <div key={s.broker} className={styles.statCard}>
              <div className={styles.statHead}>B{s.broker}</div>
              <div className={styles.statBody}>
                <div className={styles.bar}>
                  <div
                    className={styles.barLeader}
                    style={{ width: `${(s.leaderCount / maxTotal) * 100}%` }}
                  />
                  <div
                    className={styles.barFollower}
                    style={{ width: `${(s.followerCount / maxTotal) * 100}%` }}
                  />
                </div>
                {[['Lead', s.leaderCount], ['Fol', s.followerCount], ['Tot', s.total], ['Next', s.nextLeaderCount]].map(
                  ([label, val]) => (
                    <div key={label} className={styles.statRow}>
                      <span className={styles.statLbl}>{label}</span>
                      <span className={styles.statVal}>{val}</span>
                    </div>
                  ),
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className={styles.summary}>
        <div className={styles.summItem}>
          <span className={styles.summLabel}>Total replicas</span>
          <span className={styles.summVal}>{totalReplicas}</span>
        </div>
        <div className={styles.summItem}>
          <span className={styles.summLabel}>Avg / broker</span>
          <span className={styles.summVal}>{(totalReplicas / brokers).toFixed(1)}</span>
        </div>
        <div className={styles.summItem}>
          <span className={styles.summLabel}>Ideal leaders / broker</span>
          <span className={styles.summVal}>{(partitions / brokers).toFixed(1)}</span>
        </div>
        <div className={styles.summItem}>
          <span className={styles.summLabel}>Leadership balance</span>
          <span className={`${styles.balChip} ${isBalanced ? styles.balOk : styles.balWarn}`}>
            {isBalanced
              ? 'Balanced ✓'
              : `Skewed ±${Math.max(...leaderCounts) - Math.min(...leaderCounts)}`}
          </span>
        </div>
      </div>
    </div>
  );
}
