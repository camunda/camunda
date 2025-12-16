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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.impl.RecordProcessorContextImpl;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
  private final AtomicBoolean scalingInProgress = new AtomicBoolean(false);
  private final AtomicInteger dynamicPartitionCount =
      new AtomicInteger(3); // Default partition count for tests

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

    processor.setScalingInProgressSupplier(scalingInProgress::get);
    processor.setPartitionCountSupplier(() -> (int) dynamicPartitionCount.get());
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

    // backup is triggered with dynamic partition count
    verify(backupManager, times(1))
        .takeBackup(checkpointId, checkpointPosition, dynamicPartitionCount.get());

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
    assertThat(state.getLatestCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(checkpointPosition);
  }

  @Test
  void shouldNotCreateCheckpointIfAlreadyExists() {
    // given
    final long checkpointId = 1;
    final long checkpointPosition = 10;
    state.setLatestCheckpointInfo(checkpointId, checkpointPosition);

    final CheckpointRecord value = new CheckpointRecord().setCheckpointId(checkpointId);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition + 10, 0, CheckpointIntent.CREATE, RecordType.COMMAND, value);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then

    // backup is not triggered
    verify(backupManager, never()).takeBackup(eq(checkpointId), eq(checkpointPosition), anyInt());

    // followup event is written
    assertThat(result.records()).hasSize(1);
    final Event followupEvent = result.records().get(0);
    assertThat(followupEvent.intent()).isEqualTo(CheckpointIntent.IGNORED);
    assertThat(followupEvent.type()).isEqualTo(RecordType.EVENT);

    // state not changed
    assertThat(state.getLatestCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(checkpointPosition);
  }

  @Test
  void shouldNotCreateCheckpointIfHigherCheckpointExists() {
    // given
    final long checkpointId = 10;
    final long checkpointPosition = 10;
    state.setLatestCheckpointInfo(checkpointId, checkpointPosition);

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
        .takeBackup(eq(lowerCheckpointId), eq(checkpointPosition + 10), anyInt());

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
    assertThat(state.getLatestCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(checkpointPosition);
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
    assertThat(state.getLatestCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(checkpointPosition);
  }

  @Test
  void shouldReplayIgnoredRecord() {
    // given
    final long checkpointId = 2;
    final long checkpointPosition = 10;
    state.setLatestCheckpointInfo(checkpointId, checkpointPosition);
    final CheckpointRecord value = new CheckpointRecord().setCheckpointId(1);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(21, 20, CheckpointIntent.IGNORED, RecordType.EVENT, value);

    // when
    processor.replay(record);

    // then
    // state is not changed
    assertThat(state.getLatestCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(checkpointPosition);
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
    processor.setScalingInProgressSupplier(scalingInProgress::get);
    processor.setPartitionCountSupplier(dynamicPartitionCount::get);
    final long checkpointId = 3;
    final long checkpointPosition = 30;
    state.setLatestCheckpointInfo(checkpointId, checkpointPosition);

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
    state.setLatestCheckpointInfo(checkpointId, checkpointPosition);

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

  @Test
  void shouldConfirmBackupWhenBackupIdIsNewer() {
    // given
    final var currentBackupId = 5;
    final var currentBackupPosition = 50;
    state.setLatestBackupInfo(currentBackupId, currentBackupPosition);

    final var newCheckpointId = 10;
    final var newCheckpointPosition = 100;
    final var value =
        new CheckpointRecord()
            .setCheckpointId(newCheckpointId)
            .setCheckpointPosition(newCheckpointPosition);
    final var record =
        new MockTypedCheckpointRecord(
            newCheckpointPosition + 10,
            0,
            CheckpointIntent.CONFIRM_BACKUP,
            RecordType.COMMAND,
            value);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then
    assertThat(result.records())
        .singleElement()
        .returns(CheckpointIntent.CONFIRMED_BACKUP, Event::intent)
        .returns(RecordType.EVENT, Event::type)
        .returns(value, Event::value);
    assertThat(state.getLatestBackupId()).isEqualTo(newCheckpointId);
    assertThat(state.getLatestBackupPosition()).isEqualTo(newCheckpointPosition);
  }

  @Test
  void shouldRejectBackupWhenBackupIdIsOlder() {
    // given
    final var currentBackupId = 10;
    final var currentBackupPosition = 100;
    state.setLatestBackupInfo(currentBackupId, currentBackupPosition);

    final var oldCheckpointId = 5;
    final var oldCheckpointPosition = 50;
    final var value = new CheckpointRecord().setCheckpointId(oldCheckpointId);
    final var record =
        new MockTypedCheckpointRecord(
            oldCheckpointPosition, 0, CheckpointIntent.CONFIRM_BACKUP, RecordType.COMMAND, value);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then - command rejection is written
    assertThat(result.records())
        .singleElement()
        .returns(CheckpointIntent.CONFIRM_BACKUP, Event::intent)
        .returns(RecordType.COMMAND_REJECTION, Event::type)
        .returns(value, Event::value);
  }

  @Test
  void shouldRejectBackupWhenBackupIdIsSame() {
    // given
    final var currentBackupId = 10;
    final var currentBackupPosition = 100;
    state.setLatestBackupInfo(currentBackupId, currentBackupPosition);

    final var sameCheckpointId = 10;
    final var sameCheckpointPosition = 100;
    final var value = new CheckpointRecord().setCheckpointId(sameCheckpointId);
    final var record =
        new MockTypedCheckpointRecord(
            sameCheckpointPosition, 0, CheckpointIntent.CONFIRM_BACKUP, RecordType.COMMAND, value);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then - command rejection is written
    assertThat(result.records())
        .singleElement()
        .returns(CheckpointIntent.CONFIRM_BACKUP, Event::intent)
        .returns(RecordType.COMMAND_REJECTION, Event::type)
        .returns(value, Event::value);
  }

  @Test
  void shouldConfirmBackupWhenNoBackupExists() {
    // given - no backup exists (state is empty)
    final var newCheckpointId = 5;
    final var newCheckpointPosition = 50;
    final var value = new CheckpointRecord().setCheckpointId(newCheckpointId);
    final var record =
        new MockTypedCheckpointRecord(
            newCheckpointPosition, 0, CheckpointIntent.CONFIRM_BACKUP, RecordType.COMMAND, value);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then - followup event is written
    assertThat(result.records())
        .singleElement()
        .returns(CheckpointIntent.CONFIRMED_BACKUP, Event::intent)
        .returns(RecordType.EVENT, Event::type)
        .returns(value, Event::value);
  }

  @Test
  void shouldReplayConfirmedBackupRecord() {
    // given
    final var backupId = 15;
    final var backupPosition = 150;
    final var value =
        new CheckpointRecord().setCheckpointId(backupId).setCheckpointPosition(backupPosition);
    final var record =
        new MockTypedCheckpointRecord(
            backupPosition + 1,
            backupPosition,
            CheckpointIntent.CONFIRMED_BACKUP,
            RecordType.EVENT,
            value);

    // when
    processor.replay(record);

    // then - backup state is updated
    assertThat(state.getLatestBackupId()).isEqualTo(backupId);
    assertThat(state.getLatestBackupPosition()).isEqualTo(backupPosition);
  }

  @Test
  void shouldReplayConfirmedBackupRecordWithoutAffectingCheckpointState() {
    // given
    final var checkpointId = 20;
    final var checkpointPosition = 200;
    state.setLatestCheckpointInfo(checkpointId, checkpointPosition);

    final var backupId = 15;
    final var backupPosition = 150;
    final var value =
        new CheckpointRecord().setCheckpointId(backupId).setCheckpointPosition(backupPosition);
    final var record =
        new MockTypedCheckpointRecord(
            backupPosition + 1,
            backupPosition,
            CheckpointIntent.CONFIRMED_BACKUP,
            RecordType.EVENT,
            value);

    // when
    processor.replay(record);

    // then - backup state is updated
    assertThat(state.getLatestBackupId()).isEqualTo(backupId);
    assertThat(state.getLatestBackupPosition()).isEqualTo(backupPosition);
    // then - checkpoint state is unchanged
    assertThat(state.getLatestCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(checkpointPosition);
  }

  @Test
  void shouldRejectCheckpointCreationWhenScalingInProgress() {
    // given
    final long checkpointId = 1;
    final long checkpointPosition = 10;
    final CheckpointRecord value = new CheckpointRecord().setCheckpointId(checkpointId);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition, 0, CheckpointIntent.CREATE, RecordType.COMMAND, value, 1, 1);

    // Set up scaling in progress supplier to return true
    scalingInProgress.set(true);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then
    // backup is not triggered
    verify(backupManager, never()).takeBackup(eq(checkpointId), eq(checkpointPosition), anyInt());
    // verify that failed backup is taken
    verify(backupManager, times(1))
        .createFailedBackup(
            checkpointId,
            checkpointPosition,
            "Cannot create checkpoint while scaling is in progress");

    // rejection response is sent
    assertThat(result.response()).isNotNull();
    final var rejectionEvent = result.response();
    assertThat(rejectionEvent.intent()).isEqualTo(CheckpointIntent.CREATE);
    assertThat(rejectionEvent.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejectionEvent.rejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejectionEvent.rejectionReason())
        .isEqualTo("Cannot create checkpoint while scaling is in progress");

    // state is updated
    assertThat(state.getLatestCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(checkpointPosition);
  }

  @Test
  void shouldUpdatePartitionCountDynamically() {
    // given
    final long firstCheckpointId = 1;
    final long firstCheckpointPosition = 10;
    final int firstPartitionCount = 3;
    dynamicPartitionCount.set(firstPartitionCount);

    final CheckpointRecord firstValue = new CheckpointRecord().setCheckpointId(firstCheckpointId);
    final MockTypedCheckpointRecord firstRecord =
        new MockTypedCheckpointRecord(
            firstCheckpointPosition, 0, CheckpointIntent.CREATE, RecordType.COMMAND, firstValue);

    // when - first checkpoint creation
    processor.process(firstRecord, resultBuilder);

    // then - first backup uses first partition count
    verify(backupManager, times(1))
        .takeBackup(firstCheckpointId, firstCheckpointPosition, firstPartitionCount);

    // given - change partition count
    final long secondCheckpointId = 2;
    final long secondCheckpointPosition = 20;
    final int secondPartitionCount = 7; // Changed partition count
    dynamicPartitionCount.set(secondPartitionCount);

    final CheckpointRecord secondValue = new CheckpointRecord().setCheckpointId(secondCheckpointId);
    final MockTypedCheckpointRecord secondRecord =
        new MockTypedCheckpointRecord(
            secondCheckpointPosition, 0, CheckpointIntent.CREATE, RecordType.COMMAND, secondValue);

    // when - second checkpoint creation
    processor.process(secondRecord, resultBuilder);

    // then - second backup uses updated partition count
    verify(backupManager, times(1))
        .takeBackup(secondCheckpointId, secondCheckpointPosition, secondPartitionCount);
  }
}
