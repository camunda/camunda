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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.processing.MockProcessingResult.Event;
import io.camunda.zeebe.backup.processing.MockProcessingResult.MockProcessingResultBuilder;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState.BackupRange;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
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
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.impl.RecordProcessorContextImpl;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class CheckpointRecordsProcessorTest {

  @TempDir Path database;
  final BackupManager backupManager = mock(BackupManager.class);
  private final ProcessingScheduleService executor = mock(ProcessingScheduleService.class);

  private CheckpointRecordsProcessor processor;
  private ProcessingResultBuilder resultBuilder;
  // Used for verifying state in the tests
  private CheckpointState state;
  private DbBackupRangeState backupRangeState;
  private DbCheckpointMetadataState checkpointMetadataState;
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
    processor = new CheckpointRecordsProcessor(backupManager, 1, null, context.getMeterRegistry());

    processor.setScalingInProgressSupplier(scalingInProgress::get);
    processor.setPartitionCountSupplier(() -> (int) dynamicPartitionCount.get());
    processor.init(context);

    state = new DbCheckpointState(zeebedb, zeebedb.createContext());
    backupRangeState = new DbBackupRangeState(zeebedb, zeebedb.createContext());
    checkpointMetadataState = new DbCheckpointMetadataState(zeebedb, zeebedb.createContext());
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
    final CheckpointRecord value =
        new CheckpointRecord()
            .setCheckpointId(checkpointId)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition, 0, CheckpointIntent.CREATE, RecordType.COMMAND, value);
    final var backupDescriptor = BackupDescriptorImpl.from(record, -1, dynamicPartitionCount.get());

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then

    // backup is triggered with dynamic partition count
    verify(backupManager, times(1)).takeBackup(eq(checkpointId), eq(backupDescriptor));

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
    state.setLatestCheckpointInfo(
        checkpointId, checkpointPosition, Instant.now().toEpochMilli(), CheckpointType.MARKER);

    final CheckpointRecord value =
        new CheckpointRecord()
            .setCheckpointId(checkpointId)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition + 10, 0, CheckpointIntent.CREATE, RecordType.COMMAND, value);
    final var backupDescriptor = BackupDescriptorImpl.from(record, -1, dynamicPartitionCount.get());

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then

    // backup is not triggered
    verify(backupManager, never()).takeBackup(eq(checkpointId), eq(backupDescriptor));

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
    state.setLatestCheckpointInfo(
        checkpointId, checkpointPosition, Instant.now().toEpochMilli(), CheckpointType.MARKER);

    final int lowerCheckpointId = 1;
    final CheckpointRecord value =
        new CheckpointRecord()
            .setCheckpointId(lowerCheckpointId)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition + 10, 0, CheckpointIntent.CREATE, RecordType.COMMAND, value);
    final var backupDescriptor = BackupDescriptorImpl.from(record, -1, dynamicPartitionCount.get());

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then

    // backup is not triggered
    verify(backupManager, never()).takeBackup(eq(checkpointId), eq(backupDescriptor));

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
            .setCheckpointPosition(checkpointPosition)
            .setCheckpointType(CheckpointType.MARKER);
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
    assertThat(state.getLatestCheckpointTimestamp()).isEqualTo(record.getTimestamp());
    assertThat(state.getLatestCheckpointType()).isEqualTo(CheckpointType.MARKER);
  }

  @Test
  void shouldReplayIgnoredRecord() {
    // given
    final long checkpointId = 2;
    final long checkpointPosition = 10;
    final long timestamp = Instant.now().toEpochMilli();
    state.setLatestCheckpointInfo(
        checkpointId, checkpointPosition, timestamp, CheckpointType.MARKER);
    final CheckpointRecord value = new CheckpointRecord().setCheckpointId(1);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(21, 20, CheckpointIntent.IGNORED, RecordType.EVENT, value);

    // when
    processor.replay(record);

    // then
    // state is not changed
    assertThat(state.getLatestCheckpointId()).isEqualTo(checkpointId);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(checkpointPosition);
    assertThat(state.getLatestCheckpointTimestamp()).isEqualTo(timestamp);
    assertThat(state.getLatestCheckpointType()).isEqualTo(CheckpointType.MARKER);
  }

  @Test
  void shouldNotifyListenerWhenNewCheckpointCreated() {
    // given
    final AtomicLong checkpoint = new AtomicLong();
    final AtomicReference<CheckpointType> checkpointType = new AtomicReference<>();
    processor.addCheckpointListener(
        (checkpointId, type) -> {
          checkpoint.set(checkpointId);
          checkpointType.set(type);
        });

    final long checkpointId = 2;
    final long checkpointPosition = 20;
    final CheckpointRecord value =
        new CheckpointRecord()
            .setCheckpointId(checkpointId)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition, 0, CheckpointIntent.CREATE, RecordType.COMMAND, value);

    // when
    processor.process(record, resultBuilder);

    // then
    assertThat(checkpoint).hasValue(checkpointId);
    assertThat(checkpointType).hasValue(CheckpointType.MANUAL_BACKUP);
  }

  @Test
  void shouldNotifyListenerWhenReplayed() {
    // given
    final AtomicLong checkpoint = new AtomicLong();
    final AtomicReference<CheckpointType> checkpointType = new AtomicReference<>();
    processor.addCheckpointListener(
        (checkpointId, type) -> {
          checkpoint.set(checkpointId);
          checkpointType.set(type);
        });

    final long checkpointId = 3;
    final long checkpointPosition = 10;
    final CheckpointRecord value =
        new CheckpointRecord()
            .setCheckpointId(checkpointId)
            .setCheckpointPosition(checkpointPosition)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP);
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
    assertThat(checkpointType).hasValue(CheckpointType.MANUAL_BACKUP);
  }

  @Test
  void shouldNotifyListenerOnInit() {
    // given
    final RecordProcessorContextImpl context = createContext(null, zeebedb);
    processor = new CheckpointRecordsProcessor(backupManager, 1, null, context.getMeterRegistry());
    processor.setScalingInProgressSupplier(scalingInProgress::get);
    processor.setPartitionCountSupplier(dynamicPartitionCount::get);
    final long checkpointId = 3;
    final long checkpointPosition = 30;
    state.setLatestCheckpointInfo(
        checkpointId, checkpointPosition, Instant.now().toEpochMilli(), CheckpointType.MARKER);

    // when
    final AtomicLong checkpoint = new AtomicLong();
    final AtomicReference<CheckpointType> checkpointType = new AtomicReference<>();
    processor.addCheckpointListener(
        (id, type) -> {
          checkpoint.set(id);
          checkpointType.set(type);
        });
    processor.init(context);

    // then
    assertThat(checkpoint).hasValue(checkpointId);
    assertThat(checkpointType).hasValue(CheckpointType.MARKER);
  }

  @Test
  void shouldNotifyWhenListenerIsRegistered() {
    // given
    final long checkpointId = 3;
    final long checkpointPosition = 30;
    state.setLatestCheckpointInfo(
        checkpointId, checkpointPosition, Instant.now().toEpochMilli(), CheckpointType.MARKER);

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
    final AtomicReference<CheckpointType> checkpointType = new AtomicReference<>();
    processor.addCheckpointListener(
        (id, type) -> {
          checkpoint.set(id);
          checkpointType.set(type);
        });

    // then
    assertThat(checkpoint).hasValue(checkpointId);
    assertThat(checkpointType).hasValue(CheckpointType.MARKER);
  }

  @Test
  void shouldConfirmBackupWhenBackupIdIsNewer() {
    // given
    final var currentBackupId = 5;
    final var currentBackupPosition = 50;
    final var timestamp = Instant.now().toEpochMilli();
    state.setLatestBackupInfo(
        currentBackupId, currentBackupPosition, timestamp, CheckpointType.MANUAL_BACKUP, -1L);

    final var newCheckpointId = 10;
    final var newCheckpointPosition = 100;
    final var value =
        new CheckpointRecord()
            .setCheckpointId(newCheckpointId)
            .setCheckpointPosition(newCheckpointPosition)
            .setCheckpointType(CheckpointType.SCHEDULED_BACKUP);
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
    assertThat(state.getLatestBackupTimestamp()).isEqualTo(record.getTimestamp());
    assertThat(state.getLatestBackupType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
  }

  @Test
  void shouldRejectBackupWhenBackupIdIsOlder() {
    // given
    final var currentBackupId = 10;
    final var currentBackupPosition = 100;
    state.setLatestBackupInfo(
        currentBackupId,
        currentBackupPosition,
        Instant.now().toEpochMilli(),
        CheckpointType.MARKER,
        -1L);

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
    state.setLatestBackupInfo(
        currentBackupId,
        currentBackupPosition,
        Instant.now().toEpochMilli(),
        CheckpointType.MANUAL_BACKUP,
        -1L);

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
  void shouldStartNewRangeWhenFirstBackupConfirmed() {
    // given
    final var newCheckpointId = 5;
    final var newCheckpointPosition = 50;
    final var value =
        new CheckpointRecord()
            .setCheckpointId(newCheckpointId)
            .setCheckpointPosition(newCheckpointPosition)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP)
            .setFirstLogPosition(-1);
    final var record =
        new MockTypedCheckpointRecord(
            newCheckpointPosition, 0, CheckpointIntent.CONFIRM_BACKUP, RecordType.COMMAND, value);

    // when
    processor.process(record, resultBuilder);

    // then — a new range [5, 5] is created in the backup ranges CF
    assertThat(backupRangeState.getAllRanges())
        .containsExactly(new BackupRange(newCheckpointId, newCheckpointId));
  }

  @Test
  void shouldExtendRangeWhenSubsequentBackupConfirmed() {
    // given — first backup creates a range [5, 5]
    final var currentBackupId = 5;
    final var currentBackupPosition = 50;
    final var timestamp = Instant.now().toEpochMilli();
    state.setLatestBackupInfo(
        currentBackupId, currentBackupPosition, timestamp, CheckpointType.MANUAL_BACKUP, -1L);
    // Seed the range state so the extend can find the existing range
    backupRangeState.startNewRange(currentBackupId);

    final var newCheckpointId = 10;
    final var newCheckpointPosition = 100;
    final var value =
        new CheckpointRecord()
            .setCheckpointId(newCheckpointId)
            .setCheckpointPosition(newCheckpointPosition)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP)
            .setFirstLogPosition(currentBackupPosition); // position 50, so 50 <= 50+1 is true
    final var record =
        new MockTypedCheckpointRecord(
            newCheckpointPosition, 0, CheckpointIntent.CONFIRM_BACKUP, RecordType.COMMAND, value);

    // when
    processor.process(record, resultBuilder);

    // then — the range is extended to [5, 10]
    assertThat(backupRangeState.getAllRanges())
        .containsExactly(new BackupRange(currentBackupId, newCheckpointId));
  }

  @Test
  void shouldReplayConfirmedBackupRecord() {
    // given
    final var backupId = 15;
    final var backupPosition = 150;
    final var value =
        new CheckpointRecord()
            .setCheckpointId(backupId)
            .setCheckpointPosition(backupPosition)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP);
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
    assertThat(state.getLatestBackupTimestamp()).isEqualTo(record.getTimestamp());
    assertThat(state.getLatestBackupType()).isEqualTo(CheckpointType.MANUAL_BACKUP);
  }

  @Test
  void shouldReplayConfirmedBackupRecordWithoutAffectingCheckpointState() {
    // given
    final var checkpointId = 20;
    final var checkpointPosition = 200;
    final var timestamp = Instant.now().toEpochMilli();
    state.setLatestCheckpointInfo(
        checkpointId, checkpointPosition, timestamp, CheckpointType.MARKER);

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
    assertThat(state.getLatestCheckpointTimestamp()).isEqualTo(timestamp);
    assertThat(state.getLatestCheckpointType()).isEqualTo(CheckpointType.MARKER);
  }

  @Test
  void shouldRejectCheckpointCreationWhenScalingInProgress() {
    // given
    final long checkpointId = 1;
    final long checkpointPosition = 10;
    final CheckpointRecord value =
        new CheckpointRecord()
            .setCheckpointId(checkpointId)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP);
    final MockTypedCheckpointRecord record =
        new MockTypedCheckpointRecord(
            checkpointPosition, 0, CheckpointIntent.CREATE, RecordType.COMMAND, value, 1, 1);
    final var backupDescriptor = BackupDescriptorImpl.from(record, -1, dynamicPartitionCount.get());

    // Set up scaling in progress supplier to return true
    scalingInProgress.set(true);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then
    // backup is not triggered
    verify(backupManager, never()).takeBackup(eq(checkpointId), eq(backupDescriptor));
    // verify that failed backup is taken
    verify(backupManager, times(1))
        .createFailedBackup(
            checkpointId,
            backupDescriptor,
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

    final CheckpointRecord firstValue =
        new CheckpointRecord()
            .setCheckpointId(firstCheckpointId)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP);
    final MockTypedCheckpointRecord firstRecord =
        new MockTypedCheckpointRecord(
            firstCheckpointPosition, 0, CheckpointIntent.CREATE, RecordType.COMMAND, firstValue);
    var backupDescriptor = BackupDescriptorImpl.from(firstRecord, -1, firstPartitionCount);

    // when - first checkpoint creation
    processor.process(firstRecord, resultBuilder);

    // then - first backup uses first partition count
    verify(backupManager, times(1)).takeBackup(eq(firstCheckpointId), eq(backupDescriptor));

    // given - change partition count
    final long secondCheckpointId = 2;
    final long secondCheckpointPosition = 20;
    final int secondPartitionCount = 7; // Changed partition count
    dynamicPartitionCount.set(secondPartitionCount);

    final CheckpointRecord secondValue =
        new CheckpointRecord()
            .setCheckpointId(secondCheckpointId)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP);
    final MockTypedCheckpointRecord secondRecord =
        new MockTypedCheckpointRecord(
            secondCheckpointPosition, 0, CheckpointIntent.CREATE, RecordType.COMMAND, secondValue);
    backupDescriptor = BackupDescriptorImpl.from(secondRecord, -1, secondPartitionCount);

    // when - second checkpoint creation
    processor.process(secondRecord, resultBuilder);

    // then - second backup uses updated partition count
    verify(backupManager, times(1)).takeBackup(eq(secondCheckpointId), eq(backupDescriptor));
  }

  @Test
  void shouldProcessCheckpointWithNoTimestampAndType() {
    // given
    final var backupId = 15;
    final var backupPosition = 150;
    final var value =
        new CheckpointRecord().setCheckpointId(backupId).setCheckpointPosition(backupPosition);
    final var record =
        new MockTypedCheckpointRecord(
            backupPosition + 1,
            backupPosition,
            CheckpointIntent.CONFIRM_BACKUP,
            RecordType.COMMAND,
            value);

    // when
    processor.process(record, resultBuilder);

    // then - backup state is updated
    assertThat(state.getLatestBackupId()).isEqualTo(backupId);
    assertThat(state.getLatestBackupPosition()).isEqualTo(backupPosition);
    assertThat(state.getLatestBackupTimestamp()).isEqualTo(record.getTimestamp());
    assertThat(state.getLatestBackupType()).isEqualTo(CheckpointType.MANUAL_BACKUP);
  }

  @Test
  void shouldNotTakeBackupOnMarkerCheckpoints() {
    // given
    final var backupId = 15L;
    final var backupPosition = 150;
    final var value =
        new CheckpointRecord()
            .setCheckpointId(backupId)
            .setCheckpointPosition(backupPosition)
            .setCheckpointType(CheckpointType.MARKER);
    final var record =
        new MockTypedCheckpointRecord(
            backupPosition + 1, backupPosition, CheckpointIntent.CREATE, RecordType.COMMAND, value);

    // when
    processor.process(record, resultBuilder);

    // then
    verify(backupManager, times(0)).takeBackup(eq(backupId), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SCHEDULED_BACKUP", "MANUAL_BACKUP"})
  void shouldTakeBackupOnBackupType(final String checkpointType) {
    // given
    final var backupId = 15L;
    final var backupPosition = 150;
    final var value =
        new CheckpointRecord()
            .setCheckpointId(backupId)
            .setCheckpointPosition(backupPosition)
            .setCheckpointType(CheckpointType.valueOf(checkpointType));
    final var record =
        new MockTypedCheckpointRecord(
            backupPosition + 1, backupPosition, CheckpointIntent.CREATE, RecordType.COMMAND, value);

    // when
    processor.process(record, resultBuilder);

    // then
    verify(backupManager, times(1)).takeBackup(eq(backupId), any());
  }

  @Test
  void shouldScheduleSyncPostCommitTaskOnConfirmBackup() {
    // given — a processor with a real backup store
    final var backupStore = mock(BackupStore.class);
    when(backupStore.storeBackupMetadata(any(int.class), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var context = createContext(executor, zeebedb);
    final var syncProcessor =
        new CheckpointRecordsProcessor(backupManager, 1, backupStore, context.getMeterRegistry());
    syncProcessor.setScalingInProgressSupplier(scalingInProgress::get);
    syncProcessor.setPartitionCountSupplier(dynamicPartitionCount::get);
    syncProcessor.init(context);

    final var checkpointId = 5;
    final var checkpointPosition = 50;
    final var value =
        new CheckpointRecord()
            .setCheckpointId(checkpointId)
            .setCheckpointPosition(checkpointPosition)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP);
    final var record =
        new MockTypedCheckpointRecord(
            checkpointPosition + 10, 0, CheckpointIntent.CONFIRM_BACKUP, RecordType.COMMAND, value);

    // when
    final var result =
        (MockProcessingResult) syncProcessor.process(record, new MockProcessingResultBuilder());

    // then — a post-commit task is registered
    assertThat(result.postCommitTasks()).hasSize(1);

    // when — the post-commit task is executed
    final var success = result.executePostCommitTasks();

    // then — task succeeds (fire-and-forget) and store metadata is called
    assertThat(success).isTrue();
    verify(backupStore).storeBackupMetadata(eq(1), any(), any());
  }

  @Test
  void shouldNotScheduleSyncPostCommitTaskWhenNoBackupStore() {
    // given — no backup store (null)
    final var checkpointId = 5;
    final var checkpointPosition = 50;
    final var value =
        new CheckpointRecord()
            .setCheckpointId(checkpointId)
            .setCheckpointPosition(checkpointPosition)
            .setCheckpointType(CheckpointType.MANUAL_BACKUP);
    final var record =
        new MockTypedCheckpointRecord(
            checkpointPosition + 10, 0, CheckpointIntent.CONFIRM_BACKUP, RecordType.COMMAND, value);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then — no post-commit tasks
    assertThat(result.postCommitTasks()).isEmpty();
  }

  @Test
  void shouldNotScheduleSyncPostCommitTaskOnRejection() {
    // given — a processor with a backup store and an existing newer backup
    final var backupStore = mock(BackupStore.class);
    final var context = createContext(executor, zeebedb);
    final var syncProcessor =
        new CheckpointRecordsProcessor(backupManager, 1, backupStore, context.getMeterRegistry());
    syncProcessor.setScalingInProgressSupplier(scalingInProgress::get);
    syncProcessor.setPartitionCountSupplier(dynamicPartitionCount::get);
    syncProcessor.init(context);

    // Set a newer backup ID so the command will be rejected
    state.setLatestBackupInfo(
        10, 100, Instant.now().toEpochMilli(), CheckpointType.MANUAL_BACKUP, -1L);

    final var oldCheckpointId = 5;
    final var value = new CheckpointRecord().setCheckpointId(oldCheckpointId);
    final var record =
        new MockTypedCheckpointRecord(
            50, 0, CheckpointIntent.CONFIRM_BACKUP, RecordType.COMMAND, value);

    // when
    final var result =
        (MockProcessingResult) syncProcessor.process(record, new MockProcessingResultBuilder());

    // then — no post-commit task (command was rejected)
    assertThat(result.postCommitTasks()).isEmpty();
  }

  @Test
  void shouldSyncOnRecoveryWhenBackupStoreExists() {
    // given — a processor with a real backup store
    final var backupStore = mock(BackupStore.class);
    when(backupStore.storeBackupMetadata(any(int.class), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var context = createContext(executor, zeebedb);
    final var syncProcessor =
        new CheckpointRecordsProcessor(backupManager, 1, backupStore, context.getMeterRegistry());
    syncProcessor.setScalingInProgressSupplier(scalingInProgress::get);
    syncProcessor.setPartitionCountSupplier(dynamicPartitionCount::get);
    syncProcessor.init(context);

    final var readonlyContext = mock(ReadonlyStreamProcessorContext.class);
    when(readonlyContext.getPartitionId()).thenReturn(1);

    // when
    syncProcessor.onRecovered(readonlyContext);

    // then — sync is triggered (store metadata is called)
    verify(backupStore).storeBackupMetadata(eq(1), any(), any());
  }

  @Test
  void shouldNotSyncOnRecoveryWhenNoBackupStore() {
    // given — no backup store (the default processor in setup)
    final var readonlyContext = mock(ReadonlyStreamProcessorContext.class);
    when(readonlyContext.getPartitionId()).thenReturn(1);

    // when — should not throw
    processor.onRecovered(readonlyContext);

    // then — no exception, backup manager still called for fail in-progress
    verify(backupManager).failInProgressBackup(anyLong());
  }

  // ===== DELETE_BACKUP tests =====

  @Test
  void shouldDeleteBackupWhenCheckpointExists() {
    // given — a confirmed backup with checkpoint 5
    final var checkpointId = 5L;
    checkpointMetadataState.addCheckpoint(
        checkpointId, 50, Instant.now().toEpochMilli(), CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(checkpointId, 40, 3, "8.9.0");
    backupRangeState.startNewRange(checkpointId);
    state.setLatestBackupInfo(
        checkpointId, 50, Instant.now().toEpochMilli(), CheckpointType.MANUAL_BACKUP, 40);

    final var value = new CheckpointRecord().setCheckpointId(checkpointId);
    final var record =
        new MockTypedCheckpointRecord(
            60, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then — BACKUP_DELETED event is written
    assertThat(result.records())
        .singleElement()
        .returns(CheckpointIntent.BACKUP_DELETED, Event::intent)
        .returns(RecordType.EVENT, Event::type)
        .returns(value, Event::value);
    // checkpoint is removed from metadata state
    assertThat(checkpointMetadataState.getCheckpoint(checkpointId)).isNull();
    // range is deleted (was the only checkpoint in range)
    assertThat(backupRangeState.getAllRanges()).isEmpty();
  }

  @Test
  void shouldRejectDeleteBackupWhenCheckpointNotFound() {
    // given — no checkpoint exists with ID 42
    final var value = new CheckpointRecord().setCheckpointId(42);
    final var record =
        new MockTypedCheckpointRecord(
            60, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then — command rejection is written
    assertThat(result.records())
        .singleElement()
        .returns(CheckpointIntent.DELETE_BACKUP, Event::intent)
        .returns(RecordType.COMMAND_REJECTION, Event::type)
        .returns(value, Event::value);
  }

  @Test
  void shouldDeleteBackupFromStartOfRange() {
    // given — range [5, 10] with checkpoints 5 and 10
    final var firstId = 5L;
    final var secondId = 10L;
    checkpointMetadataState.addCheckpoint(
        firstId, 50, Instant.now().toEpochMilli(), CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(firstId, 40, 3, "8.9.0");
    checkpointMetadataState.addCheckpoint(
        secondId, 100, Instant.now().toEpochMilli(), CheckpointType.SCHEDULED_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(secondId, 50, 3, "8.9.0");
    backupRangeState.startNewRange(firstId);
    backupRangeState.extendRange(firstId, secondId);

    final var value = new CheckpointRecord().setCheckpointId(firstId);
    final var record =
        new MockTypedCheckpointRecord(
            110, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);

    // when
    processor.process(record, resultBuilder);

    // then — range is advanced to [10, 10]
    assertThat(backupRangeState.getAllRanges())
        .containsExactly(new BackupRange(secondId, secondId));
    assertThat(checkpointMetadataState.getCheckpoint(firstId)).isNull();
    assertThat(checkpointMetadataState.getCheckpoint(secondId)).isNotNull();
  }

  @Test
  void shouldDeleteBackupFromEndOfRange() {
    // given — range [5, 10] with checkpoints 5 and 10
    final var firstId = 5L;
    final var secondId = 10L;
    checkpointMetadataState.addCheckpoint(
        firstId, 50, Instant.now().toEpochMilli(), CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(firstId, 40, 3, "8.9.0");
    checkpointMetadataState.addCheckpoint(
        secondId, 100, Instant.now().toEpochMilli(), CheckpointType.SCHEDULED_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(secondId, 50, 3, "8.9.0");
    backupRangeState.startNewRange(firstId);
    backupRangeState.extendRange(firstId, secondId);

    final var value = new CheckpointRecord().setCheckpointId(secondId);
    final var record =
        new MockTypedCheckpointRecord(
            110, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);

    // when
    processor.process(record, resultBuilder);

    // then — range is shrunk to [5, 5]
    assertThat(backupRangeState.getAllRanges()).containsExactly(new BackupRange(firstId, firstId));
    assertThat(checkpointMetadataState.getCheckpoint(firstId)).isNotNull();
    assertThat(checkpointMetadataState.getCheckpoint(secondId)).isNull();
  }

  @Test
  void shouldDeleteBackupFromMiddleOfRange() {
    // given — range [5, 15] with checkpoints 5, 10, 15
    final var firstId = 5L;
    final var middleId = 10L;
    final var lastId = 15L;
    checkpointMetadataState.addCheckpoint(
        firstId, 50, Instant.now().toEpochMilli(), CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(firstId, 40, 3, "8.9.0");
    checkpointMetadataState.addCheckpoint(
        middleId, 100, Instant.now().toEpochMilli(), CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(middleId, 50, 3, "8.9.0");
    checkpointMetadataState.addCheckpoint(
        lastId, 150, Instant.now().toEpochMilli(), CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(lastId, 100, 3, "8.9.0");
    backupRangeState.startNewRange(firstId);
    backupRangeState.extendRange(firstId, lastId);

    final var value = new CheckpointRecord().setCheckpointId(middleId);
    final var record =
        new MockTypedCheckpointRecord(
            160, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);

    // when
    processor.process(record, resultBuilder);

    // then — range is split into [5, 5] and [15, 15]
    assertThat(backupRangeState.getAllRanges())
        .containsExactly(new BackupRange(firstId, firstId), new BackupRange(lastId, lastId));
    assertThat(checkpointMetadataState.getCheckpoint(firstId)).isNotNull();
    assertThat(checkpointMetadataState.getCheckpoint(middleId)).isNull();
    assertThat(checkpointMetadataState.getCheckpoint(lastId)).isNotNull();
  }

  @Test
  void shouldReplayBackupDeletedEvent() {
    // given — checkpoint 5 exists in state
    final var checkpointId = 5L;
    checkpointMetadataState.addCheckpoint(
        checkpointId, 50, Instant.now().toEpochMilli(), CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(checkpointId, 40, 3, "8.9.0");
    backupRangeState.startNewRange(checkpointId);

    final var value = new CheckpointRecord().setCheckpointId(checkpointId);
    final var record =
        new MockTypedCheckpointRecord(
            60, 50, CheckpointIntent.BACKUP_DELETED, RecordType.EVENT, value);

    // when
    processor.replay(record);

    // then — checkpoint is removed and range is deleted
    assertThat(checkpointMetadataState.getCheckpoint(checkpointId)).isNull();
    assertThat(backupRangeState.getAllRanges()).isEmpty();
  }

  @Test
  void shouldScheduleBackupStoreDeletePostCommitTaskOnDeleteBackup() {
    // given — a processor with a real backup store
    final var backupStore = mock(BackupStore.class);
    when(backupStore.storeBackupMetadata(any(int.class), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(backupStore.list(any()))
        .thenReturn(CompletableFuture.completedFuture(java.util.List.of()));

    final var context = createContext(executor, zeebedb);
    final var deleteProcessor =
        new CheckpointRecordsProcessor(backupManager, 1, backupStore, context.getMeterRegistry());
    deleteProcessor.setScalingInProgressSupplier(scalingInProgress::get);
    deleteProcessor.setPartitionCountSupplier(dynamicPartitionCount::get);
    deleteProcessor.init(context);

    // Seed checkpoint in metadata state
    final var metaState = new DbCheckpointMetadataState(zeebedb, zeebedb.createContext());
    metaState.addCheckpoint(5, 50, Instant.now().toEpochMilli(), CheckpointType.MANUAL_BACKUP);
    metaState.enrichWithBackupInfo(5, 40, 3, "8.9.0");
    final var rangeState = new DbBackupRangeState(zeebedb, zeebedb.createContext());
    rangeState.startNewRange(5);

    final var value = new CheckpointRecord().setCheckpointId(5);
    final var record =
        new MockTypedCheckpointRecord(
            60, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);

    // when
    final var result =
        (MockProcessingResult) deleteProcessor.process(record, new MockProcessingResultBuilder());

    // then — post-commit tasks are registered (backup store delete + JSON sync)
    assertThat(result.postCommitTasks()).hasSize(2);

    // when — the post-commit tasks are executed
    final var success = result.executePostCommitTasks();

    // then — tasks succeed and store methods are called
    assertThat(success).isTrue();
    verify(backupStore).list(any()); // backup store delete lists backups
    verify(backupStore).storeBackupMetadata(eq(1), any(), any()); // JSON sync
  }

  @Test
  void shouldNotSchedulePostCommitTasksOnDeleteBackupWithoutBackupStore() {
    // given — no backup store (the default processor in setup)
    final var checkpointId = 5L;
    checkpointMetadataState.addCheckpoint(
        checkpointId, 50, Instant.now().toEpochMilli(), CheckpointType.MANUAL_BACKUP);
    backupRangeState.startNewRange(checkpointId);

    final var value = new CheckpointRecord().setCheckpointId(checkpointId);
    final var record =
        new MockTypedCheckpointRecord(
            60, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);

    // when
    final var result = (MockProcessingResult) processor.process(record, resultBuilder);

    // then — no post-commit tasks
    assertThat(result.postCommitTasks()).isEmpty();
  }

  @Test
  void shouldNotSchedulePostCommitTasksOnDeleteBackupRejection() {
    // given — a processor with a backup store and no checkpoint to delete
    final var backupStore = mock(BackupStore.class);
    final var context = createContext(executor, zeebedb);
    final var deleteProcessor =
        new CheckpointRecordsProcessor(backupManager, 1, backupStore, context.getMeterRegistry());
    deleteProcessor.setScalingInProgressSupplier(scalingInProgress::get);
    deleteProcessor.setPartitionCountSupplier(dynamicPartitionCount::get);
    deleteProcessor.init(context);

    final var value = new CheckpointRecord().setCheckpointId(42);
    final var record =
        new MockTypedCheckpointRecord(
            60, 0, CheckpointIntent.DELETE_BACKUP, RecordType.COMMAND, value);

    // when
    final var result =
        (MockProcessingResult) deleteProcessor.process(record, new MockProcessingResultBuilder());

    // then — no post-commit tasks (command was rejected)
    assertThat(result.postCommitTasks()).isEmpty();
  }
}
