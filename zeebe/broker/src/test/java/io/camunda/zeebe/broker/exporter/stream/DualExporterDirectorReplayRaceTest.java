/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector.ExporterInitializationInfo;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext.ExporterMode;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.util.TestStreams;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.logstreams.util.TestLogStream;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.impl.SkipPositionsFilter;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

/**
 * Reproduces the ordering invariant violation behind SUPPORT-34109.
 *
 * <p>Two {@link ExporterDirector} instances for the <em>same</em> partition (the stale, not yet
 * deposed raft leader and the newly elected one) run concurrently over the same log and write into
 * the same downstream sink (Elasticsearch in production). There is no term/epoch fencing in {@link
 * ExporterDirector} and no optimistic concurrency control on the Elasticsearch side, so the sink
 * observes writes in an order that is <em>not</em> monotonic in record position.
 *
 * <p>The test asserts the minimal dangerous outcome: the last write the sink observes for a given
 * flow node instance key is a non-terminal ({@code ELEMENT_ACTIVATED}) record, even though a
 * terminal ({@code ELEMENT_COMPLETED}) record for the same key had already been written earlier.
 * Because {@code FlowNodeInstanceFromProcessInstanceHandler#flush} always writes {@code state} but
 * only writes {@code endDate} when it is non-null, that arrival order is exactly what produces an
 * Operate document with {@code state=ACTIVE} and a stale non-null {@code endDate}.
 */
public final class DualExporterDirectorReplayRaceTest {

  private static final String EXPORTER_ID = "recording";
  private static final String STREAM_NAME = "stream";
  private static final int PARTITION_ID = 1;
  private static final int EXPORTER_PROCESSOR_ID = 101;

  /** The flow node instance whose Operate document ends up corrupted. */
  private static final long TARGET_KEY = 4242L;

  private static final long OTHER_KEY = 1111L;

  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final ControlledActorClock clock = new ControlledActorClock();
  private final ActorSchedulerRule schedulerRule = new ActorSchedulerRule(clock);
  private final AutoCloseableRule closeables = new AutoCloseableRule();

  @Rule
  public final RuleChain chain =
      RuleChain.outerRule(tempFolder).around(schedulerRule).around(closeables);

  /** Shared downstream sink; models Elasticsearch. Records arrival order, not log order. */
  private final List<SinkWrite> sink = Collections.synchronizedList(new ArrayList<>());

  private TestStreams streams;
  private TestLogStream logStream;

  @Before
  public void setUp() {
    streams = new TestStreams(tempFolder, closeables, schedulerRule.get(), clock);
    logStream = streams.createLogStream(STREAM_NAME, PARTITION_ID);
  }

  @Test
  public void staleLeaderReExportReordersTerminalStateAtTheSink() throws Exception {
    // === given: the stale leader (D1) has exported a prefix of the log ===============
    // Filler records only; the target element has not been activated yet.
    final long f1 = writeRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, OTHER_KEY);
    final long f2 = writeRecord(ProcessInstanceIntent.ELEMENT_ACTIVATED, OTHER_KEY);
    final long f3 = writeRecord(ProcessInstanceIntent.ELEMENT_COMPLETING, OTHER_KEY);

    final var staleLeaderExporter = new SinkExporter("D1-stale-leader", sink);
    final var staleLeader = startDirector("D1", "d1", -1, staleLeaderExporter);

    Awaitility.await("stale leader exported the initial prefix")
        .untilAsserted(() -> assertThat(positionsExportedBy("D1-stale-leader")).contains(f3));

    // === and: the stale leader stalls (long GC / disk stall / hung ES socket) ========
    // Its exporter position freezes here. In production its raft commitIndex also freezes,
    // so it can never export anything the new leader does not also have.
    staleLeader.pauseExporting().join();

    // === and: the engine keeps producing records; the target element runs to completion ===
    final long activating = writeRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, TARGET_KEY);
    final long activated = writeRecord(ProcessInstanceIntent.ELEMENT_ACTIVATED, TARGET_KEY);
    // Wide gap between ACTIVATED and COMPLETED, as in the customer's log (~1639 positions).
    for (int i = 0; i < 20; i++) {
      writeRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, OTHER_KEY + 100 + i);
    }
    writeRecord(ProcessInstanceIntent.ELEMENT_COMPLETING, TARGET_KEY);
    final long completed = writeRecord(ProcessInstanceIntent.ELEMENT_COMPLETED, TARGET_KEY);
    final long tail = writeRecord(ProcessInstanceIntent.ELEMENT_COMPLETED, OTHER_KEY);

    // === when: a new leader (D2) is elected and rewinds to a stale checkpoint =======
    // A new leader starts from ExportersState#getLowestPosition, which on a follower is only
    // refreshed every ExporterDirectorContext.DEFAULT_DISTRIBUTION_INTERVAL (15s). So it can be
    // far behind what the previous leader had actually already exported. Seeded with f1 here.
    final var newLeaderExporter = new SinkExporter("D2-new-leader", sink);
    final var newLeader = startDirector("D2", "d2", f1, newLeaderExporter);

    Awaitility.await("new leader caught up past the target element's terminal record")
        .untilAsserted(() -> assertThat(positionsExportedBy("D2-new-leader")).contains(tail));

    // At this point the sink is correct: the last write for the target key is COMPLETED.
    assertThat(lastWriteFor(TARGET_KEY))
        .describedAs("sink is consistent before the stale leader resumes")
        .isPresent()
        .get()
        .extracting(SinkWrite::intent)
        .isEqualTo(ProcessInstanceIntent.ELEMENT_COMPLETED.name());

    // === and: the stale leader un-stalls and resumes from its own (now behind) position ===
    // It is still raft LEADER: LeaderAppender#failAttempt only steps down after
    // failureCount >= 3 AND >5s without quorum contact, and nothing in ExporterDirector
    // checks the term. It therefore re-exports records D2 has already exported.
    //
    // It is shut down before it can reach the terminal record again: modelled by making its
    // exporter fail on everything past ELEMENT_ACTIVATED, which RecordExporter#export retries
    // forever ("repeat forever until the record can be successfully exported").
    staleLeaderExporter.failAbovePosition(activated);
    staleLeader.resumeExporting().join();

    Awaitility.await("stale leader re-exported the target element's ACTIVATED record")
        .untilAsserted(
            () ->
                assertThat(positionsExportedBy("D1-stale-leader")).contains(activating, activated));

    // === then: the sink's final write for the target key is the stale, non-terminal one ===
    // Only ELEMENT_ACTIVATING / ELEMENT_COMPLETED / ELEMENT_TERMINATED / *MIGRATED are actually
    // consumed by FlowNodeInstanceFromProcessInstanceHandler#handlesRecord, so restrict the
    // invariant to those - ELEMENT_ACTIVATED / ELEMENT_COMPLETING produce no Elasticsearch write.
    final var history = writesFor(TARGET_KEY);
    final var relevant =
        history.stream()
            .filter(
                w ->
                    w.intent().equals(ProcessInstanceIntent.ELEMENT_ACTIVATING.name())
                        || w.intent().equals(ProcessInstanceIntent.ELEMENT_COMPLETED.name()))
            .toList();

    final var last = relevant.get(relevant.size() - 1);
    assertThat(last.intent())
        .describedAs(
            "last handler-relevant sink write for key %s is the stale ACTIVATING, not COMPLETED."
                + " Full history: %s",
            TARGET_KEY, history)
        .isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATING.name());
    assertThat(last.director()).isEqualTo("D1-stale-leader");
    assertThat(last.position())
        .describedAs("the winning write has a LOWER log position than the write it overwrote")
        .isEqualTo(activating)
        .isLessThan(completed);

    // And the terminal write really did land first, i.e. this is a reorder and not a
    // "terminal record never exported" scenario.
    final int lastCompletedIdx =
        lastIndexOfIntent(relevant, ProcessInstanceIntent.ELEMENT_COMPLETED);
    final int lastActivatingIdx =
        lastIndexOfIntent(relevant, ProcessInstanceIntent.ELEMENT_ACTIVATING);
    assertThat(lastCompletedIdx).describedAs("COMPLETED did land at the sink").isNotNegative();
    assertThat(lastActivatingIdx)
        .describedAs("ACTIVATING arrived after COMPLETED at the sink: %s", relevant)
        .isGreaterThan(lastCompletedIdx);

    System.out.println("=== sink arrival order for key " + TARGET_KEY + " ===");
    history.forEach(System.out::println);

    // cleanup: let the retrying director give up
    staleLeaderExporter.failAbovePosition(Long.MAX_VALUE);
    staleLeader.stopAsync().join();
    newLeader.stopAsync().join();
  }

  /**
   * Negative control: the exact same log, the exact same stale-checkpoint rewind, but only ONE
   * director. Proves the assertion above is not vacuously true and that the corruption really does
   * require two uncoordinated writers (or an interrupted replay), not merely a rewind.
   */
  @Test
  public void singleDirectorReplayingFromStaleCheckpointConvergesToTerminalState()
      throws Exception {
    final long f1 = writeRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, OTHER_KEY);
    writeRecord(ProcessInstanceIntent.ELEMENT_ACTIVATED, OTHER_KEY);
    writeRecord(ProcessInstanceIntent.ELEMENT_COMPLETING, OTHER_KEY);
    writeRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, TARGET_KEY);
    writeRecord(ProcessInstanceIntent.ELEMENT_ACTIVATED, TARGET_KEY);
    for (int i = 0; i < 20; i++) {
      writeRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, OTHER_KEY + 100 + i);
    }
    writeRecord(ProcessInstanceIntent.ELEMENT_COMPLETING, TARGET_KEY);
    writeRecord(ProcessInstanceIntent.ELEMENT_COMPLETED, TARGET_KEY);
    final long tail = writeRecord(ProcessInstanceIntent.ELEMENT_COMPLETED, OTHER_KEY);

    // one director, rewound to a stale checkpoint, replays the whole range
    final var exporter = new SinkExporter("D-only", sink);
    final var director = startDirector("D", "d-only", f1, exporter);

    Awaitility.await("director drained the log")
        .untilAsserted(() -> assertThat(positionsExportedBy("D-only")).contains(tail));

    assertThat(lastWriteFor(TARGET_KEY))
        .isPresent()
        .get()
        .extracting(SinkWrite::intent)
        .describedAs("a single serial director always converges to the terminal state")
        .isEqualTo(ProcessInstanceIntent.ELEMENT_COMPLETED.name());

    director.stopAsync().join();
  }

  // ------------------------------------------------------------------------------------------
  // helpers
  // ------------------------------------------------------------------------------------------

  private ExporterDirector startDirector(
      final String name, final String folder, final long seedPosition, final SinkExporter exporter)
      throws Exception {
    final Path runtime = tempFolder.newFolder(folder).toPath().resolve("runtime");
    final ZeebeDb<ZbColumnFamilies> db =
        DefaultZeebeDbFactory.defaultFactory().createDb(runtime.toFile(), false);
    closeables.manage(db);

    if (seedPosition >= 0) {
      // Model the stale checkpoint a freshly promoted leader would read out of its own RocksDB.
      new ExportersState(db, db.createContext()).setPosition(EXPORTER_ID, seedPosition);
    }

    final ExporterDescriptor descriptor =
        spy(new ExporterDescriptor(EXPORTER_ID, exporter.getClass(), Map.of()));
    doAnswer(c -> exporter).when(descriptor).newInstance();

    final ExporterDirectorContext context =
        new ExporterDirectorContext()
            .id(EXPORTER_PROCESSOR_ID)
            .name(name)
            .logStream(logStream)
            .clock(StreamClock.system())
            .zeebeDb(db)
            .exporterMode(ExporterMode.ACTIVE)
            // isolate the two directors: no exporter-state gossip between them
            .distributionInterval(Duration.ofHours(1))
            .partitionMessagingService(new SimplePartitionMessageService())
            .descriptors(Map.of(descriptor, new ExporterInitializationInfo(0, null)))
            .meterRegistry(new SimpleMeterRegistry())
            .positionsToSkipFilter(SkipPositionsFilter.of(Set.of()));

    final var director = new ExporterDirector(context, ExporterPhase.EXPORTING);
    director.startAsync(schedulerRule.get()).join();
    return director;
  }

  private long writeRecord(final ProcessInstanceIntent intent, final long elementInstanceKey) {
    final var value = new ProcessInstanceRecord();
    value
        .setBpmnProcessId("process")
        .setProcessDefinitionKey(1L)
        .setProcessInstanceKey(100L)
        .setVersion(1)
        .setElementId("task-" + elementInstanceKey)
        .setFlowScopeKey(100L)
        .setBpmnElementType(BpmnElementType.SERVICE_TASK);

    return streams
        .newRecord(STREAM_NAME)
        .recordType(RecordType.EVENT)
        .intent(intent)
        .key(elementInstanceKey)
        .event(value)
        .write();
  }

  private List<Long> positionsExportedBy(final String director) {
    synchronized (sink) {
      return sink.stream()
          .filter(w -> w.director().equals(director))
          .map(SinkWrite::position)
          .toList();
    }
  }

  private List<SinkWrite> writesFor(final long key) {
    synchronized (sink) {
      return sink.stream().filter(w -> w.key() == key).toList();
    }
  }

  private Optional<SinkWrite> lastWriteFor(final long key) {
    final var writes = writesFor(key);
    return writes.isEmpty() ? Optional.empty() : Optional.of(writes.get(writes.size() - 1));
  }

  private static int lastIndexOfIntent(
      final List<SinkWrite> history, final ProcessInstanceIntent intent) {
    for (int i = history.size() - 1; i >= 0; i--) {
      if (history.get(i).intent().equals(intent.name())) {
        return i;
      }
    }
    return -1;
  }

  /** A single downstream write, in sink arrival order. */
  private record SinkWrite(String director, long position, long key, String intent) {}

  /**
   * Records every exported record into the shared sink in arrival order. Optionally fails on
   * records above a given position, which {@link RecordExporter} retries forever - modelling a
   * director that is shut down (or stuck) before it can reach a later record.
   */
  public static final class SinkExporter implements Exporter {

    private final String directorName;
    private final List<SinkWrite> sink;
    private volatile long failAbovePosition = Long.MAX_VALUE;
    private Controller controller;

    public SinkExporter(final String directorName, final List<SinkWrite> sink) {
      this.directorName = directorName;
      this.sink = sink;
    }

    void failAbovePosition(final long position) {
      failAbovePosition = position;
    }

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
    }

    @Override
    public void export(final Record<?> record) {
      if (record.getPosition() > failAbovePosition) {
        throw new RuntimeException(
            "simulated stall of " + directorName + " at position " + record.getPosition());
      }
      sink.add(
          new SinkWrite(
              directorName, record.getPosition(), record.getKey(), record.getIntent().name()));
      controller.updateLastExportedRecordPosition(record.getPosition());
    }

    @Override
    public void close() {}
  }
}
