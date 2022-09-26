/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.encoding.BackupRequest;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.management.BackupStatusResponseEncoder;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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

  @RegisterExtension
  ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

  @Mock AtomixServerTransport transport;

  @Mock(answer = Answers.RETURNS_SELF)
  LogStreamRecordWriter logStreamRecordWriter;

  @Mock BackupManager backupManager;

  BackupApiRequestHandler handler;
  private ServerOutput serverOutput;
  private CompletableFuture<Either<ErrorResponse, BackupStatusResponse>> responseFuture;

  @BeforeEach
  void setup() {
    handler = new BackupApiRequestHandler(transport, logStreamRecordWriter, backupManager, 1, true);
    scheduler.submitActor(handler);
    scheduler.workUntilDone();

    serverOutput = createServerOutput();
    responseFuture = new CompletableFuture<>();
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
    verify(logStreamRecordWriter, times(1)).tryWrite();
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
    verify(logStreamRecordWriter, never()).tryWrite();
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
            Optional.of(new BackupDescriptorImpl(Optional.of("s-id"), 100, 3, "test")),
            io.camunda.zeebe.backup.api.BackupStatusCode.COMPLETED,
            Optional.empty(),
            Optional.of(createdAt),
            Optional.of(lastModified));

    when(backupManager.getBackupStatus(checkpointId))
        .thenReturn(CompletableActorFuture.completed(status));

    // when
    handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMillis(100))
        .matches(Either::isRight)
        .extracting(Either::get)
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
    handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMillis(100))
        .matches(Either::isRight)
        .extracting(Either::get)
        .returns(checkpointId, BackupStatusResponse::getBackupId)
        .returns(1, BackupStatusResponse::getPartitionId)
        .returns(1, BackupStatusResponse::getBrokerId)
        .returns(
            BackupStatusResponseEncoder.backupIdNullValue(),
            BackupStatusResponse::getCheckpointPosition)
        .returns(
            BackupStatusResponseEncoder.numberOfPartitionsNullValue(),
            BackupStatusResponse::getNumberOfPartitions)
        .matches(response -> response.getSnapshotId().isEmpty())
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

  private void handleRequest(final BackupRequest request) {
    final var requestBuffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(requestBuffer, 0);

    handler.onRequest(serverOutput, 1, 1, requestBuffer, 0, request.getLength());
    scheduler.workUntilDone();
  }

  private ServerOutput createServerOutput() {
    return serverResponse -> {
      final var buffer = new ExpandableArrayBuffer();
      serverResponse.write(buffer, 0);

      final var error = new ErrorResponse();
      if (error.tryWrap(buffer)) {
        error.wrap(buffer, 0, serverResponse.getLength());
        responseFuture.complete(Either.left(error));
        return;
      }

      final var response = new BackupStatusResponse();
      try {
        response.wrap(buffer, 0, serverResponse.getLength());
        responseFuture.complete(Either.right(response));
      } catch (final Exception e) {
        responseFuture.completeExceptionally(e);
      }
    };
  }
}
