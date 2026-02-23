/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import static io.camunda.zeebe.backup.processing.state.CheckpointState.NO_CHECKPOINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupRangeStatus;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse.CheckpointInfo;
import io.camunda.zeebe.protocol.impl.encoding.BackupRequest;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.management.BackupStatusResponseEncoder;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.ServerResponse;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BackupApiRequestHandlerTest {

  private static final Instant NOW = Instant.ofEpochMilli(Instant.now().toEpochMilli());

  @RegisterExtension
  ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

  @Mock AtomixServerTransport transport;

  @Mock(answer = Answers.RETURNS_SELF)
  LogStreamWriter logStreamWriter;

  @Mock BackupManager backupManager;
  @Mock CheckpointState checkpointState;

  BackupApiRequestHandler handler;
  private ResponseReader serverOutput;
  private CompletableFuture<Either<ErrorResponse, BufferReader>> responseFuture;

  @BeforeEach
  void setup() {
    handler =
        new BackupApiRequestHandler(
            transport, logStreamWriter, backupManager, checkpointState, 1, true);
    scheduler.submitActor(handler);
    scheduler.workUntilDone();

    serverOutput = new ResponseReader();
    responseFuture = new CompletableFuture<>();

    lenient()
        .when(backupManager.getBackupRangeStatus())
        .thenReturn(CompletableActorFuture.completed(List.of()));
  }

  @Test
  void shouldRejectRequestWithInvalidType() {
    // given
    final var request =
        new BackupRequest().setType(BackupRequestType.NULL_VAL).setPartitionId(1).setBackupId(10);

    // when
    handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode)
        .isEqualTo(ErrorCode.UNSUPPORTED_MESSAGE);
  }

  @Test
  void shouldWriteToLogstreamOnTakeBackupRequest() {

    // given
    final var request =
        new BackupRequest()
            .setType(BackupRequestType.TAKE_BACKUP)
            .setPartitionId(1)
            .setBackupId(10);

    // when
    handleRequest(request);

    // then
    verify(logStreamWriter, times(1)).tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  @Test
  void shouldNotWriteWhenNoDiskSpace() {
    // given
    final var request =
        new BackupRequest()
            .setType(BackupRequestType.TAKE_BACKUP)
            .setPartitionId(1)
            .setBackupId(10);

    handler.onDiskSpaceNotAvailable();
    scheduler.workUntilDone();

    // when
    handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode)
        .isEqualTo(ErrorCode.RESOURCE_EXHAUSTED);
    verify(logStreamWriter, never()).tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  @Test
  void shouldWriteWhenDiskSpaceAvailableAgain() {
    // given
    final var request =
        new BackupRequest()
            .setType(BackupRequestType.TAKE_BACKUP)
            .setPartitionId(1)
            .setBackupId(10);

    handler.onDiskSpaceNotAvailable();
    scheduler.workUntilDone();
    handler.onDiskSpaceAvailable();
    scheduler.workUntilDone();

    // when
    handleRequest(request);

    // then
    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldCompleteResponseWhenStatusIsCompleted() {
    // given
    final long checkpointId = 10;
    final var request =
        new BackupRequest()
            .setType(BackupRequestType.QUERY_STATUS)
            .setPartitionId(1)
            .setBackupId(checkpointId);

    final Instant createdAt = Instant.ofEpochMilli(1000);
    final Instant lastModified = Instant.ofEpochMilli(2000);
    final BackupStatus status =
        new BackupStatusImpl(
            new BackupIdentifierImpl(1, 1, checkpointId),
            Optional.of(
                new BackupDescriptorImpl(
                    "s-id", 100, 3, "test", Instant.now(), CheckpointType.MANUAL_BACKUP)),
            io.camunda.zeebe.backup.api.BackupStatusCode.COMPLETED,
            Optional.empty(),
            Optional.of(createdAt),
            Optional.of(lastModified));

    when(backupManager.getBackupStatus(checkpointId))
        .thenReturn(CompletableActorFuture.completed(status));

    // when
    final BackupStatusResponse statusResponse = new BackupStatusResponse();
    serverOutput.setResponseObject(statusResponse);
    handleRequest(request);

    // then
    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100)).matches(Either::isRight);
    assertThat(statusResponse)
        .returns(checkpointId, BackupStatusResponse::getBackupId)
        .returns(1, BackupStatusResponse::getPartitionId)
        .returns(1, BackupStatusResponse::getBrokerId)
        .returns(100L, BackupStatusResponse::getCheckpointPosition)
        .returns(3, BackupStatusResponse::getNumberOfPartitions)
        .returns("s-id", BackupStatusResponse::getSnapshotId)
        .returns(BackupStatusCode.COMPLETED, BackupStatusResponse::getStatus)
        .returns("test", BackupStatusResponse::getBrokerVersion)
        .returns(createdAt.toString(), BackupStatusResponse::getCreatedAt)
        .returns(lastModified.toString(), BackupStatusResponse::getLastUpdated)
        .matches(response -> response.getFailureReason().isEmpty());
  }

  @Test
  void shouldCompleteResponseWhenStatusIsFailed() {
    // given
    final long checkpointId = 10;
    final var request =
        new BackupRequest()
            .setType(BackupRequestType.QUERY_STATUS)
            .setPartitionId(1)
            .setBackupId(checkpointId);

    final BackupStatus status =
        new BackupStatusImpl(
            new BackupIdentifierImpl(1, 1, checkpointId),
            Optional.empty(),
            io.camunda.zeebe.backup.api.BackupStatusCode.FAILED,
            Optional.of("Expected"),
            Optional.empty(),
            Optional.empty());

    when(backupManager.getBackupStatus(checkpointId))
        .thenReturn(CompletableActorFuture.completed(status));

    // when
    final BackupStatusResponse statusResponse = new BackupStatusResponse();
    serverOutput.setResponseObject(statusResponse);
    handleRequest(request);

    // then
    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100)).matches(Either::isRight);
    assertThat(statusResponse)
        .returns(checkpointId, BackupStatusResponse::getBackupId)
        .returns(1, BackupStatusResponse::getPartitionId)
        .returns(1, BackupStatusResponse::getBrokerId)
        .returns(
            BackupStatusResponseEncoder.backupIdNullValue(),
            BackupStatusResponse::getCheckpointPosition)
        .returns(
            BackupStatusResponseEncoder.numberOfPartitionsNullValue(),
            BackupStatusResponse::getNumberOfPartitions)
        .returns(null, BackupStatusResponse::getSnapshotId)
        .returns(BackupStatusCode.FAILED, BackupStatusResponse::getStatus)
        .returns("Expected", BackupStatusResponse::getFailureReason);
  }

  @Test
  void shouldReturnErrorWhenQueryingStatusFailed() {
    // given
    final long checkpointId = 10;
    final var request =
        new BackupRequest()
            .setType(BackupRequestType.QUERY_STATUS)
            .setPartitionId(1)
            .setBackupId(checkpointId);

    when(backupManager.getBackupStatus(checkpointId))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("Expected")));

    // when
    handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode)
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @Test
  void shouldCompleteResponseWithBackupList() {
    // given
    final var request = new BackupRequest().setType(BackupRequestType.LIST).setPartitionId(1);

    final Instant createdAt = Instant.ofEpochMilli(1000);
    final Instant lastModified = Instant.ofEpochMilli(2000);
    final BackupStatus status =
        new BackupStatusImpl(
            new BackupIdentifierImpl(1, 1, 2),
            Optional.of(
                new BackupDescriptorImpl(
                    "s-id", 100, 3, "test", Instant.now(), CheckpointType.MANUAL_BACKUP)),
            io.camunda.zeebe.backup.api.BackupStatusCode.COMPLETED,
            Optional.empty(),
            Optional.of(createdAt),
            Optional.of(lastModified));

    when(backupManager.listBackups(""))
        .thenReturn(CompletableActorFuture.completed(List.of(status)));

    // when
    final BackupListResponse listResponse = new BackupListResponse(List.of());
    serverOutput.setResponseObject(listResponse);
    handleRequest(request);

    // then
    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100)).matches(Either::isRight);
    final var expected =
        new BackupListResponse.BackupStatus(
            2, 1, BackupStatusCode.COMPLETED, "", "test", createdAt.toString());
    assertThat(listResponse.getBackups()).containsExactly(expected);
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/12597")
  void shouldListManyBackups() {
    // given
    final var request = new BackupRequest().setType(BackupRequestType.LIST).setPartitionId(1);

    final var statuses =
        IntStream.range(0, 500)
            .mapToObj(
                i ->
                    (BackupStatus)
                        new BackupStatusImpl(
                            new BackupIdentifierImpl(1, 1, i),
                            Optional.empty(),
                            io.camunda.zeebe.backup.api.BackupStatusCode.FAILED,
                            Optional.empty(),
                            Optional.of(Instant.now()),
                            Optional.of(Instant.now())))
            .toList();

    when(backupManager.listBackups("")).thenReturn(CompletableActorFuture.completed(statuses));

    // when
    final BackupListResponse listResponse = new BackupListResponse(List.of());
    serverOutput.setResponseObject(listResponse);
    handleRequest(request);

    // then
    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100)).matches(Either::isRight);
    assertThat(listResponse.getBackups()).hasSize(statuses.size());
  }

  @Test
  void shouldSendErrorResponseWhenListFailed() {
    // given
    final var request = new BackupRequest().setType(BackupRequestType.LIST).setPartitionId(1);

    when(backupManager.listBackups(""))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("list failed")));

    // when
    handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMillis(100))
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .returns(ErrorCode.INTERNAL_ERROR, ErrorResponse::getErrorCode)
        .returns("list failed", error -> BufferUtil.bufferAsString(error.getErrorData()));
  }

  @Test
  void shouldDeleteBackup() {
    // given
    final var request = new BackupRequest().setType(BackupRequestType.DELETE).setPartitionId(1);

    when(backupManager.deleteBackup(anyLong())).thenReturn(CompletableActorFuture.completed(null));

    // when
    final BackupStatusResponse statusResponse = new BackupStatusResponse();
    serverOutput.setResponseObject(statusResponse);
    handleRequest(request);

    // then
    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100)).matches(Either::isRight);
    assertThat(statusResponse.getStatus()).isEqualTo(BackupStatusCode.DOES_NOT_EXIST);
  }

  @Test
  void shouldReturnErrorWhenDeleteFails() {
    // given
    final var request = new BackupRequest().setType(BackupRequestType.DELETE).setPartitionId(1);

    when(backupManager.deleteBackup(anyLong()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(
                new RuntimeException("Expected failure")));

    // when
    handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMillis(100))
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .returns(ErrorCode.INTERNAL_ERROR, ErrorResponse::getErrorCode)
        .returns("Expected failure", error -> BufferUtil.bufferAsString(error.getErrorData()));
  }

  @Test
  void shouldReturnCheckpointState() {
    when(checkpointState.getLatestCheckpointId()).thenReturn(10L);
    when(checkpointState.getLatestCheckpointType()).thenReturn(CheckpointType.SCHEDULED_BACKUP);
    when(checkpointState.getLatestCheckpointTimestamp()).thenReturn(100L);
    when(checkpointState.getLatestCheckpointPosition()).thenReturn(20L);
    when(checkpointState.getLatestBackupId()).thenReturn(NO_CHECKPOINT);

    final var request =
        new BackupRequest().setType(BackupRequestType.QUERY_STATE).setPartitionId(1);

    final var stateResponse = new CheckpointStateResponse();
    serverOutput.setResponseObject(stateResponse);
    handleRequest(request);

    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100)).matches(Either::isRight);
    assertThat(stateResponse.getCheckpointStates())
        .hasSize(1)
        .singleElement()
        .returns(10L, PartitionCheckpointState::checkpointId)
        .returns(1, PartitionCheckpointState::partitionId)
        .returns(CheckpointType.SCHEDULED_BACKUP, PartitionCheckpointState::checkpointType)
        .returns(100L, PartitionCheckpointState::checkpointTimestamp)
        .returns(20L, PartitionCheckpointState::checkpointPosition);
    assertThat(stateResponse.getBackupStates()).isEmpty();
  }

  @Test
  void shouldReturnBackupState() {
    when(checkpointState.getLatestBackupId()).thenReturn(10L);
    when(checkpointState.getLatestBackupType()).thenReturn(CheckpointType.SCHEDULED_BACKUP);
    when(checkpointState.getLatestBackupTimestamp()).thenReturn(100L);
    when(checkpointState.getLatestBackupPosition()).thenReturn(20L);
    when(checkpointState.getLatestBackupFirstLogPosition()).thenReturn(5L);
    when(checkpointState.getLatestCheckpointId()).thenReturn(NO_CHECKPOINT);

    final var request =
        new BackupRequest().setType(BackupRequestType.QUERY_STATE).setPartitionId(1);

    final var stateResponse = new CheckpointStateResponse();
    serverOutput.setResponseObject(stateResponse);
    handleRequest(request);

    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100)).matches(Either::isRight);
    assertThat(stateResponse.getBackupStates())
        .hasSize(1)
        .singleElement()
        .returns(10L, PartitionCheckpointState::checkpointId)
        .returns(1, PartitionCheckpointState::partitionId)
        .returns(CheckpointType.SCHEDULED_BACKUP, PartitionCheckpointState::checkpointType)
        .returns(100L, PartitionCheckpointState::checkpointTimestamp)
        .returns(20L, PartitionCheckpointState::checkpointPosition)
        .returns(5L, PartitionCheckpointState::firstLogPosition);
    assertThat(stateResponse.getCheckpointStates()).isEmpty();
  }

  @Test
  void shouldReturnBothBackupAndCheckpointState() {
    when(checkpointState.getLatestBackupId()).thenReturn(10L);
    when(checkpointState.getLatestBackupType()).thenReturn(CheckpointType.SCHEDULED_BACKUP);
    when(checkpointState.getLatestBackupTimestamp()).thenReturn(100L);
    when(checkpointState.getLatestBackupPosition()).thenReturn(20L);
    when(checkpointState.getLatestBackupFirstLogPosition()).thenReturn(5L);
    when(checkpointState.getLatestCheckpointId()).thenReturn(15L);
    when(checkpointState.getLatestCheckpointType()).thenReturn(CheckpointType.MANUAL_BACKUP);
    when(checkpointState.getLatestCheckpointTimestamp()).thenReturn(150L);
    when(checkpointState.getLatestCheckpointPosition()).thenReturn(25L);

    final var request =
        new BackupRequest().setType(BackupRequestType.QUERY_STATE).setPartitionId(1);

    final var stateResponse = new CheckpointStateResponse();
    serverOutput.setResponseObject(stateResponse);
    handleRequest(request);

    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100)).matches(Either::isRight);
    assertThat(stateResponse.getBackupStates())
        .hasSize(1)
        .singleElement()
        .returns(10L, PartitionCheckpointState::checkpointId)
        .returns(1, PartitionCheckpointState::partitionId)
        .returns(CheckpointType.SCHEDULED_BACKUP, PartitionCheckpointState::checkpointType)
        .returns(100L, PartitionCheckpointState::checkpointTimestamp)
        .returns(20L, PartitionCheckpointState::checkpointPosition)
        .returns(5L, PartitionCheckpointState::firstLogPosition);
    assertThat(stateResponse.getCheckpointStates())
        .hasSize(1)
        .singleElement()
        .returns(15L, PartitionCheckpointState::checkpointId)
        .returns(1, PartitionCheckpointState::partitionId)
        .returns(CheckpointType.MANUAL_BACKUP, PartitionCheckpointState::checkpointType)
        .returns(150L, PartitionCheckpointState::checkpointTimestamp)
        .returns(25L, PartitionCheckpointState::checkpointPosition);
  }

  @Test
  void shouldReturnEmptyWhenNoCheckpointAndBackupArePresent() {
    // given
    when(checkpointState.getLatestCheckpointId()).thenReturn(-1L);
    when(checkpointState.getLatestBackupId()).thenReturn(-1L);

    final var request =
        new BackupRequest().setType(BackupRequestType.QUERY_STATE).setPartitionId(1);

    final var stateResponse = new CheckpointStateResponse();
    serverOutput.setResponseObject(stateResponse);
    handleRequest(request);

    // then
    assertThat(responseFuture).succeedsWithin(Duration.ofMinutes(1)).matches(Either::isRight);
    assertThat(stateResponse.getCheckpointStates()).isEmpty();
    assertThat(stateResponse.getBackupStates()).isEmpty();
  }

  @Test
  void shouldReturnBackupRanges() {
    // given
    final long checkpointId1 = 10;
    final long checkpointId2 = 20;
    final long checkpointId3 = 30;

    final var status1 = createBackupStatus(checkpointId1);
    final var status2 = createBackupStatus(checkpointId2);
    final var status3 = createBackupStatus(checkpointId2);
    final var status4 = createBackupStatus(checkpointId3);

    final var range1 = new BackupRangeStatus.Complete(status1, status2);
    final var range2 = new BackupRangeStatus.Incomplete(status3, status4, Set.of(25L));

    when(backupManager.getBackupRangeStatus())
        .thenReturn(CompletableActorFuture.completed(List.of(range1, range2)));

    final var request =
        new BackupRequest().setType(BackupRequestType.QUERY_RANGES).setPartitionId(1);

    // when
    final var rangesResponse = new BackupRangesResponse();
    serverOutput.setResponseObject(rangesResponse);
    handleRequest(request);

    // then
    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100)).matches(Either::isRight);
    assertThat(rangesResponse.getRanges())
        .containsExactlyInAnyOrder(
            new BackupRangesResponse.PartitionBackupRange(
                1, toCheckpointInfo(status1), toCheckpointInfo(status2), Set.of()),
            new BackupRangesResponse.PartitionBackupRange(
                1, toCheckpointInfo(status3), toCheckpointInfo(status4), Set.of(25L)));
  }

  private static BackupStatus createBackupStatus(final long checkpointId) {
    return new BackupStatusImpl(
        new BackupIdentifierImpl(1, 1, checkpointId),
        Optional.of(
            new BackupDescriptorImpl("s-id", 100, 3, "test", NOW, CheckpointType.MANUAL_BACKUP)),
        io.camunda.zeebe.backup.api.BackupStatusCode.COMPLETED,
        Optional.empty(),
        Optional.of(NOW),
        Optional.of(NOW));
  }

  private static CheckpointInfo toCheckpointInfo(final BackupStatus status) {
    final var descriptor = status.descriptor().orElseThrow();
    return new CheckpointInfo(
        status.id().checkpointId(),
        descriptor.firstLogPosition().orElse(-1L),
        descriptor.checkpointPosition(),
        descriptor.checkpointType(),
        descriptor.checkpointTimestamp());
  }

  private void handleRequest(final BackupRequest request) {
    final var requestBuffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(requestBuffer, 0);

    handler.onRequest(serverOutput, 1, 1, requestBuffer, 0, request.getLength());
    scheduler.workUntilDone();
  }

  final class ResponseReader implements ServerOutput {

    BufferReader responseWrapper;

    void setResponseObject(final BufferReader responseObject) {
      responseWrapper = responseObject;
    }

    @Override
    public void sendResponse(final ServerResponse serverResponse) {
      final var buffer = new ExpandableArrayBuffer();
      serverResponse.write(buffer, 0);

      final var error = new ErrorResponse();
      if (error.tryWrap(buffer)) {
        error.wrap(buffer, 0, serverResponse.getLength());
        responseFuture.complete(Either.left(error));
        return;
      }

      try {
        responseWrapper.wrap(buffer, 0, serverResponse.getLength());
        responseFuture.complete(Either.right(responseWrapper));
      } catch (final Exception e) {
        responseFuture.completeExceptionally(e);
      }
    }
  }
}
