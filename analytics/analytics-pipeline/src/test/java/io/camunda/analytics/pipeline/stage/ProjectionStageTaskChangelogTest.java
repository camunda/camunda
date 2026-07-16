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
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import io.camunda.eventbridge.streaming.CommitCut;
import io.camunda.eventbridge.streaming.changelog.ChangelogKeyEnvelope;
import io.camunda.eventbridge.streaming.changelog.ChangelogMarker;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The changelog stage wired into Stage 1 (streaming ADR 0009 Decisions 1/2): {@code publish()}
 * appends the frozen delta's changelog records — across every base-projection column family, the
 * sealing aggregations' open segments and the pre-fold dedup watermarks, enveloped ({@link
 * ChangelogKeyEnvelope}) since several column families share one topic — before the state
 * transaction, and {@code persist()} durably records the broker-assigned position alongside it.
 */
final class ProjectionStageTaskChangelogTest {

  private static final String CHANGELOG_TOPIC = "analytics-stage1-changelog";
  private static final long PI_KEY = 123L;
  private static final int ZEEBE_PARTITION = 3;
  private static final int OTHER_ZEEBE_PARTITION = 4;
  private static final int EB_PARTITION = 1;

  @TempDir Path stateDir;

  private TestMetadataStore metadataStore;
  private DatasetRegistry registry;
  private DatasetCatalog catalog;
  private RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider;
  private ProjectionStageTask task;

  @AfterEach
  void tearDown() {
    if (task != null) {
      task.close();
    }
  }

  @Test
  void shouldPublishTheFrozenDeltaThenTheMarkerBeforePersistingTheChangelogPosition() {
    // given a changelog-enabled task with one fold frozen into a cut
    final EventBridgeClient client = mock(EventBridgeClient.class);
    final BatchPublisher batch = mock(BatchPublisher.class, RETURNS_SELF);
    when(client.newBatch()).thenReturn(batch);
    when(batch.publishToTopic(CHANGELOG_TOPIC, EB_PARTITION))
        .thenReturn(CompletableFuture.completedFuture(List.of(100L, 101L)));
    openTask(client, true);
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 100L));
    final CommitCut cut = task.freezeCut(100L);

    // when the cut publishes
    cut.publish();

    // then the changelog batch was keyed and carried the frozen delta, seen before persist() (the
    // cut protocol runs publish() to completion before persist())
    verify(batch).keyed();
    verify(batch).publishToTopic(CHANGELOG_TOPIC, EB_PARTITION);
    assertThat(changelogPosition()).isEmpty();

    // when the cut persists
    cut.persist();
    cut.complete(true);

    // then the changelog position (the marker's assigned broker position) is durable alongside
    // the state delta and the offset
    assertThat(changelogPosition()).hasValue(101L);
  }

  @Test
  void shouldNotTouchTheChangelogWhenDisabled() {
    // given a task opened without the changelog (today's default-off path for existing tests)
    openTask(mock(EventBridgeClient.class), false);
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 100L));
    final CommitCut cut = task.freezeCut(100L);

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
    openTask(client, true);
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 100L));
    final CommitCut cut = task.freezeCut(100L);

    // when / then publish() propagates the failure — this task's cut composition never calls
    // persist() itself, so nothing local is committed
    assertThatThrownBy(cut::publish).isInstanceOf(CompletionException.class);
    assertThat(changelogPosition()).isEmpty();
    assertThat(durableOffset()).isEmpty();
  }

  @Test
  void shouldEnvelopeRecordsAcrossEveryColumnFamilyTheFrozenCutPersists() {
    // given a fold that touches the base projection (ELEMENT_ENTITY), the sealing aggregation's
    // open segment (OPEN_SEGMENT), and the pre-fold dedup watermark (ZEEBE_APPLIED_POSITION)
    final RecordingClient client = recordingClient();
    openTask(client.client, true);
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 100L));
    final CommitCut cut = task.freezeCut(100L);

    // when
    cut.publish();

    // then every non-marker record decodes to one of the three column families this cut touched,
    // and the trailing marker is the reserved key, never collided with by any of them
    assertThat(client.publishedRecords).isNotEmpty();
    final List<byte[]> nonMarkerKeys =
        client.publishedRecords.stream()
            .map(entry -> entry[0])
            .filter(key -> !ChangelogMarker.isMarkerKey(key))
            .toList();
    assertThat(nonMarkerKeys).isNotEmpty();
    final Set<Integer> tags =
        nonMarkerKeys.stream()
            .map(key -> ChangelogKeyEnvelope.decode(key).cfTag())
            .collect(Collectors.toSet());
    assertThat(tags)
        .containsAnyOf(
            AnalyticsColumnFamilies.ELEMENT_ENTITY.getValue(),
            AnalyticsColumnFamilies.OPEN_SEGMENT.getValue(),
            AnalyticsColumnFamilies.ZEEBE_APPLIED_POSITION.getValue());
    // the marker is strictly last
    final byte[] lastKey = client.publishedRecords.get(client.publishedRecords.size() - 1)[0];
    assertThat(ChangelogMarker.isMarkerKey(lastKey)).isTrue();
  }

  @Test
  void shouldStillWriteTheMarkerForAnEmptyCut() {
    // given a changelog-enabled task with no folds at all
    final RecordingClient client = recordingClient();
    openTask(client.client, true);

    // when a cut freezes and publishes at offset 0 (no folds occurred since the task opened)
    final CommitCut cut = task.freezeCut(0L);
    cut.publish();

    // then the marker is still the sole record — a legitimately empty cut still advances the
    // failover/rebuild resume token
    assertThat(client.publishedRecords).hasSize(1);
    assertThat(ChangelogMarker.isMarkerKey(client.publishedRecords.get(0)[0])).isTrue();
  }

  @Test
  void shouldNotReemitAnUnchangedRowAcrossCuts() {
    // given a first cut that folds one activation and durably persists it
    final RecordingClient client = recordingClient();
    openTask(client.client, true);
    task.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L, 100L));
    Cuts.commit(task, 100L);
    client.publishedRecords.clear();

    // when a second cut folds a completion of the SAME element (an update, not a new row) plus a
    // fresh Zeebe partition watermark
    task.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L, 101L));
    final CommitCut cut = task.freezeCut(101L);
    cut.publish();

    // then the changelog carries this cut's own delta (the updated element row + the moved
    // watermark) — not a stale re-emission of anything unchanged since the first cut
    assertThat(client.publishedRecords).isNotEmpty();
  }

  @Test
  void shouldEmitOnlyTheMovedWatermarkAcrossCuts() {
    // given a first cut that folds on TWO Zeebe partitions and durably persists both watermarks
    final RecordingClient client = recordingClient();
    openTask(client.client, true);
    task.process(
        process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, ZEEBE_PARTITION, 10L, 100L));
    task.process(
        process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, OTHER_ZEEBE_PARTITION, 5L, 101L));
    Cuts.commit(task, 101L);
    client.publishedRecords.clear();

    // when a second cut folds only on the first partition — the other one does not move
    task.process(
        process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, ZEEBE_PARTITION, 11L, 102L));
    final CommitCut cut = task.freezeCut(102L);
    cut.publish();

    // then the second cut's changelog carries a watermark record for the moved partition only —
    // the unmoved partition's already-durable watermark is absent, not re-emitted
    final Set<Integer> emittedWatermarkPartitions = emittedWatermarkPartitions(client);
    assertThat(emittedWatermarkPartitions).containsExactly(ZEEBE_PARTITION);
    assertThat(emittedWatermarkPartitions).doesNotContain(OTHER_ZEEBE_PARTITION);
  }

  /**
   * The Zeebe partitions carried by {@link AnalyticsColumnFamilies#ZEEBE_APPLIED_POSITION}-tagged
   * records among {@code client}'s published records so far (decoding each such record's enveloped
   * store key — {@code zeebePartitionId(4)}, big-endian — back to the partition id).
   */
  private static Set<Integer> emittedWatermarkPartitions(final RecordingClient client) {
    final int watermarkTag = AnalyticsColumnFamilies.ZEEBE_APPLIED_POSITION.getValue();
    return client.publishedRecords.stream()
        .map(entry -> entry[0])
        .filter(key -> !ChangelogMarker.isMarkerKey(key))
        .map(ChangelogKeyEnvelope::decode)
        .filter(envelope -> envelope.cfTag() == watermarkTag)
        .map(envelope -> ByteBuffer.wrap(envelope.storeKey()).getInt())
        .collect(Collectors.toSet());
  }

  private void openTask(final EventBridgeClient client, final boolean changelogEnabled) {
    metadataStore = new TestMetadataStore();
    registry = new DatasetRegistry();
    provision("cube-a");
    catalog = new DatasetCatalog(metadataStore);
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:stage1-changelog-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
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
            1_000,
            1,
            datasetStore,
            datasetStore.writer(),
            provider,
            openSegments,
            offsets,
            appliedPositions,
            catalog,
            Long.MAX_VALUE,
            false,
            ProjectionMetrics.NOOP,
            FlowMetrics.NOOP,
            StoreMetrics.NOOP,
            0L,
            CHANGELOG_TOPIC,
            changelogEnabled);
    task.init();
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
    return process(intent, timestamp, ZEEBE_PARTITION, zeebePosition, ebOffset);
  }

  /**
   * As {@link #process(ProcessInstanceIntent, long, long, long)}, with an explicit real Zeebe
   * partition — for exercising the pre-fold dedup watermark of more than one Zeebe partition.
   */
  private static SourceRecord process(
      final ProcessInstanceIntent intent,
      final long timestamp,
      final int zeebePartition,
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
        new CopiedRecord<>(value, metadata, PI_KEY, zeebePartition, zeebePosition, -1L, timestamp);
    return new SourceRecord(EB_PARTITION, ebOffset, record);
  }

  private Optional<Long> changelogPosition() {
    final KeyValueStore<DbInt, DbLong> positions =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CHANGELOG_POSITION, new DbInt(), new DbLong());
    final DbInt key = new DbInt();
    key.wrapInt(EB_PARTITION);
    return positions.get(key).map(DbLong::getValue);
  }

  private Optional<Long> durableOffset() {
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(
            AnalyticsColumnFamilies.CONSUMED_POSITION, new DbInt(), new DbLong());
    final DbInt key = new DbInt();
    key.wrapInt(EB_PARTITION);
    return offsets.get(key).map(DbLong::getValue);
  }

  /**
   * An {@link EventBridgeClient} mock whose batch records every (key, value) pair published to
   * {@link #publishedRecords}, in order — the observable of the changelog's produce-before-commit
   * ordering and the envelope's per-record content.
   */
  private RecordingClient recordingClient() {
    final RecordingClient recording = new RecordingClient();
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.newBatch()).thenAnswer(invocation -> recording.newRecordingBatch());
    recording.client = client;
    return recording;
  }

  private static final class RecordingClient {

    private final List<byte[][]> publishedRecords = new ArrayList<>();
    private EventBridgeClient client;

    private BatchPublisher newRecordingBatch() {
      return new BatchPublisher() {

        @Override
        public BatchPublisher add(final String key, final byte[] value) {
          publishedRecords.add(new byte[][] {key.getBytes(StandardCharsets.UTF_8), value});
          return this;
        }

        @Override
        public BatchPublisher add(final byte[] key, final byte[] value) {
          publishedRecords.add(new byte[][] {key, value});
          return this;
        }

        @Override
        public BatchPublisher add(final byte[] value) {
          publishedRecords.add(new byte[][] {new byte[0], value});
          return this;
        }

        @Override
        public BatchPublisher add(final String value) {
          publishedRecords.add(new byte[][] {new byte[0], value.getBytes(StandardCharsets.UTF_8)});
          return this;
        }

        @Override
        public BatchPublisher keyed() {
          return this;
        }

        @Override
        public CompletableFuture<List<Long>> publishToTopic(
            final String topic, final int partitionId) {
          return CompletableFuture.completedFuture(
              List.of(0L, (long) (publishedRecords.size() - 1)));
        }
      };
    }
  }
}
