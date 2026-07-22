/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.sink.BackpressureGate;
import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.DescriptorSink;
import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.batch.Interner;
import io.camunda.analytics.lake.sink.batch.SegmentFactory;
import io.camunda.analytics.lake.sink.batch.SegmentRowAppender;
import io.camunda.analytics.lake.sink.batch.SegmentSorter;
import io.camunda.analytics.lake.sink.encode.IcebergParquetEncoderFactory;
import io.camunda.analytics.lake.sink.encode.LocalFileSink;
import io.camunda.analytics.lake.sink.pipeline.DirectCommitSink;
import io.camunda.analytics.lake.sink.pipeline.SinkConfig;
import io.camunda.analytics.lake.sink.pipeline.SinkPipeline;
import io.camunda.analytics.lake.state.TranslatorState;
import io.camunda.analytics.lake.translate.LakeTranslator;
import io.camunda.analytics.lake.translate.RawTableSchemas;
import io.camunda.analytics.lake.write.IcebergLakeWriter;
import io.camunda.analytics.lake.write.LakeCompactor;
import io.camunda.analytics.lake.write.LocalFileIO;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.parquet.ParquetReadOptions;
import org.apache.parquet.conf.PlainParquetConfiguration;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.SeekableInputStream;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test of the L0 sink wired into {@link LakeTranslator}: synthetic Zeebe records feed a
 * translator writing through real {@link SinkPipeline}s into a real (temp-warehouse) Iceberg
 * catalog via {@link DirectCommitSink}, exactly as {@code LakePocApp} wires production — the only
 * thing faked here is the record source (no real Event Bridge) and the clock (so {@code TIME_DUE}
 * flushes are deterministic).
 */
class SinkIntegrationTest {

  private static final String TOPIC = "test-topic";
  private static final int PARTITION_0 = 0;
  private static final int PARTITION_1 = 1;
  private static final long FLUSH_INTERVAL_MS = 1_000L;
  private static final int SEGMENT_ROWS = 4;
  private static final int RING_SEGMENTS = 4;
  private static final long FILE_TARGET_BYTES = 1024L * 1024 * 1024; // never trips SIZE_CAP here
  private static final int VARS_JSON_AVG_BYTES_PER_ROW = 512;

  private static final String PROCESS_ID = "sink-it-process";
  private static final int VERSION = 7;
  private static final String TENANT_ID = "<default>";
  private static final long PROCESS_DEFINITION_KEY = 999L;

  // Day A and day B are one calendar day apart -- see TranslatorState.OpenInstance#startMs /
  // ActivityRow#instanceStartMs for why every element of an instance always shares its instance's
  // family day (an instance's own activities never straddle two days by construction).
  private static final long DAY_A_START_MS = 1_700_000_000_000L;
  private static final long DAY_B_START_MS = DAY_A_START_MS + 86_400_000L;
  private static final long DAY_C_START_MS = DAY_B_START_MS + 86_400_000L;

  @TempDir Path tempDir;

  @Test
  void shouldIngestAcrossFamilyDaysFlushIdempotentlyAndCarryForwardOffsets() throws Exception {
    // given a fresh warehouse, tables created the same way IcebergLakeWriter always creates them
    final LakeConfig config = testConfig();
    final IcebergLakeWriter writer = new IcebergLakeWriter(config);
    try {
      final Table instancesTable = writer.instancesTable();
      final Table activitiesTable = writer.activitiesTable();
      final LocalFileSink fileSink = new LocalFileSink(config.warehouseDir().resolve("lake"));
      final IcebergParquetEncoderFactory instancesEncoderFactory =
          new IcebergParquetEncoderFactory(
              instancesTable.schema(), fileSink, SEGMENT_ROWS, Set.of());
      final IcebergParquetEncoderFactory activitiesEncoderFactory =
          new IcebergParquetEncoderFactory(
              activitiesTable.schema(), fileSink, SEGMENT_ROWS, Set.of());
      final DirectCommitSink instancesSink =
          new DirectCommitSink(instancesTable, writer.commitLock(instancesTable));
      final DirectCommitSink activitiesSink =
          new DirectCommitSink(activitiesTable, writer.commitLock(activitiesTable));
      final TableSchema instancesSchema = RawTableSchemas.instances(instancesTable.schema());
      final TableSchema activitiesSchema = RawTableSchemas.activities(activitiesTable.schema());
      final MeterRegistry meterRegistry = new SimpleMeterRegistry();
      final FakeClock clock = new FakeClock(0L);

      // when: partition 0 gets 5 instances (3 on day A, 2 on day B, 2 elements + 1 variable each),
      // fed in two batches with a TIME_DUE-forcing clock advance between them (several flushes,
      // one window spanning both family days); partition 1 gets 1 more instance on day A, on the
      // SAME two tables -- proving DirectCommitSink's carry-forward rule across partitions.
      final TranslatorState state0 = new InMemoryTranslatorState();
      final Pipelines pipelines0 =
          buildPipelines(
              PARTITION_0,
              instancesSchema,
              activitiesSchema,
              instancesEncoderFactory,
              activitiesEncoderFactory,
              instancesSink,
              activitiesSink,
              clock,
              meterRegistry);
      final LakeTranslator translator0 =
          new LakeTranslator(state0, pipelines0.instanceAppender, pipelines0.activityAppender);

      final TranslatorState state1 = new InMemoryTranslatorState();
      final Pipelines pipelines1 =
          buildPipelines(
              PARTITION_1,
              instancesSchema,
              activitiesSchema,
              instancesEncoderFactory,
              activitiesEncoderFactory,
              instancesSink,
              activitiesSink,
              clock,
              meterRegistry);
      final LakeTranslator translator1 =
          new LakeTranslator(state1, pipelines1.instanceAppender, pipelines1.activityAppender);

      pipelines0.start();
      pipelines1.start();

      final OffsetCounter offsets0 = new OffsetCounter();
      final OffsetCounter offsets1 = new OffsetCounter();

      // batch 1: instances 1, 2 (day A) on partition 0
      long lastOffset0 =
          feed(
              translator0,
              offsets0,
              List.of(instance(1L, DAY_A_START_MS, 0), instance(2L, DAY_A_START_MS + 100, 0)));
      clock.advance(FLUSH_INTERVAL_MS + 1);
      pipelines0.onPollTick(lastOffset0, DAY_A_START_MS);

      // batch 2: instance 3 (day A) + instances 4, 5 (day B) on partition 0 -- this window's files
      // span both family days
      lastOffset0 =
          feed(
              translator0,
              offsets0,
              List.of(
                  instance(3L, DAY_A_START_MS + 200, 0),
                  instance(4L, DAY_B_START_MS, 0),
                  instance(5L, DAY_B_START_MS + 100, 0)));
      clock.advance(FLUSH_INTERVAL_MS + 1);
      pipelines0.onPollTick(lastOffset0, DAY_B_START_MS);

      // partition 1: instance 6 (day A), same two tables
      final long lastOffset1 =
          feed(translator1, offsets1, List.of(instance(6L, DAY_A_START_MS + 300, 1)));
      clock.advance(FLUSH_INTERVAL_MS + 1);
      pipelines1.onPollTick(lastOffset1, DAY_A_START_MS);

      // drain everything: any still-open window gets a final SHUTDOWN-triggered commit
      pipelines0.close();
      pipelines1.close();

      // then (a): both tables contain exactly the expected rows
      assertThat(rowCount(instancesTable)).isEqualTo(6L);
      assertThat(rowCount(activitiesTable)).isEqualTo(12L);
      assertThat(instanceKeys(instancesTable)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
      assertThat(varsJsonOf(instancesTable, 1L)).contains("\"x\":1");

      // then (c): committedOffset is the MIN across the two tables, per partition
      assertThat(writer.committedOffset(PARTITION_0)).isEqualTo(lastOffset0);
      assertThat(writer.committedOffset(PARTITION_1)).isEqualTo(lastOffset1);

      // then (b): both partitions' offset stamps are present (carry-forward across partitions on
      // the same table) and survive a subsequent compaction-style commit
      assertOffsetStamped(instancesTable, PARTITION_0, lastOffset0);
      assertOffsetStamped(instancesTable, PARTITION_1, lastOffset1);
      assertOffsetStamped(activitiesTable, PARTITION_0, lastOffset0);
      assertOffsetStamped(activitiesTable, PARTITION_1, lastOffset1);

      new LakeCompactor(writer).compactIfNeeded();

      assertOffsetStamped(instancesTable, PARTITION_0, lastOffset0);
      assertOffsetStamped(instancesTable, PARTITION_1, lastOffset1);
      assertOffsetStamped(activitiesTable, PARTITION_0, lastOffset0);
      assertOffsetStamped(activitiesTable, PARTITION_1, lastOffset1);
      assertThat(writer.committedOffset(PARTITION_0)).isEqualTo(lastOffset0);
      assertThat(writer.committedOffset(PARTITION_1)).isEqualTo(lastOffset1);

      // then (e): a data file actually carries the schema's authoritative field ids
      assertFieldIdsInOneDataFile(instancesTable, instancesSchema);

      // then (f): every planned data file carries exactly one partition tuple (schema v2 --
      // days(started_at)), spanning both family days this batch actually touched
      assertPartitionTuplesSpanBothDays(instancesTable);

      // then (g): started_at/ended_at round-trip through DuckDB as proper TIMESTAMP values equal
      // to the instants that were appended, not raw millisecond integers
      assertTimestamptzRoundTrip(instancesTable, 1L, DAY_A_START_MS, DAY_A_START_MS + 50);

      // then (d): replaying the exact same records after a simulated crash (fresh state, fresh
      // pipelines, same tables/sinks) does not duplicate rows -- DirectCommitSink's idempotence
      // guard must skip every replayed descriptor since its lastOffset is already committed
      final long instanceRowsBeforeReplay = rowCount(instancesTable);
      final long activityRowsBeforeReplay = rowCount(activitiesTable);

      final FakeClock replayClock = new FakeClock(0L);
      final TranslatorState replayState0 = new InMemoryTranslatorState();
      final Pipelines replayPipelines0 =
          buildPipelines(
              PARTITION_0,
              instancesSchema,
              activitiesSchema,
              instancesEncoderFactory,
              activitiesEncoderFactory,
              instancesSink,
              activitiesSink,
              replayClock,
              meterRegistry);
      final LakeTranslator replayTranslator0 =
          new LakeTranslator(
              replayState0, replayPipelines0.instanceAppender, replayPipelines0.activityAppender);
      replayPipelines0.start();

      final OffsetCounter replayOffsets0 = new OffsetCounter();
      long replayLastOffset =
          feed(
              replayTranslator0,
              replayOffsets0,
              List.of(
                  instance(1L, DAY_A_START_MS, 0),
                  instance(2L, DAY_A_START_MS + 100, 0),
                  instance(3L, DAY_A_START_MS + 200, 0),
                  instance(4L, DAY_B_START_MS, 0),
                  instance(5L, DAY_B_START_MS + 100, 0)));
      replayClock.advance(FLUSH_INTERVAL_MS + 1);
      replayPipelines0.onPollTick(replayLastOffset, DAY_B_START_MS);
      replayPipelines0.close();

      assertThat(rowCount(instancesTable)).isEqualTo(instanceRowsBeforeReplay);
      assertThat(rowCount(activitiesTable)).isEqualTo(activityRowsBeforeReplay);
      assertThat(writer.committedOffset(PARTITION_0)).isEqualTo(lastOffset0);
    } finally {
      writer.close();
    }
  }

  /**
   * The point of this whole slice: instances land across three distinct family days, then an
   * Iceberg scan filtered to just one day's instant range plans <b>only</b> that day's data file(s)
   * -- proved at the file level via {@link org.apache.iceberg.TableScan#planFiles()}, not merely by
   * post-filtering rows after reading everything.
   */
  @Test
  void shouldPruneIcebergScanFilesToOnlyTheRequestedFamilyDay() throws Exception {
    // given a fresh warehouse with one instance on each of three distinct family days
    final LakeConfig config = testConfig();
    final IcebergLakeWriter writer = new IcebergLakeWriter(config);
    try {
      final Table instancesTable = writer.instancesTable();
      final LocalFileSink fileSink = new LocalFileSink(config.warehouseDir().resolve("lake"));
      final IcebergParquetEncoderFactory instancesEncoderFactory =
          new IcebergParquetEncoderFactory(
              instancesTable.schema(), fileSink, SEGMENT_ROWS, Set.of());
      final IcebergParquetEncoderFactory activitiesEncoderFactory =
          new IcebergParquetEncoderFactory(
              writer.activitiesTable().schema(), fileSink, SEGMENT_ROWS, Set.of());
      final DirectCommitSink instancesSink =
          new DirectCommitSink(instancesTable, writer.commitLock(instancesTable));
      final DirectCommitSink activitiesSink =
          new DirectCommitSink(
              writer.activitiesTable(), writer.commitLock(writer.activitiesTable()));
      final TableSchema instancesSchema = RawTableSchemas.instances(instancesTable.schema());
      final TableSchema activitiesSchema =
          RawTableSchemas.activities(writer.activitiesTable().schema());
      final MeterRegistry meterRegistry = new SimpleMeterRegistry();
      final FakeClock clock = new FakeClock(0L);

      final TranslatorState state = new InMemoryTranslatorState();
      final Pipelines pipelines =
          buildPipelines(
              PARTITION_0,
              instancesSchema,
              activitiesSchema,
              instancesEncoderFactory,
              activitiesEncoderFactory,
              instancesSink,
              activitiesSink,
              clock,
              meterRegistry);
      final LakeTranslator translator =
          new LakeTranslator(state, pipelines.instanceAppender, pipelines.activityAppender);
      pipelines.start();

      final OffsetCounter offsets = new OffsetCounter();
      final long lastOffset =
          feed(
              translator,
              offsets,
              List.of(
                  instance(101L, DAY_A_START_MS, 0),
                  instance(102L, DAY_B_START_MS, 0),
                  instance(103L, DAY_C_START_MS, 0)));
      clock.advance(FLUSH_INTERVAL_MS + 1);
      pipelines.onPollTick(lastOffset, DAY_C_START_MS);
      pipelines.close();

      // when: the scan is filtered to day B's instant range only
      final long dayBStartMicros = DAY_B_START_MS * 1000L;
      final long dayBEndMicros = dayBStartMicros + 86_400_000_000L;
      final Expression dayBOnly =
          Expressions.and(
              Expressions.greaterThanOrEqual("started_at", dayBStartMicros),
              Expressions.lessThan("started_at", dayBEndMicros));
      instancesTable.refresh();
      final List<FileScanTask> planned = new ArrayList<>();
      try (CloseableIterable<FileScanTask> tasks =
          instancesTable.newScan().filter(dayBOnly).planFiles()) {
        tasks.forEach(planned::add);
      }

      // then: at least one file is planned, and EVERY planned file's partition tuple is day B's
      // epoch day -- day A's and day C's files were pruned at the manifest level, never opened
      assertThat(planned).isNotEmpty();
      final long expectedEpochDay = epochDayOf(DAY_B_START_MS);
      for (final FileScanTask task : planned) {
        assertThat(task.file().partition().get(0, Integer.class))
            .as("planned file's partition day for %s", task.file().location())
            .isEqualTo((int) expectedEpochDay);
      }

      // and reading exactly the planned files back yields only day B's instance -- confirming the
      // plan is not just correct in theory but actually excludes day A/C's rows
      final List<String> plannedLocations =
          planned.stream().map(task -> task.file().location()).distinct().toList();
      final List<Long> keysInPlannedFiles = instanceKeysAt(plannedLocations);
      assertThat(keysInPlannedFiles).containsExactly(102L);
    } finally {
      writer.close();
    }
  }

  // ---- scenario construction -------------------------------------------------------------

  /**
   * One finished instance's full lifecycle: root activation, one variable, 2 elements, root end.
   * {@code zeebePartitionId} feeds every record's Zeebe origin {@code partitionId} (the origin-
   * position dedup gate's own key — see {@code LakeTranslator}'s "Origin-position dedup" javadoc
   * section); each record's Zeebe {@code position} is derived from {@code instanceKey} (base {@code
   * instanceKey * 1000}, one of the 7 lifecycle records per {@code +0..+6} offset) so positions
   * strictly increase across instances fed to the same Zeebe partition in ascending {@code
   * instanceKey} order, exactly like a real Zeebe log never would repeat or rewind outside of the
   * redelivery scenario the gate exists to catch.
   */
  private static List<ZeebeRecord> instanceRecordsWithoutOffsets(
      final long instanceKey, final long startMs, final int zeebePartitionId) {
    final long elementA = instanceKey * 100 + 1;
    final long elementB = instanceKey * 100 + 2;
    final long position = instanceKey * 1000;
    final List<ZeebeRecord> records = new ArrayList<>();
    records.add(
        processInstanceRecord(
            instanceKey,
            instanceKey,
            startMs,
            BpmnElementType.PROCESS,
            "",
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            zeebePartitionId,
            position));
    records.add(variableRecord(instanceKey, startMs + 1, zeebePartitionId, position + 1));
    records.add(
        processInstanceRecord(
            instanceKey,
            elementA,
            startMs + 10,
            BpmnElementType.SERVICE_TASK,
            "task-a",
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            zeebePartitionId,
            position + 2));
    records.add(
        processInstanceRecord(
            instanceKey,
            elementA,
            startMs + 20,
            BpmnElementType.SERVICE_TASK,
            "task-a",
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            zeebePartitionId,
            position + 3));
    records.add(
        processInstanceRecord(
            instanceKey,
            elementB,
            startMs + 30,
            BpmnElementType.SERVICE_TASK,
            "task-b",
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            zeebePartitionId,
            position + 4));
    records.add(
        processInstanceRecord(
            instanceKey,
            elementB,
            startMs + 40,
            BpmnElementType.SERVICE_TASK,
            "task-b",
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            zeebePartitionId,
            position + 5));
    records.add(
        processInstanceRecord(
            instanceKey,
            instanceKey,
            startMs + 50,
            BpmnElementType.PROCESS,
            "",
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            zeebePartitionId,
            position + 6));
    return records;
  }

  /**
   * A named "raw" lifecycle (Event Bridge offsets stamped in later, per partition, in feed order —
   * see {@link #feed}; {@code zeebePartitionId} is this lifecycle's Zeebe origin partition,
   * distinct from the Event Bridge source partition {@link #feed} stamps).
   */
  private static List<ZeebeRecord> instance(
      final long instanceKey, final long startMs, final int zeebePartitionId) {
    return instanceRecordsWithoutOffsets(instanceKey, startMs, zeebePartitionId);
  }

  /**
   * Feeds every record of every {@code lifecycles} entry through {@code translator}, stamping
   * sequential Event Bridge offsets as it goes (which of the two {@link OffsetCounter}s/{@code
   * translator}s the caller passes in is what actually determines the Event Bridge source partition
   * a batch belongs to in this test). Each record's own Zeebe origin {@code partitionId}/{@code
   * position} — set by {@link #instance} — is left untouched here; {@link LakeTranslator}'s
   * origin-position dedup gate reads those, not this method's Event Bridge offset. Retries
   * (spin-wait) on backpressure, matching what a real poll loop must do.
   *
   * @return the last stamped offset (the batch's {@code lastProcessedOffset} for {@code
   *     onPollTick})
   */
  private static long feed(
      final LakeTranslator translator,
      final OffsetCounter offsets,
      final List<List<ZeebeRecord>> lifecycles) {
    long lastOffset = -1;
    for (final List<ZeebeRecord> lifecycle : lifecycles) {
      for (final ZeebeRecord withoutOffset : lifecycle) {
        final long offset = offsets.next();
        final ZeebeRecord record =
            new ZeebeRecord(
                withoutOffset.topic(), withoutOffset.partitionId(), offset, withoutOffset.record());
        while (!translator.onRecord(record)) {
          Thread.onSpinWait();
        }
        lastOffset = offset;
      }
    }
    return lastOffset;
  }

  private static ZeebeRecord processInstanceRecord(
      final long processInstanceKey,
      final long elementInstanceKey,
      final long timestamp,
      final BpmnElementType elementType,
      final String elementId,
      final ProcessInstanceIntent intent,
      final int zeebePartitionId,
      final long position) {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withVersion(VERSION)
            .withProcessInstanceKey(processInstanceKey)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withTenantId(TENANT_ID)
            .withElementId(elementId)
            .withBpmnElementType(elementType)
            .build();
    final Record<ProcessInstanceRecordValue> record =
        ImmutableRecord.<ProcessInstanceRecordValue>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.PROCESS_INSTANCE)
            .withIntent(intent)
            .withKey(elementInstanceKey)
            .withTimestamp(timestamp)
            .withPartitionId(zeebePartitionId)
            .withPosition(position)
            .withValue(value)
            .build();
    // The ZeebeRecord envelope's own partitionId/offset are placeholders here: feed() replaces the
    // offset with a real, sequential Event Bridge offset as it stamps each record, and its
    // partitionId is never read by LakeTranslator (only the wrapped Zeebe protocol record's own
    // getPartitionId()/getPosition(), set above, feed the origin-position dedup gate).
    return new ZeebeRecord(TOPIC, 0, 0L, record);
  }

  private static ZeebeRecord variableRecord(
      final long processInstanceKey,
      final long timestamp,
      final int zeebePartitionId,
      final long position) {
    final VariableRecordValue value =
        ImmutableVariableRecordValue.builder()
            .withName("x")
            .withValue("1")
            .withScopeKey(processInstanceKey)
            .withProcessInstanceKey(processInstanceKey)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withTenantId(TENANT_ID)
            .withBpmnProcessId(PROCESS_ID)
            .build();
    final Record<VariableRecordValue> record =
        ImmutableRecord.<VariableRecordValue>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.VARIABLE)
            .withIntent(VariableIntent.CREATED)
            .withKey(processInstanceKey)
            .withTimestamp(timestamp)
            .withPartitionId(zeebePartitionId)
            .withPosition(position)
            .withValue(value)
            .build();
    return new ZeebeRecord(TOPIC, 0, 0L, record);
  }

  // ---- pipeline wiring (mirrors LakePocApp's own) -----------------------------------------

  private static Pipelines buildPipelines(
      final int partition,
      final TableSchema instancesSchema,
      final TableSchema activitiesSchema,
      final BatchEncoder.Factory instancesEncoderFactory,
      final BatchEncoder.Factory activitiesEncoderFactory,
      final DescriptorSink instancesSink,
      final DescriptorSink activitiesSink,
      final LongSupplier clock,
      final MeterRegistry meterRegistry) {
    final BackpressureGate gate = new NoOpBackpressureGate();
    final SinkPipeline instancesPipeline =
        newPipeline(
            instancesSchema,
            partition,
            gate,
            instancesEncoderFactory,
            instancesSink,
            clock,
            meterRegistry);
    final SinkPipeline activitiesPipeline =
        newPipeline(
            activitiesSchema,
            partition,
            gate,
            activitiesEncoderFactory,
            activitiesSink,
            clock,
            meterRegistry);
    return new Pipelines(
        instancesPipeline,
        activitiesPipeline,
        new SegmentRowAppender(instancesPipeline.ring()),
        new SegmentRowAppender(activitiesPipeline.ring()));
  }

  private static SinkPipeline newPipeline(
      final TableSchema schema,
      final int partition,
      final BackpressureGate gate,
      final BatchEncoder.Factory encoderFactory,
      final DescriptorSink descriptorSink,
      final LongSupplier clock,
      final MeterRegistry meterRegistry) {
    final int[] binaryAvgBytesPerRow = new int[schema.columns().size()];
    for (int i = 0; i < binaryAvgBytesPerRow.length; i++) {
      if (schema.columns().get(i).type() == ColumnType.BINARY) {
        binaryAvgBytesPerRow[i] = VARS_JSON_AVG_BYTES_PER_ROW;
      }
    }
    final Interner interner = new Interner();
    final Segment[] segments =
        SegmentFactory.createSegments(
            schema, RING_SEGMENTS, SEGMENT_ROWS, binaryAvgBytesPerRow, interner);
    final SegmentSorter sorter =
        new SegmentSorter(schema, SEGMENT_ROWS, binaryAvgBytesPerRow, interner);
    final SinkConfig config =
        new SinkConfig(
            SEGMENT_ROWS,
            RING_SEGMENTS,
            FLUSH_INTERVAL_MS,
            FILE_TARGET_BYTES,
            partition,
            schema.table());
    return new SinkPipeline(
        config,
        segments,
        gate,
        sorter::sort,
        encoderFactory,
        descriptorSink,
        List.of(),
        clock,
        meterRegistry);
  }

  private LakeConfig testConfig() {
    return new LakeConfig(
        "http://localhost:0",
        TOPIC,
        "test-group",
        tempDir.resolve("warehouse"),
        tempDir.resolve("state"),
        1,
        FLUSH_INTERVAL_MS,
        0L,
        0L,
        0,
        null);
  }

  // ---- assertions --------------------------------------------------------------------------

  private static void assertOffsetStamped(
      final Table table, final int partition, final long expected) {
    table.refresh();
    final Snapshot snapshot = table.currentSnapshot();
    assertThat(snapshot).as("table %s has a current snapshot", table.name()).isNotNull();
    final String value =
        snapshot.summary().get(IcebergLakeWriter.OFFSET_PROPERTY_PREFIX + partition);
    assertThat(value)
        .as("lake.offset.p%d on table %s", partition, table.name())
        .isEqualTo(Long.toString(expected));
  }

  /**
   * Asserts every data file the table's current snapshot plans carries exactly one partition tuple
   * (schema v2 -- a partitioned table's data file must, see {@code
   * io.camunda.analytics.lake.sink.encode.DayRouter}'s javadoc), and that the set of distinct
   * partition days spans both {@link #DAY_A_START_MS} and {@link #DAY_B_START_MS}.
   */
  private static void assertPartitionTuplesSpanBothDays(final Table table) throws SQLException {
    table.refresh();
    final Set<Integer> partitionDays = new LinkedHashSet<>();
    try (CloseableIterable<FileScanTask> tasks = table.newScan().planFiles()) {
      for (final FileScanTask task : tasks) {
        final DataFile file = task.file();
        assertThat(file.partition().size())
            .as("partition tuple size for %s", file.location())
            .isEqualTo(1);
        partitionDays.add(file.partition().get(0, Integer.class));
      }
    } catch (final IOException e) {
      throw new SQLException("Failed to plan data files for " + table.name(), e);
    }
    assertThat(partitionDays)
        .contains((int) epochDayOf(DAY_A_START_MS), (int) epochDayOf(DAY_B_START_MS));
  }

  private static long epochDayOf(final long epochMillis) {
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
  }

  /**
   * Asserts {@code started_at}/{@code ended_at} round-trip through DuckDB as proper {@code
   * TIMESTAMP WITH TIME ZONE} values equal to the millisecond instants that were appended (schema
   * v2 -- these columns are Iceberg {@code timestamptz}, not raw integers).
   */
  private static void assertTimestamptzRoundTrip(
      final Table table,
      final long instanceKey,
      final long expectedStartMs,
      final long expectedEndMs)
      throws SQLException {
    final List<String> locations = currentFileLocations(table);
    try (Connection duckdb = DriverManager.getConnection("jdbc:duckdb:");
        Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT started_at, ended_at FROM "
                    + readParquetOf(locations)
                    + " WHERE key = "
                    + instanceKey)) {
      assertThat(rs.next()).as("row for instance key %d", instanceKey).isTrue();
      assertThat(rs.getObject("started_at", OffsetDateTime.class).toInstant())
          .isEqualTo(Instant.ofEpochMilli(expectedStartMs));
      assertThat(rs.getObject("ended_at", OffsetDateTime.class).toInstant())
          .isEqualTo(Instant.ofEpochMilli(expectedEndMs));
    }
  }

  private static List<Long> instanceKeysAt(final List<String> locations) throws SQLException {
    final List<Long> keys = new ArrayList<>();
    try (Connection duckdb = DriverManager.getConnection("jdbc:duckdb:");
        Statement statement = duckdb.createStatement();
        ResultSet rs = statement.executeQuery("SELECT key FROM " + readParquetOf(locations))) {
      while (rs.next()) {
        keys.add(rs.getLong(1));
      }
    }
    return keys;
  }

  private static long rowCount(final Table table) throws SQLException {
    final List<String> locations = currentFileLocations(table);
    if (locations.isEmpty()) {
      return 0L;
    }
    try (Connection duckdb = DriverManager.getConnection("jdbc:duckdb:");
        Statement statement = duckdb.createStatement();
        ResultSet rs = statement.executeQuery("SELECT count(*) FROM " + readParquetOf(locations))) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static List<Long> instanceKeys(final Table table) throws SQLException {
    final List<String> locations = currentFileLocations(table);
    final List<Long> keys = new ArrayList<>();
    try (Connection duckdb = DriverManager.getConnection("jdbc:duckdb:");
        Statement statement = duckdb.createStatement();
        ResultSet rs = statement.executeQuery("SELECT key FROM " + readParquetOf(locations))) {
      while (rs.next()) {
        keys.add(rs.getLong(1));
      }
    }
    return keys;
  }

  private static String varsJsonOf(final Table table, final long instanceKey) throws SQLException {
    final List<String> locations = currentFileLocations(table);
    try (Connection duckdb = DriverManager.getConnection("jdbc:duckdb:");
        Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT vars_json FROM "
                    + readParquetOf(locations)
                    + " WHERE key = "
                    + instanceKey)) {
      assertThat(rs.next()).as("row for instance key %d", instanceKey).isTrue();
      final Blob blob = rs.getBlob(1);
      final byte[] bytes = blob.getBytes(1, (int) blob.length());
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }

  private static String readParquetOf(final List<String> locations) {
    final String fileList =
        locations.stream()
            .map(location -> "'" + LocalFileIO.toFilesystemPath(location) + "'")
            .collect(Collectors.joining(", "));
    return "read_parquet([" + fileList + "])";
  }

  private static List<String> currentFileLocations(final Table table) throws SQLException {
    table.refresh();
    final List<String> locations = new ArrayList<>();
    try (CloseableIterable<FileScanTask> tasks = table.newScan().planFiles()) {
      for (final FileScanTask task : tasks) {
        final String location = task.file().location();
        if (!locations.contains(location)) {
          locations.add(location);
        }
      }
    } catch (final IOException e) {
      throw new SQLException("Failed to plan data files for " + table.name(), e);
    }
    return locations;
  }

  private static void assertFieldIdsInOneDataFile(final Table table, final TableSchema schema)
      throws Exception {
    final List<String> locations = currentFileLocations(table);
    assertThat(locations).as("at least one data file for %s", table.name()).isNotEmpty();
    final Path physicalPath = LocalFileIO.toFilesystemPath(locations.get(0));
    final ParquetReadOptions options =
        ParquetReadOptions.builder(new PlainParquetConfiguration()).build();
    try (ParquetFileReader reader =
        ParquetFileReader.open(new LocalParquetInputFile(physicalPath), options)) {
      final ParquetMetadata footer = reader.getFooter();
      final MessageType fileSchema = footer.getFileMetaData().getSchema();
      for (final TableSchema.Column column : schema.columns()) {
        final Type field = fileSchema.getType(column.name());
        assertThat(field.getId().intValue())
            .as("field id for column %s", column.name())
            .isEqualTo(column.icebergFieldId());
      }
    }
  }

  // ---- test doubles -------------------------------------------------------------------------

  private record Pipelines(
      SinkPipeline instancesPipeline,
      SinkPipeline activitiesPipeline,
      RowAppender instanceAppender,
      RowAppender activityAppender) {

    void start() {
      instancesPipeline.start();
      activitiesPipeline.start();
    }

    void onPollTick(final long lastOffset, final long frontierMs) {
      instancesPipeline.onPollTick(lastOffset, frontierMs);
      activitiesPipeline.onPollTick(lastOffset, frontierMs);
    }

    void close() {
      instancesPipeline.close();
      activitiesPipeline.close();
    }
  }

  /** Assigns sequential offsets to fed records, one instance per source partition. */
  private static final class OffsetCounter {
    private long next;

    long next() {
      return next++;
    }
  }

  /** {@link LongSupplier} clock this test fully controls (no wall-clock reads). */
  private static final class FakeClock implements LongSupplier {
    private final AtomicLong now;

    FakeClock(final long start) {
      now = new AtomicLong(start);
    }

    @Override
    public long getAsLong() {
      return now.get();
    }

    void advance(final long deltaMillis) {
      now.addAndGet(deltaMillis);
    }
  }

  /**
   * Never engages backpressure: this test's rings are sized generously for its own small batches.
   */
  private static final class NoOpBackpressureGate implements BackpressureGate {
    @Override
    public void pause() {}

    @Override
    public void resume() {}
  }

  /** Minimal in-memory {@link TranslatorState}: this test exercises sink wiring, not RocksDB. */
  private static final class InMemoryTranslatorState implements TranslatorState {
    private final Map<Long, OpenInstance> instances = new HashMap<>();
    private final Map<Long, OpenElement> elements = new HashMap<>();
    private final Map<Long, Map<String, String>> variables = new HashMap<>();

    @Override
    public void putInstance(final long instanceKey, final OpenInstance instance) {
      instances.put(instanceKey, instance);
    }

    @Override
    public OpenInstance getInstance(final long instanceKey) {
      return instances.get(instanceKey);
    }

    @Override
    public void deleteInstance(final long instanceKey) {
      instances.remove(instanceKey);
    }

    @Override
    public void putElement(final long elementKey, final OpenElement element) {
      elements.put(elementKey, element);
    }

    @Override
    public OpenElement getElement(final long elementKey) {
      return elements.get(elementKey);
    }

    @Override
    public void deleteElement(final long elementKey) {
      elements.remove(elementKey);
    }

    @Override
    public void putVariable(final long instanceKey, final String name, final String valueJson) {
      variables.computeIfAbsent(instanceKey, k -> new HashMap<>()).put(name, valueJson);
    }

    @Override
    public Map<String, String> variablesOf(final long instanceKey) {
      return variables.getOrDefault(instanceKey, Map.of());
    }

    @Override
    public void deleteVariablesOf(final long instanceKey) {
      variables.remove(instanceKey);
    }

    @Override
    public void forEachOpenInstance(final BiConsumer<Long, OpenInstance> consumer) {
      instances.forEach(consumer);
    }

    @Override
    public void forEachOpenElement(final BiConsumer<Long, OpenElement> consumer) {
      elements.forEach(consumer);
    }

    @Override
    public void close() {}
  }

  /**
   * A from-scratch, Hadoop-free {@code org.apache.parquet.io.InputFile} over a local path,
   * mirroring {@code io.camunda.analytics.lake.sink.encode.LocalParquetInputFile} (a
   * package-private test helper in a different test package, hence duplicated here rather than
   * reused).
   */
  private static final class LocalParquetInputFile implements InputFile {
    private final Path path;

    LocalParquetInputFile(final Path path) {
      this.path = path;
    }

    @Override
    public long getLength() throws IOException {
      return Files.size(path);
    }

    @Override
    public SeekableInputStream newStream() throws IOException {
      final FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
      return new SeekableInputStream() {
        @Override
        public long getPos() throws IOException {
          return channel.position();
        }

        @Override
        public void seek(final long newPos) throws IOException {
          channel.position(newPos);
        }

        @Override
        public int read() throws IOException {
          final ByteBuffer one = ByteBuffer.allocate(1);
          final int n = channel.read(one);
          return n < 0 ? -1 : one.get(0) & 0xFF;
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
          return channel.read(ByteBuffer.wrap(b, off, len));
        }

        @Override
        public int read(final ByteBuffer buf) throws IOException {
          return channel.read(buf);
        }

        @Override
        public void readFully(final byte[] b) throws IOException {
          readFully(b, 0, b.length);
        }

        @Override
        public void readFully(final byte[] b, final int off, final int len) throws IOException {
          readFully(ByteBuffer.wrap(b, off, len));
        }

        @Override
        public void readFully(final ByteBuffer buf) throws IOException {
          while (buf.hasRemaining()) {
            if (channel.read(buf) < 0) {
              throw new EOFException();
            }
          }
        }

        @Override
        public void close() throws IOException {
          channel.close();
        }
      };
    }
  }
}
