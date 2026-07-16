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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeyValue;
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
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import io.camunda.eventbridge.streaming.CommitCut;
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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The changelog stage wired into Stage 2 (streaming ADR 0009 Decisions 1/2): {@code publish()}
 * appends the frozen delta's changelog records before the serving/state transaction, and {@code
 * persist()} durably records the broker-assigned position alongside the state delta.
 */
final class AggregationStageTaskChangelogTest {

  private static final String PROCESS = "order-process";
  private static final String CHANGELOG_TOPIC = "analytics-stage2-changelog";

  @TempDir Path stateDir;

  private TestMetadataStore metadataStore;
  private DatasetRegistry registry;
  private DatasetCatalog catalog;
  private RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private DataSource dataSource;
  private AggregationStageTask task;

  @AfterEach
  void tearDown() {
    if (task != null) {
      task.close();
    }
  }

  @Test
  void shouldPublishTheFrozenDeltaThenTheMarkerBeforePersistingTheChangelogPosition() {
    // given a changelog-enabled task with one merged delta frozen into a cut
    final EventBridgeClient client = mock(EventBridgeClient.class);
    final BatchPublisher batch = mock(BatchPublisher.class, RETURNS_SELF);
    when(client.newBatch()).thenReturn(batch);
    when(batch.publishToTopic(CHANGELOG_TOPIC, 1))
        .thenReturn(CompletableFuture.completedFuture(List.of(100L, 101L)));
    openTask(client);
    final CubeHandle handle = resolve();
    task.process(envelope(handle, 1L, 0));
    final CommitCut cut = task.freezeCut(0L);

    // when the cut publishes
    cut.publish();

    // then the changelog batch was keyed, carried the frozen put, and the client saw it before
    // persist() (which runs only afterwards, by the cut protocol)
    verify(batch).keyed();
    verify(batch).publishToTopic(CHANGELOG_TOPIC, 1);
    assertThat(changelogPosition()).isEmpty(); // not persisted yet — publish() precedes persist()

    // when the cut persists
    cut.persist();
    cut.complete(true);

    // then the changelog position (the marker's assigned broker position) is durable alongside
    // the state delta and the offset
    assertThat(changelogPosition()).hasValue(101L);
  }

  @Test
  void shouldNotTouchTheChangelogWhenDisabled() {
    // given a task opened without the changelog (today's default-off path, and every existing
    // AggregationStageTask test)
    openTaskWithoutChangelog();
    final CubeHandle handle = resolve();
    task.process(envelope(handle, 1L, 0));
    final CommitCut cut = task.freezeCut(0L);

    // when / then publishing and persisting never touch a changelog position
    cut.publish();
    cut.persist();
    cut.complete(true);
    assertThat(changelogPosition()).isEmpty();
  }

  @Test
  void shouldFailTheCutWhenTheChangelogPublishFails() {
    // given a changelog whose publish is rejected by the broker
    final EventBridgeClient client = mock(EventBridgeClient.class);
    final BatchPublisher batch = mock(BatchPublisher.class, RETURNS_SELF);
    when(client.newBatch()).thenReturn(batch);
    when(batch.publishToTopic(any(), anyInt()))
        .thenReturn(
            CompletableFuture.failedFuture(new IllegalStateException("injected broker failure")));
    openTask(client);
    final CubeHandle handle = resolve();
    task.process(envelope(handle, 1L, 0));
    final CommitCut cut = task.freezeCut(0L);

    // when / then publish() propagates the failure — this task's cut composition never calls
    // persist() itself, so nothing local is committed and no position is ever recorded
    assertThatThrownBy(cut::publish).isInstanceOf(CompletionException.class);
    assertThat(changelogPosition()).isEmpty();
    assertThat(durableOffset()).isEmpty();
  }

  private void openTask(final EventBridgeClient client) {
    open("stage2", client, CHANGELOG_TOPIC, true);
  }

  private void openTaskWithoutChangelog() {
    open("stage2-off", null, null, false);
  }

  private void open(
      final String dir,
      final EventBridgeClient client,
      final String changelogTopic,
      final boolean changelogEnabled) {
    metadataStore = new TestMetadataStore();
    registry = new DatasetRegistry();
    provision("cube-a");
    catalog = new DatasetCatalog(metadataStore);
    final JdbcDataSource h2 = new JdbcDataSource();
    h2.setURL("jdbc:h2:mem:changelog-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    dataSource = h2;
    provider =
        RocksDbStateStoreProvider.open(new File(stateDir.toFile(), dir), new SimpleMeterRegistry());
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
    task =
        new AggregationStageTask(
            1,
            () -> 1L,
            datasetStore,
            datasetStore.writer(),
            provider,
            cellStore,
            offsets,
            dedupStore,
            parkedStore,
            catalog,
            null,
            Long.MAX_VALUE,
            0L,
            client,
            changelogTopic,
            changelogEnabled);
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

  private record CubeHandle(CompiledDataset dataset) {
    int streamId() {
      return dataset.streamId();
    }

    DimensionKeyValue keyCodec() {
      return new DimensionKeyValue(dataset.grain());
    }
  }

  private CubeHandle resolve() {
    catalog.refresh();
    final List<ActiveCube> cubes = catalog.cubes();
    assertThat(cubes).hasSize(1);
    return new CubeHandle(cubes.get(0).compiled());
  }

  private ShuffleEnvelope envelope(final CubeHandle handle, final long segment, final int chunk) {
    final byte[] key =
        handle.keyCodec().toBytes(DimensionKey.of(handle.dataset().grain(), PROCESS));
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

  private Optional<Long> changelogPosition() {
    final KeyValueStore<DbInt, DbLong> positions =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CHANGELOG_POSITION, new DbInt(), new DbLong());
    final DbInt key = new DbInt();
    key.wrapInt(1);
    return positions.get(key).map(DbLong::getValue);
  }

  private Optional<Long> durableOffset() {
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final DbInt key = new DbInt();
    key.wrapInt(1);
    return offsets.get(key).map(DbLong::getValue);
  }
}
