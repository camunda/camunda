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

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.store.rdbms.RdbmsDatasetStore;
import io.camunda.eventbridge.client.EventBridgeClient;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Stage-1 pre-fold dedup (ADR 0007): a per-Zeebe-partition applied-position high-watermark
 * skips a producer duplicate — the same Zeebe record re-appended at a later Event Bridge offset —
 * before the fold, while a crash-replay of not-yet-committed records still folds them exactly once
 * (the watermark commits in the same atomic cut as the topology state and the consumed offset).
 */
final class ProjectionStageTaskDedupTest {

  private static final long PI_KEY = 123L;
  private static final int ZEEBE_PARTITION = 3;
  private static final int EB_PARTITION = 1;

  @TempDir Path stateDir;

  private TestMetadataStore metadataStore;
  private DatasetRegistry registry;
  private DatasetCatalog catalog;
  private RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private ProjectionStageTask task;

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
  void shouldFoldAProducerDuplicateOnlyOnce() {
    // given a running task that folded an instance activation and completion
    openTask();
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 100L));
    task.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L, 101L));

    // when the exporter re-appends the same Zeebe records at later Event Bridge offsets (a retried
    // batch under at-least-once delivery)
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 102L));
    task.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L, 103L));
    task.commit(103L);

    // then each record folded exactly once — the open segment counts the two distinct facts, not
    // the duplicates
    assertThat(openSegmentTotal()).isEqualTo(2L);
  }

  @Test
  void shouldRefoldOnlyNotYetCommittedRecordsAfterARestart() {
    // given a committed activation and an uncommitted completion when the task crashes
    openTask();
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 100L));
    task.commit(100L);
    task.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L, 101L));
    task.close();
    task = null;

    // when the task restarts over the same state and the runtime replays from the committed offset
    openTask();
    assertThat(task.restore()).isEqualTo(100L);
    task.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L, 101L));
    task.commit(101L);

    // then the replayed completion folded (its fold was not part of the cut) — exactly once — on
    // top of the recovered activation fold
    assertThat(openSegmentTotal()).isEqualTo(2L);
  }

  private void openTask() {
    catalog = new DatasetCatalog(metadataStore);
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:dedup-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
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
            mock(EventBridgeClient.class), // never used: nothing seals below the segment stride
            "facts",
            1,
            1_000,
            1,
            datasetStore,
            datasetStore.writer(),
            provider,
            openSegments,
            offsets,
            appliedPositions,
            catalog,
            Long.MAX_VALUE, // no reload in these tests
            false,
            0L);
    task.init();
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
   * {@code ebOffset} — a producer duplicate is the same Zeebe coordinate at a later offset.
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

  /**
   * The summed COUNT of the single meter's checkpointed open-segment cells (skipping the bare-group
   * meta entry) — the number of facts folded so far.
   */
  private long openSegmentTotal() {
    final CompiledMeter meter = singleMeter();
    @SuppressWarnings("unchecked")
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) meter.bound();
    final KeyValueStore<DbBytes, DbBytes> openSegments =
        provider.keyValueStore(AnalyticsColumnFamilies.OPEN_SEGMENT, new DbBytes(), new DbBytes());
    final DbBytes prefix = new DbBytes();
    prefix.wrapBytes(ByteBuffer.allocate(Integer.BYTES).putInt(meter.aggId()).array());
    final List<Long> totals = new ArrayList<>();
    openSegments.prefixScan(
        prefix,
        (key, value) -> {
          if (key.getBytes().length == Integer.BYTES) {
            return; // the per-group (openSegment, sourcePartition) meta entry
          }
          final Object accumulator = bound.accumulatorCodec().fromBytes(value.getBytes());
          totals.add(((Number) bound.aggregate().getResult(accumulator)).longValue());
        });
    return totals.stream().mapToLong(Long::longValue).sum();
  }

  /** The provisioned cube's single compiled meter, resolved from the live catalog. */
  private CompiledMeter singleMeter() {
    catalog.refresh();
    final List<ActiveCube> cubes = catalog.cubes();
    assertThat(cubes).hasSize(1);
    final List<CompiledMeter> meters = cubes.get(0).compiled().meters();
    assertThat(meters).hasSize(1);
    return meters.get(0);
  }
}
