/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.CompositeAggregateFunction;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.projection.ProjectionMetrics;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.store.rdbms.RdbmsDatasetStore;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import io.camunda.eventbridge.streaming.CommitCut;
import io.camunda.eventbridge.streaming.internals.FlowMetrics;
import io.camunda.eventbridge.streaming.internals.StoreMetrics;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelopeCodec;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Stage-1 frozen commit cut (streaming ADR 0005): {@code freezeCut} detaches the cut at the
 * barrier — the persisted state, the pre-fold dedup watermarks and the published shuffle deltas
 * describe exactly the folds up to the barrier, no matter what the task folds while the cut
 * publishes and persists — a failed cut is merged back and covered exactly once by the next commit,
 * and the produced output is durable before the offset advances.
 */
final class ProjectionStageTaskFrozenCutTest {

  private static final long PI_KEY = 123L;
  private static final int ZEEBE_PARTITION = 3;
  private static final int EB_PARTITION = 1;

  @TempDir Path stateDir;

  private TestMetadataStore metadataStore;
  private DatasetRegistry registry;
  private DatasetCatalog catalog;
  private RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private ProjectionStageTask task;

  /** Frames the fake transport has durably published (i.e. batches actually sent), in order. */
  private final List<byte[]> publishedFrames = new ArrayList<>();

  @BeforeEach
  void setUp() {
    metadataStore = new TestMetadataStore();
    registry = new DatasetRegistry();
    provision("cube-a");
  }

  @AfterEach
  void tearDown() {
    if (task != null) {
      task.close(); // also closes the dataset store and the provider
    }
  }

  @Test
  void shouldNotIncludeFoldsAfterTheFreezeInThePersistedCut() {
    // given two folds up to the barrier
    openTask(1_000, mock(EventBridgeClient.class));
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 100L));
    task.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L, 101L));

    // when a cut freezes at that barrier and a third record folds while it persists
    final CommitCut cut = task.freezeCut(101L);
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 2000L, 12L, 102L));
    cut.publish();
    cut.persist();
    cut.complete(true);

    // then the durable cut holds exactly the two pre-barrier folds and its offset
    assertThat(openSegmentTotal()).isEqualTo(2L);
    assertThat(durableOffset()).hasValue(101L);

    // and the next commit covers the post-barrier fold
    Cuts.commit(task, 102L);
    assertThat(openSegmentTotal()).isEqualTo(3L);
    assertThat(durableOffset()).hasValue(102L);
  }

  @Test
  void shouldPersistTheFreezeTimeDedupWatermarkWhileFoldingContinues() {
    // given one fold up to the barrier (Zeebe position 10)
    openTask(1_000, mock(EventBridgeClient.class));
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 100L));

    // when a cut freezes at that barrier and a later position folds while it persists
    final CommitCut cut = task.freezeCut(100L);
    task.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 20L, 101L));
    cut.publish();
    cut.persist();
    cut.complete(true);

    // then the persisted watermark is the freeze-time one — a replay from the cut's offset must
    // re-fold position 20, whose fold is not part of the cut
    assertThat(durableAppliedPosition()).hasValue(10L);

    // and the next commit advances it together with that fold
    Cuts.commit(task, 101L);
    assertThat(durableAppliedPosition()).hasValue(20L);
    assertThat(openSegmentTotal()).isEqualTo(2L);
  }

  @Test
  void shouldCoverAFailedCutExactlyOnceInTheNextCommit() {
    // given a frozen cut over two folds, plus one fold past the barrier
    openTask(1_000, mock(EventBridgeClient.class));
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 100L));
    task.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L, 101L));
    final CommitCut cut = task.freezeCut(101L);
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 2000L, 12L, 102L));

    // when the cut fails (before anything became durable) and is merged back
    cut.complete(false);

    // then nothing is durable yet
    assertThat(openSegmentTotal()).isZero();
    assertThat(durableOffset()).isEmpty();
    assertThat(durableAppliedPosition()).isEmpty();

    // and the next commit covers all three folds exactly once
    Cuts.commit(task, 102L);
    assertThat(openSegmentTotal()).isEqualTo(3L);
    assertThat(durableOffset()).hasValue(102L);
    assertThat(durableAppliedPosition()).hasValue(12L);
  }

  @Test
  void shouldPublishFrozenSealedDeltasBeforeTheOffsetPersists() {
    // given a segment stride of 2 and a sealed segment: two folds in segment 0, then a record in
    // segment 1 whose accept seals segment 0 into the publisher
    openTask(2, recordingClient());
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 0L));
    task.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L, 1L));
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 2000L, 12L, 2L));

    // when the cut freezes at the barrier
    final CommitCut cut = task.freezeCut(2L);

    // then nothing left the transport at the freeze (the actor thread does no IO)
    assertThat(publishedFrames).isEmpty();

    // when the cut publishes on the IO thread
    cut.publish();

    // then the sealed segment-0 delta is durable at the shuffle before the offset advances
    assertThat(publishedFrames).hasSize(1);
    final ShuffleEnvelope envelope = new ShuffleEnvelopeCodec().decode(publishedFrames.get(0));
    assertThat(envelope.segment()).isZero();
    assertThat(envelope.chunk()).isZero();
    assertThat(durableOffset()).isEmpty();

    // and only the persist advances the offset
    cut.persist();
    cut.complete(true);
    assertThat(durableOffset()).hasValue(2L);
  }

  @Test
  void shouldPublishSealedDeltasEagerlyBeforeTheBarrierWhenEnabled() {
    // given eager shuffle publish and a sealed segment: two folds in segment 0, then a record in
    // segment 1 whose accept seals segment 0 into the publisher
    openTask(2, recordingClient(), true);
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 0L));
    task.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L, 1L));
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 2000L, 12L, 2L));

    // then the sealed segment-0 delta left for the facts topic the moment it sealed — before any
    // commit barrier exists
    assertThat(publishedFrames).hasSize(1);
    final ShuffleEnvelope eager = new ShuffleEnvelopeCodec().decode(publishedFrames.get(0));
    assertThat(eager.segment()).isZero();
    assertThat(eager.chunk()).isZero();

    // when the cut freezes at the barrier and publishes on the IO thread
    final CommitCut cut = task.freezeCut(2L);
    cut.publish();

    // then the eagerly-published frame is not re-sent — the cut only awaited its acknowledgment
    assertThat(publishedFrames).hasSize(1);
    assertThat(durableOffset()).isEmpty();

    // and only the persist advances the offset
    cut.persist();
    cut.complete(true);
    assertThat(durableOffset()).hasValue(2L);
  }

  private void openTask(final int segmentStride, final EventBridgeClient client) {
    openTask(segmentStride, client, false);
  }

  private void openTask(
      final int segmentStride, final EventBridgeClient client, final boolean eagerShufflePublish) {
    catalog = new DatasetCatalog(metadataStore);
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:frozencut-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    provider =
        RocksDbStateStoreProvider.open(
            new File(stateDir.toFile(), "stage1"), new SimpleMeterRegistry());
    final KeyValueStore<DbBytes, DbBytes> openSegments =
        provider.keyValueStore(AnalyticsColumnFamilies.OPEN_SEGMENT, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final KeyValueStore<DbInt, DbLong> appliedPositions =
        provider.keyValueStore(
            AnalyticsColumnFamilies.ZEEBE_APPLIED_POSITION, new DbInt(), new DbLong());
    final RdbmsDatasetStore datasetStore = new RdbmsDatasetStore(dataSource);
    task =
        new ProjectionStageTask(
            EB_PARTITION,
            () -> 1L,
            client,
            "facts",
            1,
            segmentStride,
            1,
            datasetStore,
            datasetStore.writer(),
            provider,
            openSegments,
            offsets,
            appliedPositions,
            catalog,
            Long.MAX_VALUE, // no reload in these tests
            eagerShufflePublish,
            ProjectionMetrics.NOOP,
            FlowMetrics.NOOP,
            StoreMetrics.NOOP,
            0L);
    task.init();
  }

  /**
   * An {@link EventBridgeClient} whose batches record their frames into {@link #publishedFrames}
   * only when actually published — the observable of the produce-before-commit ordering.
   */
  private EventBridgeClient recordingClient() {
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.newBatch()).thenAnswer(invocation -> new RecordingBatch());
    return client;
  }

  private final class RecordingBatch implements BatchPublisher {

    private final List<byte[]> frames = new ArrayList<>();

    @Override
    public BatchPublisher add(final String key, final byte[] value) {
      frames.add(value);
      return this;
    }

    @Override
    public BatchPublisher add(final byte[] key, final byte[] value) {
      frames.add(value);
      return this;
    }

    @Override
    public BatchPublisher add(final byte[] value) {
      frames.add(value);
      return this;
    }

    @Override
    public BatchPublisher add(final String value) {
      frames.add(value.getBytes(StandardCharsets.UTF_8));
      return this;
    }

    @Override
    public BatchPublisher keyed() {
      return this;
    }

    @Override
    public CompletableFuture<List<Long>> publishToTopic(final String topic, final int partitionId) {
      publishedFrames.addAll(frames);
      return CompletableFuture.completedFuture(List.of(0L, (long) frames.size()));
    }
  }

  /** Declares and stores a single-meter COUNT cube on process-instance facts. */
  private void provision(final String name) {
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder(name, FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(60_000L)
            .lateness(300_000L)
            .build();
    final long cubeId = registry.admit(declaration, Map.of(), 0L).cubeId();
    metadataStore.datasetSpecStore().create(registry.get(cubeId).orElseThrow());
  }

  /**
   * A root process-element record with the given real Zeebe coordinate {@code (ZEEBE_PARTITION,
   * zeebePosition)}, wrapped as consumed from Event Bridge partition {@link #EB_PARTITION} at
   * {@code ebOffset}.
   */
  private static SourceRecord process(
      final ProcessInstanceIntent intent,
      final long timestamp,
      final long zeebePosition,
      final long ebOffset) {
    final ProcessInstanceRecord value =
        new ProcessInstanceRecord()
            .setProcessInstanceKey(PI_KEY)
            .setProcessDefinitionKey(77L)
            .setBpmnProcessId("order")
            .setVersion(3)
            .setTenantId("<default>")
            .setElementId("order")
            .setFlowScopeKey(-1L)
            .setBpmnElementType(BpmnElementType.PROCESS);
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.PROCESS_INSTANCE)
            .intent(intent);
    final Record<?> record =
        new CopiedRecord<>(value, metadata, PI_KEY, ZEEBE_PARTITION, zeebePosition, -1L, timestamp);
    return new SourceRecord(EB_PARTITION, ebOffset, record);
  }

  /** The durably committed consumed offset of {@link #EB_PARTITION}, if any. */
  private Optional<Long> durableOffset() {
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final DbInt key = new DbInt();
    key.wrapInt(EB_PARTITION);
    return offsets.get(key).map(DbLong::getValue);
  }

  /** The durably persisted pre-fold dedup watermark of {@link #ZEEBE_PARTITION}, if any. */
  private Optional<Long> durableAppliedPosition() {
    final KeyValueStore<DbInt, DbLong> appliedPositions =
        provider.keyValueStore(
            AnalyticsColumnFamilies.ZEEBE_APPLIED_POSITION, new DbInt(), new DbLong());
    final DbInt key = new DbInt();
    key.wrapInt(ZEEBE_PARTITION);
    return appliedPositions.get(key).map(DbLong::getValue);
  }

  /**
   * The summed COUNT of the cube's checkpointed open-segment composite cells (skipping the
   * bare-group meta entry) — the number of facts durably folded so far.
   */
  private long openSegmentTotal() {
    final CompiledDataset dataset = singleDataset();
    final CompositeAggregateFunction aggregate =
        new CompositeAggregateFunction(dataset.meterBounds());
    final CompositeAccumulatorValue codec = new CompositeAccumulatorValue(dataset.meterBounds());
    final KeyValueStore<DbBytes, DbBytes> openSegments =
        provider.keyValueStore(AnalyticsColumnFamilies.OPEN_SEGMENT, new DbBytes(), new DbBytes());
    final DbBytes prefix = new DbBytes();
    prefix.wrapBytes(ByteBuffer.allocate(Integer.BYTES).putInt(dataset.streamId()).array());
    final List<Long> totals = new ArrayList<>();
    openSegments.prefixScan(
        prefix,
        (key, value) -> {
          if (key.getBytes().length == Integer.BYTES) {
            return; // the per-group (openSegment, sourcePartition) meta entry
          }
          final Object[] accumulator = codec.fromBytes(value.getBytes());
          totals.add(((Number) aggregate.getResult(accumulator)[0]).longValue());
        });
    return totals.stream().mapToLong(Long::longValue).sum();
  }

  /** The provisioned cube's compiled dataset, resolved from the live catalog. */
  private CompiledDataset singleDataset() {
    catalog.refresh();
    final List<ActiveCube> cubes = catalog.cubes();
    assertThat(cubes).hasSize(1);
    final CompiledDataset dataset = cubes.get(0).compiled();
    assertThat(dataset.meters()).hasSize(1);
    return dataset;
  }
}
