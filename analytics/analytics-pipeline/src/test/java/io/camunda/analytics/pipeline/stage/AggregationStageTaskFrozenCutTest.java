/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeyValue;
import io.camunda.analytics.dimension.DimensionSchema;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.CompositeAggregateFunction;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.store.rdbms.RdbmsDatasetStore;
import io.camunda.eventbridge.streaming.CommitCut;
import io.camunda.eventbridge.streaming.shuffle.CellDelta;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.eventbridge.streaming.shuffle.ShuffleOperation;
import io.camunda.eventbridge.streaming.shuffle.ShufflePayloadKind;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Stage-2 frozen commit cut (streaming ADR 0005): {@code freezeCut} detaches the cut at the
 * barrier — the persisted cells and dedup watermarks describe exactly the admissions up to the
 * barrier, no matter what the task merges while the cut publishes and persists — a failed persist
 * rolls back and is covered exactly once by the next commit, and the serving rows are durable
 * before the offset advances.
 */
final class AggregationStageTaskFrozenCutTest {

  private static final String PROCESS = "order-process";

  @TempDir Path stateDir;

  private TestMetadataStore metadataStore;
  private DatasetRegistry registry;
  private DatasetCatalog catalog;
  private RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private DataSource dataSource;
  private FailingOnceKeyValueStore<DbBytes, DbBytes> dedupStoreWrapper;
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
  void shouldNotIncludeMergesAfterTheFreezeInThePersistedCut() {
    // given one admitted delta up to the barrier
    openTask();
    final CubeHandle handle = resolve();
    task.process(envelope(handle, 1L, 0));

    // when a cut freezes at that barrier and another delta merges while it persists
    final CommitCut cut = task.freezeCut(0L);
    task.process(envelope(handle, 2L, 0));
    cut.publish();
    cut.persist();
    cut.complete(true);

    // then the durable cut holds exactly the pre-barrier merge and its offset
    assertThat(durableTotal(handle)).isEqualTo(1L);
    assertThat(durableOffset()).hasValue(0L);

    // and the next commit covers the post-barrier merge
    Cuts.commit(task, 1L);
    assertThat(durableTotal(handle)).isEqualTo(2L);
    assertThat(durableOffset()).hasValue(1L);
  }

  @Test
  void shouldPersistTheFreezeTimeDedupWatermarkWhileAdmissionsContinue() {
    // given one admission (segment 1) up to the barrier
    openTask();
    final CubeHandle handle = resolve();
    task.process(envelope(handle, 1L, 0));

    // when a cut freezes at that barrier and segment 2 is admitted while it persists
    final CommitCut cut = task.freezeCut(0L);
    task.process(envelope(handle, 2L, 0));
    cut.publish();
    cut.persist();
    cut.complete(true);

    // then the persisted watermark is the freeze-time one (segment 1): the segment-2 fold is not
    // in the cut, so a replay from the cut's offset must re-admit it rather than drop it
    assertThat(durableDedupSegment(handle)).hasValue(1L);

    // and the next commit persists the segment-2 admission together with its fold
    Cuts.commit(task, 1L);
    assertThat(durableDedupSegment(handle)).hasValue(2L);
  }

  @Test
  void shouldRefoldAPostFreezeAdmissionAfterARestartFromTheCut() {
    // given a cut frozen at offset 0 that persisted while segment 2 was admitted past the barrier
    openTask();
    final CubeHandle handle = resolve();
    task.process(envelope(handle, 1L, 0));
    final CommitCut cut = task.freezeCut(0L);
    task.process(envelope(handle, 2L, 0));
    cut.publish();
    cut.persist();
    cut.complete(true);

    // when the task crashes before the next commit and restarts over the cut's state
    task.close();
    task = null;
    openTask();
    final CubeHandle reopened = resolve();
    assertThat(task.restore()).isEqualTo(0L);

    // and the runtime replays the record after the cut's offset — the same segment-2 delta
    task.process(envelope(reopened, 2L, 0));
    Cuts.commit(task, 1L);

    // then the replayed delta folds exactly once: the freeze-time watermark did not cover it (a
    // live-at-persist-time snapshot would have, silently losing this fold)
    assertThat(durableTotal(reopened)).isEqualTo(2L);
  }

  @Test
  void shouldCoverAFailedPersistExactlyOnceInTheNextCommit() {
    // given a cut whose persist fails mid-transaction (the dedup write throws after the offset
    // write already went into the transaction)
    openTask();
    final CubeHandle handle = resolve();
    task.process(envelope(handle, 1L, 0));
    final CommitCut cut = task.freezeCut(0L);
    task.process(envelope(handle, 2L, 0));
    cut.publish();
    dedupStoreWrapper.failNextPut();
    assertThatThrownBy(cut::persist).isInstanceOf(RuntimeException.class);
    cut.complete(false);

    // then the transaction rolled back — nothing is durable, not even the offset written before
    // the failure
    assertThat(durableTotal(handle)).isZero();
    assertThat(durableOffset()).isEmpty();
    assertThat(durableDedupSegment(handle)).isEmpty();

    // and the next commit covers both merges and both admissions exactly once
    Cuts.commit(task, 1L);
    assertThat(durableTotal(handle)).isEqualTo(2L);
    assertThat(durableOffset()).hasValue(1L);
    assertThat(durableDedupSegment(handle)).hasValue(2L);
  }

  @Test
  void shouldFoldAReDeliveredEagerlyPublishedSegmentExactlyOnce() {
    // given a segment delta that Stage 1 published eagerly at its seal and that this task folded
    // and committed — then Stage 1 crashed before its (never-completed) cut persisted, so its
    // replay re-publishes the same segment at a later facts offset
    openTask();
    final CubeHandle handle = resolve();
    task.process(envelope(handle, 1L, 0));
    Cuts.commit(task, 0L);

    // when this task restarts over its cut and the runtime delivers the republished duplicate
    task.close();
    task = null;
    openTask();
    final CubeHandle reopened = resolve();
    assertThat(task.restore()).isEqualTo(0L);
    task.process(envelope(reopened, 1L, 0));
    Cuts.commit(task, 1L);

    // then the segment folded exactly once: the persisted dedup watermark absorbs the re-delivery
    assertThat(durableTotal(reopened)).isEqualTo(1L);
    assertThat(durableOffset()).hasValue(1L);
  }

  @Test
  void shouldFlushFrozenServingRowsBeforeTheOffsetPersists() {
    // given one merged delta frozen into a cut
    openTask();
    final CubeHandle handle = resolve();
    task.process(envelope(handle, 1L, 0));
    final CommitCut cut = task.freezeCut(0L);

    // then nothing reached the serving store at the freeze (the actor thread does no IO)
    assertThat(servingCellCount(handle)).isZero();

    // when the cut publishes on the IO thread
    cut.publish();

    // then the frozen serving row is durable before the offset advances
    assertThat(servingCellCount(handle)).isEqualTo(1L);
    assertThat(durableOffset()).isEmpty();

    // and only the persist advances the offset
    cut.persist();
    cut.complete(true);
    assertThat(durableOffset()).hasValue(0L);
  }

  private void openTask() {
    catalog = new DatasetCatalog(metadataStore);
    final JdbcDataSource h2 = new JdbcDataSource();
    h2.setURL("jdbc:h2:mem:frozencut-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    dataSource = h2;
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
    final KeyValueStore<DbBytes, DbBytes> parkedStore =
        provider.keyValueStore(AnalyticsColumnFamilies.PARKED_DELTAS, new DbBytes(), new DbBytes());
    final RdbmsDatasetStore datasetStore = new RdbmsDatasetStore(dataSource);
    dedupStoreWrapper = new FailingOnceKeyValueStore<>(dedupStore);
    task =
        new AggregationStageTask(
            1,
            () -> 1L,
            datasetStore,
            datasetStore.writer(),
            provider,
            cellStore,
            offsets,
            dedupStoreWrapper,
            parkedStore,
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
  private record CubeHandle(CompiledDataset dataset) {

    long cubeId() {
      return dataset.cubeId();
    }

    int streamId() {
      return dataset.streamId();
    }

    int cellGroup() {
      return dataset.finestTier().cellGroup();
    }

    DimensionSchema grain() {
      return dataset.grain();
    }
  }

  private CubeHandle resolve() {
    catalog.refresh();
    final List<ActiveCube> cubes = catalog.cubes();
    assertThat(cubes).hasSize(1);
    final ActiveCube cube = cubes.get(0);
    assertThat(cube.compiled().meters()).hasSize(1);
    return new CubeHandle(cube.compiled());
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
        List.of(new CellDelta(handle.streamId(), 0L, key, oneFact(handle.dataset(), segment))));
  }

  /** A one-fact composite COUNT accumulator, encoded the way Stage 1 ships deltas. */
  private byte[] oneFact(final CompiledDataset dataset, final long position) {
    final CompositeAggregateFunction aggregate =
        new CompositeAggregateFunction(dataset.meterBounds());
    final Fact fact =
        Fact.builder(FactType.PROCESS_INSTANCE)
            .field("bpmnProcessId", PROCESS)
            .eventTime(0L)
            .source(1, position)
            .build();
    final Object[] accumulator = aggregate.add(fact, aggregate.createAccumulator());
    return new CompositeAccumulatorValue(dataset.meterBounds()).toBytes(accumulator);
  }

  /** The long COUNT result of the cube's durable composite cells, or 0 when none is persisted. */
  private long durableTotal(final CubeHandle handle) {
    final CompositeAggregateFunction aggregate =
        new CompositeAggregateFunction(handle.dataset().meterBounds());
    final CompositeAccumulatorValue codec =
        new CompositeAccumulatorValue(handle.dataset().meterBounds());
    final KeyValueStore<DbBytes, DbBytes> cells =
        provider.keyValueStore(AnalyticsColumnFamilies.CUBE_CELLS, new DbBytes(), new DbBytes());
    final DbBytes prefix = new DbBytes();
    prefix.wrapBytes(ByteBuffer.allocate(Integer.BYTES).putInt(handle.cellGroup()).array());
    final List<Long> totals = new ArrayList<>();
    cells.prefixScan(
        prefix,
        (key, value) -> {
          final Object[] accumulator = codec.fromBytes(value.getBytes());
          totals.add(((Number) aggregate.getResult(accumulator)[0]).longValue());
        });
    return totals.stream().mapToLong(Long::longValue).sum();
  }

  /** The durably committed facts offset of this task's partition, if any. */
  private Optional<Long> durableOffset() {
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final DbInt key = new DbInt();
    key.wrapInt(1);
    return offsets.get(key).map(DbLong::getValue);
  }

  /** The durably persisted dedup watermark segment of the cube's shuffle stream, if any. */
  private Optional<Long> durableDedupSegment(final CubeHandle handle) {
    final KeyValueStore<DbBytes, DbBytes> dedupStore =
        provider.keyValueStore(
            AnalyticsColumnFamilies.SHUFFLE_DEDUP_WATERMARK, new DbBytes(), new DbBytes());
    final KeyValueStore<DbBytes, DbBytes> parkedStore =
        provider.keyValueStore(AnalyticsColumnFamilies.PARKED_DELTAS, new DbBytes(), new DbBytes());
    final DbBytes key = new DbBytes();
    key.wrapBytes(
        ByteBuffer.allocate(2 * Integer.BYTES).putInt(1).putInt(handle.streamId()).array());
    return dedupStore.get(key).map(value -> ByteBuffer.wrap(value.getBytes()).getLong());
  }

  /** How many cube cells the serving store holds durably — the produce-before-commit observable. */
  private long servingCellCount(final CubeHandle handle) {
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement();
        final ResultSet result =
            statement.executeQuery("SELECT COUNT(*) FROM dataset_" + handle.cubeId())) {
      result.next();
      return result.getLong(1);
    } catch (final SQLException e) {
      throw new IllegalStateException("failed to count serving cells", e);
    }
  }

  /**
   * A {@link KeyValueStore} wrapper whose next {@link #put} throws once — armed by a test to make a
   * cut's persist fail inside its transaction so the rollback and merge-back paths are exercised
   * end to end.
   */
  private static final class FailingOnceKeyValueStore<K extends DbKey, V extends DbValue>
      implements KeyValueStore<K, V> {

    private final KeyValueStore<K, V> delegate;
    private boolean failNextPut;

    FailingOnceKeyValueStore(final KeyValueStore<K, V> delegate) {
      this.delegate = delegate;
    }

    void failNextPut() {
      failNextPut = true;
    }

    @Override
    public Optional<V> get(final K key) {
      return delegate.get(key);
    }

    @Override
    public boolean exists(final K key) {
      return delegate.exists(key);
    }

    @Override
    public void prefixScan(final DbKey prefix, final BiConsumer<K, V> visitor) {
      delegate.prefixScan(prefix, visitor);
    }

    @Override
    public void prefixScanKeys(final DbKey prefix, final Consumer<K> visitor) {
      delegate.prefixScanKeys(prefix, visitor);
    }

    @Override
    public void forEach(final BiConsumer<K, V> visitor) {
      delegate.forEach(visitor);
    }

    @Override
    public void put(final K key, final V value) {
      if (failNextPut) {
        failNextPut = false;
        throw new IllegalStateException("injected durable-write failure");
      }
      delegate.put(key, value);
    }

    @Override
    public void delete(final K key) {
      delegate.delete(key);
    }
  }
}
