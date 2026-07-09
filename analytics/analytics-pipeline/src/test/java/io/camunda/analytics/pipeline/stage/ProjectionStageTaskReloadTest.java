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
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.store.rdbms.RdbmsDatasetStore;
import io.camunda.analytics.table.ProcessDefinitionSink;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Incremental-reload behavior of the Stage-1 task: a catalog change rebuilds only what changed —
 * surviving meters keep their sealing aggregations (no re-recover prefix scan of the open-segment
 * store), added ones are constructed and recovered once, removed ones are dropped, and serving DDL
 * (including the built-in process-definitions table) runs only for added datasets.
 */
final class ProjectionStageTaskReloadTest {

  @TempDir Path stateDir;

  private TestMetadataStore metadataStore;
  private DatasetRegistry registry;
  private DatasetCatalog catalog;
  private CountingDatasetStore datasetStore;
  private RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private CountingKeyValueStore openSegments;
  private ProjectionStageTask task;

  @BeforeEach
  void setUp() {
    metadataStore = new TestMetadataStore();
    registry = new DatasetRegistry();
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:reload-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    datasetStore = new CountingDatasetStore(new RdbmsDatasetStore(dataSource));
    provider =
        RocksDbStateStoreProvider.open(
            new File(stateDir.toFile(), "stage1"), new SimpleMeterRegistry());
    openSegments =
        new CountingKeyValueStore(
            provider.keyValueStore(
                AnalyticsColumnFamilies.OPEN_SEGMENT, new DbBytes(), new DbBytes()));
  }

  @AfterEach
  void tearDown() {
    if (task != null) {
      task.close(); // also closes the dataset store and the provider
    }
  }

  @Test
  void shouldRecoverAndProvisionOnlyTheAddedCubeOnReload() {
    // given a running task over one cube (plus one raw table)
    final long cubeA = provision("cube-a");
    final long tableA = provisionTable("table-a");
    openTask();
    final int streamA = streamIdOf(cubeA);
    assertThat(openSegments.scans(streamA)).isEqualTo(1); // recovered once at construction
    assertThat(datasetStore.ensures(cubeA)).isEqualTo(1);
    assertThat(datasetStore.tableEnsures(tableA)).isEqualTo(1);
    assertThat(datasetStore.tableEnsures(ProcessDefinitionSink.TABLE.cubeId())).isEqualTo(1);

    // when a second cube is provisioned and the next commit reloads the topology
    final long cubeB = provision("cube-b");
    task.commit(0L);

    // then only the added cube's aggregation recovered and only its DDL ran; the surviving cube,
    // the raw table, and the built-in definitions table were not re-provisioned
    final int streamB = streamIdOf(cubeB);
    assertThat(openSegments.scans(streamA)).isEqualTo(1);
    assertThat(openSegments.scans(streamB)).isEqualTo(1);
    assertThat(datasetStore.ensures(cubeA)).isEqualTo(1);
    assertThat(datasetStore.ensures(cubeB)).isEqualTo(1);
    assertThat(datasetStore.tableEnsures(tableA)).isEqualTo(1);
    assertThat(datasetStore.tableEnsures(ProcessDefinitionSink.TABLE.cubeId())).isEqualTo(1);
  }

  @Test
  void shouldDropARemovedCubeAndRecoverAReaddedOneFromDurableStateOnly() {
    // given a running task over one cube
    final long cubeA = provision("cube-a");
    openTask();
    final int streamA = streamIdOf(cubeA);
    assertThat(openSegments.scans(streamA)).isEqualTo(1);

    // when the cube is removed and the next commit reloads
    metadataStore.hide(cubeA);
    task.commit(0L);

    // and the same id is re-added on a later commit
    metadataStore.unhide(cubeA);
    task.commit(1L);

    // then its aggregation was reconstructed and recovered afresh from the durable open segment
    // (a second recover scan — the removed incarnation's heap state did not leak)
    assertThat(openSegments.scans(streamA)).isEqualTo(2);
  }

  private void openTask() {
    catalog = new DatasetCatalog(metadataStore);
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final KeyValueStore<DbInt, DbLong> appliedPositions =
        provider.keyValueStore(
            AnalyticsColumnFamilies.ZEEBE_APPLIED_POSITION, new DbInt(), new DbLong());
    task =
        new ProjectionStageTask(
            1,
            mock(EventBridgeClient.class), // never used: no records flow in these tests
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
            0L, // check the catalog at every commit
            false,
            0L);
  }

  /** Declares and stores a single-meter COUNT cube; returns its cubeId. */
  private long provision(final String name) {
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder(name, FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(60_000L)
            .lateness(300_000L)
            .build();
    final long cubeId = registry.admit(declaration, Map.of(), 0L).cubeId();
    metadataStore.datasetSpecStore().create(registry.get(cubeId).orElseThrow());
    return cubeId;
  }

  /** Declares and stores a projected (raw) table dataset; returns its cubeId. */
  private long provisionTable(final String name) {
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder(name, FactType.PROCESS_INSTANCE)
            .asTable("processInstanceKey")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .build();
    final long cubeId = registry.admit(declaration, Map.of(), 0L).cubeId();
    metadataStore.datasetSpecStore().create(registry.get(cubeId).orElseThrow());
    return cubeId;
  }

  /** The given cube's shuffle streamId — its open-segment store group — from the live catalog. */
  private int streamIdOf(final long cubeId) {
    catalog.refresh();
    for (final ActiveCube cube : catalog.cubes()) {
      if (cube.registered().cubeId() == cubeId) {
        assertThat(cube.compiled().meters()).hasSize(1);
        return cube.compiled().streamId();
      }
    }
    throw new IllegalStateException("cube " + cubeId + " not in the catalog");
  }
}
