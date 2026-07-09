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
 * Persistence of the Stage-2 segment-dedup watermarks: they commit in the same atomic cut as the
 * merged cells and the facts offset and are restored at task open, so an already-admitted {@code
 * (segment, chunk)} re-delivered after a restart — e.g. a Stage-1 crash in its
 * produce-before-commit gap re-publishing the same delta as a new facts-topic append — is not
 * double-folded by the non-idempotent merge, while a genuinely new chunk still admits.
 */
final class AggregationStageTaskDedupPersistenceTest {

  private static final String PROCESS = "order-process";

  @TempDir Path stateDir;

  private TestMetadataStore metadataStore;
  private DatasetRegistry registry;
  private DatasetCatalog catalog;
  private RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private AggregationStageTask task;

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
  void shouldNotRefoldAnAlreadyAdmittedChunkAfterARestart() {
    // given a delta admitted and committed durably
    openTask();
    final CubeHandle handle = resolve();
    task.process(envelope(handle, 1L, 0));
    Cuts.commit(task, 0L);
    assertThat(durableTotal(handle)).isEqualTo(1L);

    // when the task restarts over the same store and the same (segment, chunk) is re-delivered as
    // a new facts-topic append (a producer re-emit, not a consumption replay)
    task.close();
    task = null;
    openTask();
    final CubeHandle reopened = resolve();
    task.process(envelope(reopened, 1L, 0));
    Cuts.commit(task, 1L);

    // then the restored watermarks drop it — the durable total is unchanged
    assertThat(durableTotal(reopened)).isEqualTo(1L);

    // and a genuinely new chunk still admits and folds
    task.process(envelope(reopened, 2L, 0));
    Cuts.commit(task, 2L);
    assertThat(durableTotal(reopened)).isEqualTo(2L);
  }

  private void openTask() {
    catalog = new DatasetCatalog(metadataStore);
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:dedup-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    provider =
        RocksDbStateStoreProvider.open(
            new File(stateDir.toFile(), "stage2"), new SimpleMeterRegistry());
    final KeyValueStore<DbBytes, DbBytes> cellStore =
        provider.keyValueStore(AnalyticsColumnFamilies.CUBE_CELLS, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final KeyValueStore<DbBytes, DbBytes> dedupStore =
        provider.keyValueStore(
            AnalyticsColumnFamilies.SHUFFLE_DEDUP_WATERMARK, new DbBytes(), new DbBytes());
    final RdbmsDatasetStore datasetStore = new RdbmsDatasetStore(dataSource);
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
            Long.MAX_VALUE, // no reload in these tests
            0L);
    task.init();
  }

  /** Declares and stores a single-meter COUNT cube. */
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

  /** Everything a test needs of the provisioned cube, resolved from the live catalog. */
  private record CubeHandle(int aggId, CompiledMeter meter, DimensionSchema grain) {}

  private CubeHandle resolve() {
    catalog.refresh();
    final List<ActiveCube> cubes = catalog.cubes();
    assertThat(cubes).hasSize(1);
    final ActiveCube cube = cubes.get(0);
    final List<CompiledMeter> meters = cube.compiled().meters();
    assertThat(meters).hasSize(1);
    return new CubeHandle(meters.get(0).aggId(), meters.get(0), cube.compiled().grain());
  }

  /** One AGGREGATE_DELTA/MERGE envelope with a single one-fact cell delta for window 0. */
  private ShuffleEnvelope envelope(final CubeHandle handle, final long segment, final int chunk) {
    final byte[] key =
        new DimensionKeyValue(handle.grain()).toBytes(DimensionKey.of(handle.grain(), PROCESS));
    return new ShuffleEnvelope(
        0L,
        1,
        1,
        segment,
        chunk,
        false,
        ShufflePayloadKind.AGGREGATE_DELTA,
        ShuffleOperation.MERGE,
        List.of(new CellDelta(handle.aggId(), 0L, key, oneFact(handle.meter(), segment))));
  }

  /** A one-fact COUNT accumulator, encoded the way Stage 1 ships deltas. */
  @SuppressWarnings("unchecked")
  private byte[] oneFact(final CompiledMeter meter, final long position) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) meter.bound();
    final AggregateFunction<FactRow, Object, Object> aggregate = bound.aggregate();
    final Fact fact =
        Fact.builder(FactType.PROCESS_INSTANCE)
            .field("bpmnProcessId", PROCESS)
            .eventTime(0L)
            .source(1, position)
            .build();
    final Object accumulator = aggregate.add(fact, aggregate.createAccumulator());
    return bound.accumulatorCodec().toBytes(accumulator);
  }

  /** The long result of the meter's single durable cell. */
  @SuppressWarnings("unchecked")
  private long durableTotal(final CubeHandle handle) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) handle.meter().bound();
    final KeyValueStore<DbBytes, DbBytes> cells =
        provider.keyValueStore(AnalyticsColumnFamilies.CUBE_CELLS, new DbBytes(), new DbBytes());
    final DbBytes prefix = new DbBytes();
    prefix.wrapBytes(ByteBuffer.allocate(Integer.BYTES).putInt(handle.aggId()).array());
    final List<Long> totals = new ArrayList<>();
    cells.prefixScan(
        prefix,
        (key, value) -> {
          final Object accumulator = bound.accumulatorCodec().fromBytes(value.getBytes());
          totals.add(((Number) bound.aggregate().getResult(accumulator)).longValue());
        });
    assertThat(totals).hasSize(1);
    return totals.get(0);
  }
}
