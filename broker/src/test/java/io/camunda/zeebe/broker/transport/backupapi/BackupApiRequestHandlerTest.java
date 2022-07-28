/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.encoding.AdminResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRequest;
import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.record.BackupRequestType;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class BackupApiRequestHandlerTest {

  @RegisterExtension
  ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

  final AtomixServerTransport transport = mock(AtomixServerTransport.class);
  BackupApiRequestHandler handler;
  private final LogStreamRecordWriter logStreamRecordWriter = mock(LogStreamRecordWriter.class);
  private ServerOutput serverOutput;

  private CompletableFuture<Either<ErrorResponse, AdminResponse>> responseFuture;

  @BeforeEach
  void setup() {
    when(logStreamRecordWriter.metadataWriter(any())).thenReturn(logStreamRecordWriter);
    when(logStreamRecordWriter.valueWriter(any())).thenReturn(logStreamRecordWriter);

    handler = new BackupApiRequestHandler(transport, logStreamRecordWriter, 1);
    scheduler.submitActor(handler);
    scheduler.workUntilDone();

    serverOutput = createServerOutput();
    responseFuture = new CompletableFuture<>();
  }

  @Test
  void shouldRejectRequestWithInvalidType() {
    // given
    final var request =
        new BackupRequest()
            .setType(BackupRequestType.NULL_VAL)
            .setPartitionId(1)
            .setCheckpointId(10);

    // when
    final var requestBuffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(requestBuffer, 0);

    handler.onRequest(serverOutput, 1, 1, requestBuffer, 0, request.getLength());
    scheduler.workUntilDone();

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
            .setCheckpointId(10);

    // when
    final var requestBuffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(requestBuffer, 0);
    handler.onRequest(serverOutput, 1, 1, requestBuffer, 0, request.getLength());
    scheduler.workUntilDone();

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
            .setCheckpointId(10);

    handler.onDiskSpaceNotAvailable();
    scheduler.workUntilDone();

    // when
    final var requestBuffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(requestBuffer, 0);

    handler.onRequest(serverOutput, 1, 1, requestBuffer, 0, request.getLength());
    scheduler.workUntilDone();

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
            .setCheckpointId(10);

    handler.onDiskSpaceNotAvailable();
    scheduler.workUntilDone();
    handler.onDiskSpaceAvailable();
    scheduler.workUntilDone();

    // when
    final var requestBuffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(requestBuffer, 0);

    handler.onRequest(serverOutput, 1, 1, requestBuffer, 0, request.getLength());
    scheduler.workUntilDone();

    // then
    verify(logStreamRecordWriter, times(1)).tryWrite();
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

      final var response = new AdminResponse();
      try {
        response.wrap(buffer, 0, serverResponse.getLength());
        responseFuture.complete(Either.right(response));
      } catch (final Exception e) {
        responseFuture.completeExceptionally(e);
      }
    };
  }
}
