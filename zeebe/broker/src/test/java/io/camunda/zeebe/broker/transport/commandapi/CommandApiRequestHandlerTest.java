/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteCommandRequest;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteCommandResponse;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteQueryRequest;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerRule;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CommandApiRequestHandlerTest {
  @Rule public final ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();
  final CommandApiRequestHandler handler = new CommandApiRequestHandler();
  private LogStreamWriter logStreamWriter;

  @Before
  public void setup() {
    scheduler.submitActor(handler);
    logStreamWriter = mock(LogStreamWriter.class);
    handler.addPartition(0, logStreamWriter);
    scheduler.workUntilDone();
  }

  @Test
  public void shouldRejectCommandWithInvalidTemplate() {
    // given
    final var request = new ExecuteQueryRequest();

    // when
    final var responseFuture = handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft)
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode)
        .isEqualTo(ErrorCode.INVALID_MESSAGE_TEMPLATE);
  }

  @Test
  public void shouldRejectCommandIfNotLeader() {
    // given
    final var request = new ExecuteCommandRequest();
    handler.removePartition(0);
    scheduler.workUntilDone();

    // when
    final var responseFuture = handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft)
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode)
        .isEqualTo(ErrorCode.PARTITION_LEADER_MISMATCH);
  }

  @Test
  public void shouldRejectCommandWithoutEvent() {
    // given
    final var request = new ExecuteCommandRequest();

    // when
    final var responseFuture = handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft)
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode)
        .isEqualTo(ErrorCode.UNSUPPORTED_MESSAGE);
  }

  @Test
  public void shouldRejectCommandWithUnknownEvent() {
    // given
    final var request = new ExecuteCommandRequest();
    request.setValueType(ValueType.ERROR);

    // when
    final var responseFuture = handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft)
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode)
        .isEqualTo(ErrorCode.UNSUPPORTED_MESSAGE);
  }

  @Test
  public void shouldWriteToLog() {
    // given
    final var logWriter = mock(LogStreamWriter.class);
    when(logWriter.canWriteEvents(anyInt(), anyInt())).thenReturn(true);
    handler.addPartition(0, logWriter);
    scheduler.workUntilDone();

    final var request =
        new BrokerPublishMessageRequest("test", "1").setMessageId("1").setTimeToLive(0);
    request.serializeValue();

    // when
    handleRequest(request);

    // then
    verify(logWriter).tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  @Test
  public void shouldReturnProcessingPausedIfPartitionPaused() {
    // given
    final var logWriter = mock(LogStreamWriter.class);
    when(logWriter.canWriteEvents(anyInt(), anyInt())).thenReturn(true);
    when(logWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED));
    handler.addPartition(0, logWriter);
    handler.onRecovered(0);
    handler.onPaused(0);
    scheduler.workUntilDone();

    final var request =
        new BrokerPublishMessageRequest("test", "1").setMessageId("1").setTimeToLive(0);
    request.serializeValue();

    // when
    final var responseFuture = handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(5))
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorData)
        .extracting(errorData -> BufferUtil.bufferAsString(errorData))
        .isEqualTo("Processing paused for partition '0'");
  }

  @Test
  public void shouldReturnPartitionLeaderMismatchWhenWriterClosed() {
    // given
    final var logWriter = mock(LogStreamWriter.class);
    when(logWriter.canWriteEvents(anyInt(), anyInt())).thenReturn(true);
    when(logWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.left(WriteFailure.CLOSED));
    handler.addPartition(0, logWriter);
    scheduler.workUntilDone();

    final var request =
        new BrokerPublishMessageRequest("test", "1").setMessageId("1").setTimeToLive(0);
    request.serializeValue();

    // when
    final var responseFuture = handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode)
        .isEqualTo(ErrorCode.PARTITION_LEADER_MISMATCH);
  }

  @Test
  public void shouldRejectRequestIfTooLarge() {
    when(logStreamWriter.canWriteEvents(anyInt(), anyInt())).thenReturn(false);

    final var request =
        new BrokerPublishMessageRequest("test", "1").setMessageId("1").setTimeToLive(0);
    request.serializeValue();

    // when
    final var responseFuture = handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode, e -> BufferUtil.bufferAsString(e.getErrorData()))
        .containsExactly(
            ErrorCode.MALFORMED_REQUEST, "Request size is above configured maxMessageSize.");
  }

  @Test
  public void shouldRejectClusterVersionGatedCommand_whenClusterBelowGateOrdinal() {
    // given a PING command (gated at line 810, ordinal 10) while the active ECV is still (0, 0)
    final var pingValue =
        new ClusterVersionRecord().setLine(0).setOrdinal(0).setGatedField("hello");
    final var pingBuffer = new UnsafeBuffer(new byte[pingValue.getLength()]);
    pingValue.write(pingBuffer, 0);

    final var request =
        new ExecuteCommandRequest()
            .setValueType(ValueType.CLUSTER_VERSION)
            .setIntent(ClusterVersionIntent.PING)
            .setValue(pingBuffer, 0, pingValue.getLength());

    // when the request hits the broker's command-API admission path
    final var responseFuture = handleRequest(request);

    // then the broker responds synchronously with an UNSUPPORTED_MESSAGE rejection
    assertThat(responseFuture).succeedsWithin(Duration.ofMinutes(1)).matches(Either::isLeft);
    final var error = responseFuture.join().getLeft();
    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MESSAGE);
    assertThat(BufferUtil.bufferAsString(error.getErrorData()))
        .contains("not yet available")
        .contains("ordinal 10")
        // Broker handler defaults to ClusterVersionState.INITIAL_ORDINAL, which is the BASELINE
        // (ordinal 1). The PING gate (10) is still well above it.
        .contains("active ordinal is 1");

    // and: logStreamWriter.tryWrite was NEVER called — the command never reached the log stream
    verify(logStreamWriter, never()).tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  @Test
  public void shouldGateMultipleCommandsAtTheirOwnOrdinals_progressively() {
    // The admission registry holds two gated commands: ECHO at (810, 3), PING at (810, 10). As the
    // cluster's active ECV grows, the set of admissible commands grows monotonically with it.
    when(logStreamWriter.canWriteEvents(anyInt(), anyInt())).thenReturn(true);
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(0L));

    final var echoValue = new ClusterVersionRecord().setGatedField("e");
    final var echoBuffer = new UnsafeBuffer(new byte[echoValue.getLength()]);
    echoValue.write(echoBuffer, 0);
    final var pingValue = new ClusterVersionRecord().setGatedField("p");
    final var pingBuffer = new UnsafeBuffer(new byte[pingValue.getLength()]);
    pingValue.write(pingBuffer, 0);

    // stage 1: active (0, 0) — both gated, both rejected
    final var echoAtZero = makeClusterVersionRequest(ClusterVersionIntent.ECHO, echoBuffer);
    final var pingAtZero = makeClusterVersionRequest(ClusterVersionIntent.PING, pingBuffer);
    assertThat(handleRequest(echoAtZero))
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft);
    assertThat(handleRequest(pingAtZero))
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft);
    verify(logStreamWriter, never()).tryWrite(any(WriteContext.class), any(LogAppendEntry.class));

    // stage 2: active (810, 3) — ECHO opens, PING still closed
    handler.updateActiveClusterVersion(810, 3);
    scheduler.workUntilDone();

    handleRequest(makeClusterVersionRequest(ClusterVersionIntent.ECHO, echoBuffer));
    scheduler.workUntilDone();
    verify(logStreamWriter, org.mockito.Mockito.times(1))
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));

    assertThat(handleRequest(makeClusterVersionRequest(ClusterVersionIntent.PING, pingBuffer)))
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft);
    // still only one write — PING was rejected synchronously
    verify(logStreamWriter, org.mockito.Mockito.times(1))
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));

    // stage 3: active (810, 10) — both open
    handler.updateActiveClusterVersion(810, 10);
    scheduler.workUntilDone();

    handleRequest(makeClusterVersionRequest(ClusterVersionIntent.PING, pingBuffer));
    scheduler.workUntilDone();
    verify(logStreamWriter, org.mockito.Mockito.times(2))
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  private static ExecuteCommandRequest makeClusterVersionRequest(
      final ClusterVersionIntent intent, final UnsafeBuffer valueBuffer) {
    return new ExecuteCommandRequest()
        .setValueType(ValueType.CLUSTER_VERSION)
        .setIntent(intent)
        .setValue(valueBuffer, 0, valueBuffer.capacity());
  }

  @Test
  public void shouldAdmitClusterVersionGatedCommand_afterUpdatingActiveClusterVersionToGate() {
    // given the broker has been informed the cluster is at the PING gate's (line, ordinal)
    handler.updateActiveClusterVersion(810, 10);
    when(logStreamWriter.canWriteEvents(anyInt(), anyInt())).thenReturn(true);
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(0L));
    scheduler.workUntilDone();

    final var pingValue = new ClusterVersionRecord().setGatedField("hello");
    final var pingBuffer = new UnsafeBuffer(new byte[pingValue.getLength()]);
    pingValue.write(pingBuffer, 0);

    final var request =
        new ExecuteCommandRequest()
            .setValueType(ValueType.CLUSTER_VERSION)
            .setIntent(ClusterVersionIntent.PING)
            .setValue(pingBuffer, 0, pingValue.getLength());

    // when the request hits the admission path
    handleRequest(request);

    // then the gate admits and writeCommand is called — the PING reaches the log
    verify(logStreamWriter).tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  private CompletableFuture<Either<ErrorResponse, ExecuteCommandResponse>> handleRequest(
      final BufferWriter request) {
    final var future = new CompletableFuture<Either<ErrorResponse, ExecuteCommandResponse>>();
    final ServerOutput serverOutput = createServerOutput(future);
    final var requestBuffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(requestBuffer, 0);
    handler.onRequest(serverOutput, 0, 0, requestBuffer, 0, request.getLength());
    scheduler.workUntilDone();
    return future;
  }

  private ServerOutput createServerOutput(
      final CompletableFuture<Either<ErrorResponse, ExecuteCommandResponse>> future) {
    return serverResponse -> {
      final var buffer = new ExpandableArrayBuffer();
      serverResponse.write(buffer, 0);

      final var error = new ErrorResponse();
      if (error.tryWrap(buffer)) {
        error.wrap(buffer, 0, serverResponse.getLength());
        future.complete(Either.left(error));
        return;
      }

      final var response = new ExecuteCommandResponse();
      try {
        response.wrap(buffer, 0, serverResponse.getLength());
        future.complete(Either.right(response));
      } catch (final Exception e) {
        future.completeExceptionally(e);
      }
    };
  }
}
