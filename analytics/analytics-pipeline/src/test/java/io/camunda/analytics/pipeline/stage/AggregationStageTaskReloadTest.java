/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeyValue;
import io.camunda.analytics.dimension.DimensionSchema;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.dimension.FactRow;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.store.rdbms.RdbmsDatasetStore;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.shuffle.CellDelta;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.eventbridge.streaming.shuffle.ShuffleOperation;
import io.camunda.eventbridge.streaming.shuffle.ShufflePayloadKind;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
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
 * Incremental-reload behavior of the Stage-2 task: a catalog change rebuilds only what changed —
 * surviving aggregations keep their state (no re-recover prefix scan), added ones are constructed
 * and recovered once, removed ones are dropped, and serving DDL runs only for added datasets.
 */
final class AggregationStageTaskReloadTest {

  private static final String PROCESS = "order-process";

  @TempDir Path stateDir;

  private TestMetadataStore metadataStore;
  private DatasetRegistry registry;
  private DatasetCatalog catalog;
  private CountingDatasetStore datasetStore;
  private RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private CountingKeyValueStore cellStore;
  private KeyValueStore<DbBytes, DbBytes> rawCells; // uncounted handle for assertions
  private AggregationStageTask task;
  private long segment;

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
            new File(stateDir.toFile(), "stage2"), new SimpleMeterRegistry());
    cellStore =
        new CountingKeyValueStore(
            provider.keyValueStore(
                AnalyticsColumnFamilies.CUBE_CELLS, new DbBytes(), new DbBytes()));
    rawCells =
        provider.keyValueStore(AnalyticsColumnFamilies.CUBE_CELLS, new DbBytes(), new DbBytes());
  }

  @AfterEach
  void tearDown() {
    if (task != null) {
      task.close(); // also closes the dataset store and the provider
    }
  }

  @Test
  void shouldKeepExistingAggregationStateWhenAddingACube() {
    // given a running task over one cube with one merged delta committed
    final CubeHandle cubeA = provision("cube-a");
    openTask();
    final CubeHandle handleA = resolve(cubeA);
    assertThat(cellStore.scans(handleA.aggId())).isEqualTo(1); // recovered once at construction
    task.process(envelope(handleA));
    Cuts.commit(task, 0L);

    // when a second cube is provisioned and the next commit reloads the topology
    final CubeHandle cubeB = provision("cube-b");
    Cuts.commit(task, 1L);

    // then only the added cube's aggregation recovered and only its DDL ran — the existing one
    // kept its wiring and in-heap state untouched
    final CubeHandle handleB = resolve(cubeB);
    assertThat(cellStore.scans(handleA.aggId())).isEqualTo(1);
    assertThat(cellStore.scans(handleB.aggId())).isEqualTo(1);
    assertThat(datasetStore.ensures(handleA.cubeId())).isEqualTo(1);
    assertThat(datasetStore.ensures(handleB.cubeId())).isEqualTo(1);

    // and a further delta folds onto the surviving cube's prior total
    task.process(envelope(handleA));
    Cuts.commit(task, 2L);
    assertThat(durableTotal(handleA)).isEqualTo(2L);
  }

  @Test
  void shouldDropARemovedCubeAndRecoverAReaddedOneFromDurableStateOnly() {
    // given a cube with one merged delta committed durably
    final CubeHandle cubeA = provision("cube-a");
    openTask();
    final CubeHandle handleA = resolve(cubeA); // resolved before the cube disappears
    task.process(envelope(handleA));
    Cuts.commit(task, 0L);
    assertThat(durableTotal(handleA)).isEqualTo(1L);

    // when the cube is removed and the next commit reloads
    metadataStore.hide(handleA.cubeId());
    Cuts.commit(task, 1L);

    // then its node is gone: a delta for its stream is dropped, the durable cell untouched
    task.process(envelope(handleA));
    Cuts.commit(task, 2L);
    assertThat(durableTotal(handleA)).isEqualTo(1L);

    // when the same id is re-added
    metadataStore.unhide(handleA.cubeId());
    Cuts.commit(task, 3L);

    // then it recovered afresh from durable state only (a second recover scan, no leaked heap
    // state) and folds forward from the durable total
    assertThat(cellStore.scans(handleA.aggId())).isEqualTo(2);
    task.process(envelope(handleA));
    Cuts.commit(task, 4L);
    assertThat(durableTotal(handleA)).isEqualTo(2L);
  }

  private void openTask() {
    catalog = new DatasetCatalog(metadataStore);
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final KeyValueStore<DbBytes, DbBytes> dedupStore =
        provider.keyValueStore(
            AnalyticsColumnFamilies.SHUFFLE_DEDUP_WATERMARK, new DbBytes(), new DbBytes());
    task =
        new AggregationStageTask(
            1,
            datasetStore,
            datasetStore.writer(),
            provider,
            cellStore,
            offsets,
            dedupStore,
            catalog,
            0L, // check the catalog at every commit
            0L);
  }

  /** Everything a test needs of a cube, captured while it is visible in the catalog. */
  private record CubeHandle(long cubeId, int aggId, CompiledMeter meter, DimensionSchema grain) {}

  /** Declares and stores a single-meter COUNT cube; returns a handle with just its id. */
  private CubeHandle provision(final String name) {
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder(name, FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(60_000L)
            .lateness(300_000L)
            .build();
    final long cubeId = registry.admit(declaration, Map.of(), 0L).cubeId();
    metadataStore.datasetSpecStore().create(registry.get(cubeId).orElseThrow());
    return new CubeHandle(cubeId, -1, null, null);
  }

  /** Resolves the provisioned cube's compiled meter/grain from the live catalog. */
  private CubeHandle resolve(final CubeHandle provisioned) {
    catalog.refresh();
    for (final ActiveCube cube : catalog.cubes()) {
      if (cube.registered().cubeId() == provisioned.cubeId()) {
        final List<CompiledMeter> meters = cube.compiled().meters();
        assertThat(meters).hasSize(1);
        return new CubeHandle(
            provisioned.cubeId(), meters.get(0).aggId(), meters.get(0), cube.compiled().grain());
      }
    }
    throw new IllegalStateException("cube " + provisioned.cubeId() + " not in the catalog");
  }

  /** One AGGREGATE_DELTA/MERGE envelope with a single one-fact cell delta for window 0. */
  private ShuffleEnvelope envelope(final CubeHandle handle) {
    final byte[] key =
        new DimensionKeyValue(handle.grain()).toBytes(DimensionKey.of(handle.grain(), PROCESS));
    return new ShuffleEnvelope(
        0L,
        1,
        1,
        ++segment,
        0,
        false,
        ShufflePayloadKind.AGGREGATE_DELTA,
        ShuffleOperation.MERGE,
        List.of(new CellDelta(handle.aggId(), 0L, key, oneFact(handle.meter()))));
  }

  /** A one-fact COUNT accumulator, encoded the way Stage 1 ships deltas. */
  @SuppressWarnings("unchecked")
  private byte[] oneFact(final CompiledMeter meter) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) meter.bound();
    final AggregateFunction<FactRow, Object, Object> aggregate = bound.aggregate();
    final Fact fact =
        Fact.builder(FactType.PROCESS_INSTANCE)
            .field("bpmnProcessId", PROCESS)
            .eventTime(0L)
            .source(1, segment)
            .build();
    final Object accumulator = aggregate.add(fact, aggregate.createAccumulator());
    return bound.accumulatorCodec().toBytes(accumulator);
  }

  /** The long result of the meter's single durable cell, read through an uncounted handle. */
  @SuppressWarnings("unchecked")
  private long durableTotal(final CubeHandle handle) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) handle.meter().bound();
    final DbBytes prefix = new DbBytes();
    prefix.wrapBytes(ByteBuffer.allocate(Integer.BYTES).putInt(handle.aggId()).array());
    final List<Long> totals = new ArrayList<>();
    rawCells.prefixScan(
        prefix,
        (key, value) -> {
          final Object accumulator = bound.accumulatorCodec().fromBytes(value.getBytes());
          totals.add(((Number) bound.aggregate().getResult(accumulator)).longValue());
        });
    assertThat(totals).hasSize(1);
    return totals.get(0);
  }
}
