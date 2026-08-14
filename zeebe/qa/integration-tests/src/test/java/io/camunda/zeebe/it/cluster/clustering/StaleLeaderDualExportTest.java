/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static io.camunda.zeebe.test.StableValuePredicate.hasStableValue;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.broker.system.configuration.ExportingCfg;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

/**
 * End-to-end half of the SUPPORT-34109 reproduction: proves that a real raft failover does produce
 * TWO concurrently-exporting {@code ExporterDirector} instances for one partition, and that the
 * shared downstream sink therefore observes writes out of log-position order.
 *
 * <p>Companion tests:
 *
 * <ul>
 *   <li>{@code DualExporterDirectorReplayRaceTest} (zeebe/broker) - deterministic proof that this
 *       arrival order flips a flow node instance back to a non-terminal state.
 *   <li>{@code ReorderedFlushCorruptsFlowNodeStateTest} (camunda-exporter) - proof that the real
 *       handler + Elasticsearch partial-doc merge then yields {@code state=ACTIVE} with a stale
 *       non-null {@code endDate}.
 * </ul>
 *
 * <p>Two production knobs are turned to remove timing flakiness. Neither changes the code path:
 *
 * <ul>
 *   <li>{@code maxQuorumResponseTimeout} is widened. With the default of {@code 0} it resolves to
 *       {@code electionTimeout * 2 = 5s} (LeaderAppender), so a partitioned leader keeps the LEADER
 *       role for 5-15s. Widening it just makes that window comfortably observable.
 *   <li>the exporter-state {@code distributionInterval} is set very high, so followers never learn
 *       the leader's exported position and the new leader rewinds to the start of the log. In
 *       production the same rewind happens, bounded by the 15s default interval.
 * </ul>
 */
public final class StaleLeaderDualExportTest {

  private static final int PARTITION = Protocol.START_PARTITION_ID;
  private static final String JOB_TYPE = "task";

  public final Timeout testTimeout = Timeout.seconds(300);

  public final ClusteringRule clusteringRule =
      new ClusteringRule(
          1,
          3,
          3,
          cfg -> {
            final var exporter = new ExporterCfg();
            exporter.setClassName(SinkExporter.class.getName());
            exporter.setArgs(Map.of("nodeId", cfg.getCluster().getNodeId()));
            cfg.getExporters().put("sink", exporter);

            // never gossip exporter positions -> the new leader rewinds to the very beginning
            cfg.setExporting(new ExportingCfg(Map.of(), Duration.ofHours(1)));
            // keep the exporter position out of snapshots too
            cfg.getData().setSnapshotPeriod(Duration.ofMinutes(30));
            // widen the stale-leader window (see class javadoc)
            cfg.getExperimental().getRaft().setMaxQuorumResponseTimeout(Duration.ofSeconds(120));
          });

  @Rule public RuleChain ruleChain = RuleChain.outerRule(testTimeout).around(clusteringRule);

  @After
  public void tearDown() {
    SinkExporter.STALL_ABOVE.clear();
    SinkExporter.STALLED.clear();
    SinkExporter.SINK.clear();
  }

  @Test
  public void staleLeaderReExportsPositionsTheNewLeaderAlreadyPassed() {
    final var client = clusteringRule.getClient();
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("proc")
                .startEvent()
                .serviceTask("target-task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done(),
            "proc.bpmn")
        .send()
        .join();

    // --- given: some traffic already exported by the current leader ------------------------
    createInstances(5);
    final int staleLeaderId = clusteringRule.getLeaderForPartition(PARTITION).getNodeId();
    final var staleLeader = clusteringRule.getBroker(staleLeaderId);

    Awaitility.await("leader exported something")
        .atMost(Duration.ofSeconds(60))
        .until(() -> maxPositionOf(staleLeaderId) > 0);

    // --- and: the leader's exporter stalls (long GC / disk stall / hung ES socket) ---------
    SinkExporter.STALLED.add(staleLeaderId);
    final long frozenPosition = maxPositionOf(staleLeaderId);

    // more work commits on the leader but is NOT exported by it: a committed backlog
    createInstances(25);

    // --- when: the leader is network-partitioned from its peers ----------------------------
    // Nothing in ExporterDirector checks the raft term, and LeaderAppender only steps down
    // after minStepDownFailureCount failures AND maxQuorumResponseTimeout without quorum.
    clusteringRule.disconnect(staleLeader);

    Awaitility.await("a new leader was elected")
        .atMost(Duration.ofSeconds(60))
        .until(() -> currentLeaderId() != staleLeaderId);
    final int newLeaderId = currentLeaderId();

    // The new leader rewinds to the start of the log and replays it, overtaking the (still
    // LEADER, still alive) stale leader's frozen position. Wait until it has replayed a COMPLETE
    // flow node span - ELEMENT_ACTIVATING .. ELEMENT_COMPLETED - that lies entirely ABOVE the
    // stale leader's frozen position. That span is the reorder victim.
    final long[] victim = new long[3]; // key, activatingPosition, completedPosition
    Awaitility.await("new leader replayed a whole flow node span above the frozen position")
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofMillis(50))
        .until(() -> findVictimSpan(newLeaderId, frozenPosition, victim));
    final int overtakeIndex = SinkExporter.SINK.size();

    // --- and: the stale leader resumes, but only gets PART of the way through ---------------
    // In production the stale leader is stopped mid-window: it self-steps-down
    // (LeaderAppender#failAttempt) or learns of the higher term, and PartitionTransition closes
    // its ExporterDirector, CamundaExporter#close() flushing the pending batch on the way out.
    // The cap makes that deterministic: it re-exports up to the victim's ELEMENT_ACTIVATING and
    // never reaches the victim's ELEMENT_COMPLETED.
    SinkExporter.STALL_ABOVE.put(staleLeaderId, victim[1]);
    SinkExporter.STALLED.remove(staleLeaderId);

    Awaitility.await("stale leader resumed and re-reached the victim's ELEMENT_ACTIVATING")
        .atMost(Duration.ofSeconds(60))
        .until(() -> maxPositionOf(staleLeaderId) >= victim[1]);

    // --- then ------------------------------------------------------------------------------
    final var history = List.copyOf(SinkExporter.SINK);

    // 1. two distinct director instances for the same partition both exported
    final var instanceIds =
        history.stream().map(SinkWrite::instanceId).distinct().sorted().toList();
    assertThat(instanceIds)
        .describedAs("more than one ExporterDirector instance exported for partition %s", PARTITION)
        .hasSizeGreaterThan(1);
    assertThat(history.stream().map(SinkWrite::nodeId).distinct())
        .describedAs("both the stale leader and the new leader exported")
        .contains(staleLeaderId, newLeaderId);

    // 2. the shared sink observed a LOWER position AFTER a HIGHER one - the ordering
    //    invariant the Elasticsearch writes silently depend on is violated
    final var lateStaleWrites =
        history.subList(overtakeIndex, history.size()).stream()
            .filter(w -> w.nodeId() == staleLeaderId)
            .toList();
    assertThat(lateStaleWrites)
        .describedAs("the stale leader wrote to the sink after the new leader had overtaken it")
        .isNotEmpty();

    final long highestBeforeOvertake =
        history.subList(0, overtakeIndex).stream()
            .filter(w -> w.nodeId() == newLeaderId)
            .mapToLong(SinkWrite::position)
            .max()
            .orElse(-1);
    final long lowestLateStaleWrite =
        lateStaleWrites.stream().mapToLong(SinkWrite::position).min().orElse(Long.MAX_VALUE);

    assertThat(lowestLateStaleWrite)
        .describedAs(
            "stale leader (node %s) wrote position %s to the sink AFTER the new leader (node %s)"
                + " had already written position %s - non-monotonic arrival order",
            staleLeaderId, lowestLateStaleWrite, newLeaderId, highestBeforeOvertake)
        .isLessThan(highestBeforeOvertake);

    // 3. the same positions were exported twice, by two different directors
    final var duplicated =
        history.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    SinkWrite::position,
                    java.util.stream.Collectors.mapping(
                        SinkWrite::nodeId, java.util.stream.Collectors.toSet())))
            .entrySet()
            .stream()
            .filter(e -> e.getValue().size() > 1)
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    assertThat(duplicated)
        .describedAs("positions exported by more than one node (duplicate, uncoordinated writes)")
        .isNotEmpty();

    System.out.printf(
        "stale leader=%s, new leader=%s, frozen at=%s, duplicated positions=%s%n",
        staleLeaderId, newLeaderId, frozenPosition, duplicated.size());

    // --- and: the stale leader really loses leadership, closing its ExporterDirector --------
    clusteringRule.connect(staleLeader);
    Awaitility.await("stale leader lost the LEADER role")
        .atMost(Duration.ofSeconds(60))
        .until(() -> currentLeaderId() != staleLeaderId);

    // --- then: the sink's final state for that flow node is the non-terminal one ------------
    Awaitility.await("sink state settles")
        .atMost(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(5))
        .until(() -> writesForKey(victim[0]).size(), hasStableValue());

    final var victimWrites = writesForKey(victim[0]);
    System.out.printf(
        "victim key=%s activating@%s completed@%s writes=%s%n",
        victim[0],
        victim[1],
        victim[2],
        victimWrites.stream()
            .map(w -> w.nodeId() + ":" + w.position() + ":" + w.intent())
            .toList());

    assertThat(victimWrites.stream().map(SinkWrite::intent))
        .describedAs("the flow node was completed at some point")
        .contains(ProcessInstanceIntent.ELEMENT_COMPLETED.name());
    assertThat(victimWrites.get(victimWrites.size() - 1).intent())
        .describedAs(
            "the LAST write Elasticsearch sees for flow node %s is ELEMENT_ACTIVATING, so the"
                + " document is left state=ACTIVE while endDate keeps the value written by the"
                + " earlier ELEMENT_COMPLETED write. Writes: %s",
            victim[0], victimWrites)
        .isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATING.name());
    assertThat(victimWrites.get(victimWrites.size() - 1).nodeId())
        .describedAs("the winning write came from the stale leader")
        .isEqualTo(staleLeaderId);
  }

  /**
   * Finds a flow node instance whose whole ELEMENT_ACTIVATING..ELEMENT_COMPLETED span was exported
   * by the new leader at positions strictly above the stale leader's frozen position. Writes {key,
   * activatingPosition, completedPosition} into {@code out}.
   */
  private boolean findVictimSpan(
      final int newLeaderId, final long frozenPosition, final long[] out) {
    final var byNewLeader =
        List.copyOf(SinkExporter.SINK).stream()
            .filter(w -> w.nodeId() == newLeaderId)
            .filter(w -> w.valueType() == ValueType.PROCESS_INSTANCE)
            .toList();

    for (final var activating : byNewLeader) {
      if (!ProcessInstanceIntent.ELEMENT_ACTIVATING.name().equals(activating.intent())
          || activating.position() <= frozenPosition) {
        continue;
      }
      final var completed =
          byNewLeader.stream()
              .filter(w -> w.key() == activating.key())
              .filter(w -> ProcessInstanceIntent.ELEMENT_COMPLETED.name().equals(w.intent()))
              .filter(w -> w.position() > activating.position())
              .findFirst();
      if (completed.isPresent()) {
        out[0] = activating.key();
        out[1] = activating.position();
        out[2] = completed.get().position();
        return true;
      }
    }
    return false;
  }

  /** The writes Elasticsearch would see for one flow node, restricted to state-setting intents. */
  private List<SinkWrite> writesForKey(final long key) {
    return SinkExporter.SINK.stream()
        .filter(w -> w.key() == key)
        .filter(
            w ->
                ProcessInstanceIntent.ELEMENT_ACTIVATING.name().equals(w.intent())
                    || ProcessInstanceIntent.ELEMENT_COMPLETED.name().equals(w.intent()))
        .toList();
  }

  // ---------------------------------------------------------------------------------------------

  private int currentLeaderId() {
    return clusteringRule.getLeaderForPartition(PARTITION).getNodeId();
  }

  private long maxPositionOf(final int nodeId) {
    return SinkExporter.SINK.stream()
        .filter(w -> w.nodeId() == nodeId)
        .max(Comparator.comparingLong(SinkWrite::position))
        .map(SinkWrite::position)
        .orElse(-1L);
  }

  private void createInstances(final int count) {
    final var client = clusteringRule.getClient();
    for (int i = 0; i < count; i++) {
      client.newCreateInstanceCommand().bpmnProcessId("proc").latestVersion().send().join();
    }
    // complete the jobs so the service tasks reach ELEMENT_COMPLETED far behind their ACTIVATING
    final var jobs =
        client
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(count)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join();
    jobs.getJobs().forEach(job -> client.newCompleteCommand(job.getKey()).send().join());
  }

  /** One downstream write, in sink arrival order. */
  public record SinkWrite(
      int nodeId, int instanceId, long position, ValueType valueType, String intent, long key) {}

  /** Exporter that records into a JVM-wide ordered sink and can be stalled per broker. */
  public static final class SinkExporter implements Exporter {

    static final List<SinkWrite> SINK = new CopyOnWriteArrayList<>();
    static final Set<Integer> STALLED = ConcurrentHashMap.newKeySet();

    /** nodeId -> highest position that node is allowed to export; everything above throws. */
    static final Map<Integer, Long> STALL_ABOVE = new ConcurrentHashMap<>();

    private static final java.util.concurrent.atomic.AtomicInteger INSTANCES =
        new java.util.concurrent.atomic.AtomicInteger();

    private int nodeId;
    private int instanceId;
    private Controller controller;

    @Override
    public void configure(final Context context) {
      nodeId = ((Number) context.getConfiguration().getArguments().get("nodeId")).intValue();
    }

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
      instanceId = INSTANCES.incrementAndGet();
    }

    @Override
    public void export(final Record<?> record) {
      // Throw rather than block: RecordExporter retries forever ("repeat forever until the record
      // can be successfully exported") without occupying the actor thread, so the director's
      // position freezes but closeAsync() can still complete. Models a hung ES socket / GC pause.
      final Long cap = STALL_ABOVE.get(nodeId);
      if (STALLED.contains(nodeId) || (cap != null && record.getPosition() > cap)) {
        throw new RuntimeException("simulated exporter stall on node " + nodeId);
      }

      SINK.add(
          new SinkWrite(
              nodeId,
              instanceId,
              record.getPosition(),
              record.getValueType(),
              record.getIntent().name(),
              record.getKey()));
      controller.updateLastExportedRecordPosition(record.getPosition());
    }

    @Override
    public void close() {}
  }
}
