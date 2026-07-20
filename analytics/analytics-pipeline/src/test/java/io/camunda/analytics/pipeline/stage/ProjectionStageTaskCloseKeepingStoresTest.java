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

import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.projection.ProjectionMetrics;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.store.rdbms.RdbmsDatasetStore;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.internals.FlowMetrics;
import io.camunda.eventbridge.streaming.internals.StoreMetrics;
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
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ProjectionStageTask#closeKeepingStores()} (event-bridge-streaming ADR 0009 decision 6 /
 * consumer-groups ADR 0006 decision 1's demotion lifecycle): releases everything this task owns
 * except its {@link RocksDbStateStoreProvider}, so a controller demoting this task to STANDBY can
 * keep tailing the changelog into the same store, and a subsequent promotion's freshly constructed
 * task sees exactly what this one persisted — the promoted fold cannot tell itself apart from a
 * restart.
 */
final class ProjectionStageTaskCloseKeepingStoresTest {

  private static final int EB_PARTITION = 1;

  @TempDir Path stateDir;

  private TestMetadataStore metadataStore;
  private DatasetRegistry registry;
  private DatasetCatalog catalog;
  private RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;

  @Test
  void shouldKeepTheProviderOpenAndLetANewTaskSeeThePersistedState() {
    // given a task that folded and durably committed one activation
    final ProjectionStageTask first = openTask();
    first.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 100L));
    Cuts.commit(first, 100L);

    // when demoted (closeKeepingStores, not close)
    first.closeKeepingStores();

    // then the provider is still open and usable directly
    assertThat(durableOffset()).isEqualTo(100L);

    // and — a second task built against the SAME provider (as a promoted fold would be) sees the
    // exact durable offset the first task committed, exactly as a restart would restore it
    final ProjectionStageTask second = reopenTaskAgainstSameProvider();
    try {
      assertThat(second.restore()).isEqualTo(100L);
    } finally {
      second.close();
    }
  }

  @Test
  void shouldStillCloseTheProviderOnAFullClose() throws Exception {
    // given a task that folded and durably committed one activation
    final ProjectionStageTask task = openTask();
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 100L));
    Cuts.commit(task, 100L);
    final File dir = new File(stateDir.toFile(), "stage1");

    // when fully closed
    task.close();

    // then the provider released its RocksDB lock on the directory — a fresh provider can open the
    // same path (an unreleased lock would make this open fail) and sees the durable row from disk
    try (var reopened =
        RocksDbStateStoreProvider.<AnalyticsColumnFamilies>open(dir, new SimpleMeterRegistry())) {
      final KeyValueStore<DbInt, DbLong> offsets =
          reopened.keyValueStore(
              AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
      final DbInt key = new DbInt();
      key.wrapInt(EB_PARTITION);
      assertThat(offsets.get(key).map(DbLong::getValue)).hasValue(100L);
    }
  }

  private ProjectionStageTask openTask() {
    metadataStore = new TestMetadataStore();
    registry = new DatasetRegistry();
    provision("cube-a");
    catalog = new DatasetCatalog(metadataStore);
    provider =
        RocksDbStateStoreProvider.open(
            new File(stateDir.toFile(), "stage1"), new SimpleMeterRegistry());
    return buildTask(provider);
  }

  /**
   * Reopens a task against the SAME (still-open) provider the first task left behind — the shape a
   * promoted fold takes over {@link
   * io.camunda.eventbridge.streaming.changelog.PartitionRoleController}.
   */
  private ProjectionStageTask reopenTaskAgainstSameProvider() {
    final ProjectionStageTask task = buildTask(provider);
    task.init();
    return task;
  }

  private ProjectionStageTask buildTask(
      final RocksDbStateStoreProvider<AnalyticsColumnFamilies> p) {
    final EventBridgeClient client = mock(EventBridgeClient.class);
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:stage1-close-keeping-stores-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    final KeyValueStore<DbBytes, DbBytes> openSegments =
        p.keyValueStore(AnalyticsColumnFamilies.OPEN_SEGMENT, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> offsets =
        p.keyValueStore(AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final KeyValueStore<DbInt, DbLong> appliedPositions =
        p.keyValueStore(AnalyticsColumnFamilies.ZEEBE_APPLIED_POSITION, new DbInt(), new DbLong());
    final RdbmsDatasetStore datasetStore = new RdbmsDatasetStore(dataSource);
    final ProjectionStageTask task =
        new ProjectionStageTask(
            EB_PARTITION,
            () -> 1L,
            client,
            "facts",
            1,
            1_000,
            1,
            datasetStore,
            datasetStore.writer(),
            p,
            openSegments,
            offsets,
            appliedPositions,
            catalog,
            Long.MAX_VALUE,
            false,
            ProjectionMetrics.NOOP,
            FlowMetrics.NOOP,
            StoreMetrics.NOOP,
            0L);
    task.init();
    return task;
  }

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

  private static SourceRecord process(
      final ProcessInstanceIntent intent,
      final long timestamp,
      final long zeebePosition,
      final long ebOffset) {
    final ProcessInstanceRecord value =
        new ProcessInstanceRecord()
            .setProcessInstanceKey(123L)
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
        new CopiedRecord<>(value, metadata, 123L, 3, zeebePosition, -1L, timestamp);
    return new SourceRecord(EB_PARTITION, ebOffset, record);
  }

  private Long durableOffset() {
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final DbInt key = new DbInt();
    key.wrapInt(EB_PARTITION);
    return offsets.get(key).map(DbLong::getValue).orElse(null);
  }
}
