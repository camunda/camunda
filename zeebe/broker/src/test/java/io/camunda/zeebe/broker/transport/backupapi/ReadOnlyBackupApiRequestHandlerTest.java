/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.management.ReadOnlyBackupService;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRequest;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.ServerResponse;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferReader;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadOnlyBackupApiRequestHandlerTest {

  private static final int PARTITION_ID = 1;
  private static final long BACKUP_ID = 10L;

  @RegisterExtension
  ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

  @Mock AtomixServerTransport transport;
  @Mock ReadOnlyBackupService backupService;

  ReadOnlyBackupApiRequestHandler handler;
  ResponseReader serverOutput;
  CompletableFuture<Either<ErrorResponse, BufferReader>> responseFuture;

  @BeforeEach
  void setup() {
    handler =
        new ReadOnlyBackupApiRequestHandler(
            backupService,
            new PartitionId(Protocol.DEFAULT_PARTITION_GROUP_NAME, PARTITION_ID),
            transport,
            true);
    scheduler.submitActor(handler);
    scheduler.workUntilDone();

    serverOutput = new ResponseReader();
    responseFuture = new CompletableFuture<>();

    final var doesNotExist =
        new BackupStatusImpl(
            new BackupIdentifierImpl(0, PARTITION_ID, BACKUP_ID),
            Optional.empty(),
            BackupStatusCode.DOES_NOT_EXIST,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    lenient()
        .when(backupService.getBackupStatus(anyLong()))
        .thenReturn(CompletableActorFuture.completed(doesNotExist));
    lenient()
        .when(backupService.listBackups(anyString()))
        .thenReturn(CompletableActorFuture.completed(List.of()));
    lenient()
        .when(backupService.getBackupRangeStatus())
        .thenReturn(CompletableActorFuture.completed(List.of()));
  }

  // ── read operations: allowed ─────────────────────────────────────────────

  @Test
  void shouldHandleQueryStatusRequest() {
    // given
    final var request =
        new BackupRequest()
            .setType(BackupRequestType.QUERY_STATUS)
            .setPartitionId(PARTITION_ID)
            .setBackupId(BACKUP_ID);
    serverOutput.setResponseObject(new BackupStatusResponse());

    // when
    handleRequest(request);

    // then
    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100)).matches(Either::isRight);
    verify(backupService).getBackupStatus(BACKUP_ID);
  }

  @Test
  void shouldHandleListRequest() {
    // given
    final var request =
        new BackupRequest().setType(BackupRequestType.LIST).setPartitionId(PARTITION_ID);
    serverOutput.setResponseObject(new BackupListResponse());

    // when
    handleRequest(request);

    // then
    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100)).matches(Either::isRight);
    verify(backupService).listBackups(any());
  }

  @Test
  void shouldHandleQueryRangesRequest() {
    // given
    final var request =
        new BackupRequest().setType(BackupRequestType.QUERY_RANGES).setPartitionId(PARTITION_ID);
    serverOutput.setResponseObject(new BackupRangesResponse());

    // when
    handleRequest(request);

    // then
    assertThat(responseFuture).succeedsWithin(Duration.ofMillis(100)).matches(Either::isRight);
    verify(backupService).getBackupRangeStatus();
  }

  // ── write operations: rejected ───────────────────────────────────────────

  @Test
  void shouldRejectTakeBackupDuringRecovery() {
    // given
    final var request =
        new BackupRequest()
            .setType(BackupRequestType.TAKE_BACKUP)
            .setPartitionId(PARTITION_ID)
            .setBackupId(BACKUP_ID);

    // when
    handleRequest(request);

    // then
    assertUnsupportedDuringRecovery();
  }

  @Test
  void shouldRejectDeleteDuringRecovery() {
    // given
    final var request =
        new BackupRequest()
            .setType(BackupRequestType.DELETE)
            .setPartitionId(PARTITION_ID)
            .setBackupId(BACKUP_ID);

    // when
    handleRequest(request);

    // then
    assertUnsupportedDuringRecovery();
  }

  @Test
  void shouldRejectQueryStateDuringRecovery() {
    // given
    final var request =
        new BackupRequest().setType(BackupRequestType.QUERY_STATE).setPartitionId(PARTITION_ID);

    // when
    handleRequest(request);

    // then
    assertUnsupportedDuringRecovery();
  }

  @Test
  void shouldRejectSyncMetadataDuringRecovery() {
    // given
    final var request =
        new BackupRequest().setType(BackupRequestType.SYNC_METADATA).setPartitionId(PARTITION_ID);

    // when
    handleRequest(request);

    // then
    assertUnsupportedDuringRecovery();
  }

  @Test
  void shouldRejectClearStateDuringRecovery() {
    // given
    final var request =
        new BackupRequest().setType(BackupRequestType.CLEAR_STATE).setPartitionId(PARTITION_ID);

    // when
    handleRequest(request);

    // then
    assertUnsupportedDuringRecovery();
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private void assertUnsupportedDuringRecovery() {
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMillis(100))
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode)
        .isEqualTo(ErrorCode.UNSUPPORTED_MESSAGE);
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
        if (responseWrapper != null) {
          responseWrapper.wrap(buffer, 0, serverResponse.getLength());
        }
        responseFuture.complete(Either.right(responseWrapper));
      } catch (final Exception e) {
        responseFuture.completeExceptionally(e);
      }
    }
  }
}
