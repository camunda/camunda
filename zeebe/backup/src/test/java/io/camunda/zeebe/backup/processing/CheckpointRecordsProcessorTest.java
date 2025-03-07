/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.processing.MockProcessingResult.Event;
import io.camunda.zeebe.backup.processing.MockProcessingResult.MockProcessingResultBuilder;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointState;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.impl.RecordProcessorContextImpl;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CheckpointRecordsProcessorTest {

  @TempDir Path database;
  final BackupManager backupManager = mock(BackupManager.class);
  private final ProcessingScheduleService executor = mock(ProcessingScheduleService.class);

  private CheckpointRecordsProcessor processor;
  private ProcessingResultBuilder resultBuilder;
  // Used for verifying state in the tests
  private CheckpointState state;
  private ZeebeDb zeebedb;

  @BeforeEach
  void setup() {
    zeebedb =
        new ZeebeRocksDbFactory<>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(true, true),
                new AccessMetricsConfiguration(Kind.NONE, 1),
                SimpleMeterRegistry::new)
            .createDb(database.toFile());
    final RecordProcessorContextImpl context = createContext(executor, zeebedb);

    resultBuilder = new MockProcessingResultBuilder();
    processor = new CheckpointRecordsProcessor(backupManager, 1, context.getMeterRegistry());
    processor.init(context);

    state = new DbCheckpointState(zeebedb, zeebedb.createContext());
  }

  private RecordProcessorContextImpl createContext(
      final ProcessingScheduleService executor, final ZeebeDb zeebeDb) {
    final var context = zeebeDb.createContext();
    return new RecordProcessorContextImpl(
        1,
        executor,
        zeebeDb,
        context,
        null,
        new DbKeyGenerator(1, zeebeDb, context),
        StreamClock.controllable(InstantSource.system()),
        new SimpleMeterRegistry());
  }

  @AfterEach
  void after() throws Exception {
    zeebedb.close();
  }

  @Test
  void shouldCreateCheckpointOnCreateRecord() {
    // given
    final long checkpointId = 1;
    final long checkpointPosition = 10;
    final CheckpointRecord value = new CheckpointRecord().setCheckpointId(checkpointId);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition, 0, CheckpointIntent.CREATE, RecordType.COMMAND, value);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then

    // backup is triggered
    verify(backupManager, times(1)).takeBackup(checkpointId, checkpointPosition);

    // followup event is written
    assertThat(result.records()).hasSize(1);
    final Event followupEvent = result.records().get(0);
    assertThat(followupEvent.intent()).isEqualTo(CheckpointIntent.CREATED);
    assertThat(followupEvent.type()).isEqualTo(RecordType.EVENT);
    assertThat(followupEvent.value()).isNotNull();

    final CheckpointRecord followupRecord = (CheckpointRecord) followupEvent.value();
    assertThat(followupRecord.getCheckpointId()).isEqualTo(checkpointId);
    assertThat(followupRecord.getCheckpointPosition()).isEqualTo(checkpointPosition);

    // state is updated
    assertThat(state.getCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getCheckpointPosition()).isEqualTo(checkpointPosition);
  }

  @Test
  void shouldNotCreateCheckpointIfAlreadyExists() {
    // given
    final long checkpointId = 1;
    final long checkpointPosition = 10;
    state.setCheckpointInfo(checkpointId, checkpointPosition);

    final CheckpointRecord value = new CheckpointRecord().setCheckpointId(checkpointId);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition + 10, 0, CheckpointIntent.CREATE, RecordType.COMMAND, value);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then

    // backup is not triggered
    verify(backupManager, never()).takeBackup(checkpointId, checkpointPosition);

    // followup event is written
    assertThat(result.records()).hasSize(1);
    final Event followupEvent = result.records().get(0);
    assertThat(followupEvent.intent()).isEqualTo(CheckpointIntent.IGNORED);
    assertThat(followupEvent.type()).isEqualTo(RecordType.EVENT);

    // state not changed
    assertThat(state.getCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getCheckpointPosition()).isEqualTo(checkpointPosition);
  }

  @Test
  void shouldNotCreateCheckpointIfHigherCheckpointExists() {
    // given
    final long checkpointId = 10;
    final long checkpointPosition = 10;
    state.setCheckpointInfo(checkpointId, checkpointPosition);

    final int lowerCheckpointId = 1;
    final CheckpointRecord value = new CheckpointRecord().setCheckpointId(lowerCheckpointId);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition + 10, 0, CheckpointIntent.CREATE, RecordType.COMMAND, value);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then

    // backup is not triggered
    verify(backupManager, never())
        .takeBackup(lowerCheckpointId, checkpointPosition + 10);

    // followup event is written
    assertThat(result.records()).hasSize(1);
    final Event followupEvent = result.records().get(0);
    assertThat(followupEvent.intent()).isEqualTo(CheckpointIntent.IGNORED);
    assertThat(followupEvent.type()).isEqualTo(RecordType.EVENT);

    // followup event contains latest checkpoint info
    final CheckpointRecord followupRecord = (CheckpointRecord) followupEvent.value();
    assertThat(followupRecord.getCheckpointId()).isEqualTo(checkpointId);
    assertThat(followupRecord.getCheckpointPosition()).isEqualTo(checkpointPosition);

    // state not changed
    assertThat(state.getCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getCheckpointPosition()).isEqualTo(checkpointPosition);
  }

  @Test
  void shouldReplayCreatedRecord() {
    // given
    final long checkpointId = 1;
    final long checkpointPosition = 10;
    final CheckpointRecord value =
        new CheckpointRecord()
            .setCheckpointId(checkpointId)
            .setCheckpointPosition(checkpointPosition);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition + 1,
            checkpointPosition,
            CheckpointIntent.CREATED,
            RecordType.EVENT,
            value);

    // when
    processor.replay(record);

    // then
    // state is updated
    assertThat(state.getCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getCheckpointPosition()).isEqualTo(checkpointPosition);
  }

  @Test
  void shouldReplayIgnoredRecord() {
    // given
    final long checkpointId = 2;
    final long checkpointPosition = 10;
    state.setCheckpointInfo(checkpointId, checkpointPosition);
    final CheckpointRecord value = new CheckpointRecord().setCheckpointId(1);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(21, 20, CheckpointIntent.IGNORED, RecordType.EVENT, value);

    // when
    processor.replay(record);

    // then
    // state is not changed
    assertThat(state.getCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getCheckpointPosition()).isEqualTo(checkpointPosition);
  }

  @Test
  void shouldNotifyListenerWhenNewCheckpointCreated() {
    // given
    final AtomicLong checkpoint = new AtomicLong();
    processor.addCheckpointListener(checkpoint::set);

    final long checkpointId = 2;
    final long checkpointPosition = 20;
    final CheckpointRecord value = new CheckpointRecord().setCheckpointId(checkpointId);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition, 0, CheckpointIntent.CREATE, RecordType.COMMAND, value);

    // when
    processor.process(record, resultBuilder);

    // then
    assertThat(checkpoint).hasValue(checkpointId);
  }

  @Test
  void shouldNotifyListenerWhenReplayed() {
    // given
    final AtomicLong checkpoint = new AtomicLong();
    processor.addCheckpointListener(checkpoint::set);

    final long checkpointId = 3;
    final long checkpointPosition = 10;
    final CheckpointRecord value =
        new CheckpointRecord()
            .setCheckpointId(checkpointId)
            .setCheckpointPosition(checkpointPosition);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition + 1,
            checkpointPosition,
            CheckpointIntent.CREATED,
            RecordType.EVENT,
            value);

    // when
    processor.replay(record);

    // then
    assertThat(checkpoint).hasValue(checkpointId);
  }

  @Test
  void shouldNotifyListenerOnInit() {
    // given
    final RecordProcessorContextImpl context = createContext(null, zeebedb);
    processor = new CheckpointRecordsProcessor(backupManager, 1, context.getMeterRegistry());
    final long checkpointId = 3;
    final long checkpointPosition = 30;
    state.setCheckpointInfo(checkpointId, checkpointPosition);

    // when
    final AtomicLong checkpoint = new AtomicLong();
    processor.addCheckpointListener(checkpoint::set);
    processor.init(context);

    // then
    assertThat(checkpoint).hasValue(checkpointId);
  }

  @Test
  void shouldNotifyWhenListenerIsRegistered() {
    // given
    final long checkpointId = 3;
    final long checkpointPosition = 30;
    state.setCheckpointInfo(checkpointId, checkpointPosition);

    doAnswer(
            invocation -> {
              final Runnable callback = (Runnable) invocation.getArguments()[1];
              callback.run();
              return null;
            })
        .when(executor)
        .runDelayed(any(), any(Runnable.class));

    // when
    final AtomicLong checkpoint = new AtomicLong();
    processor.addCheckpointListener(checkpoint::set);

    // then
    assertThat(checkpoint).hasValue(checkpointId);
  }
}
