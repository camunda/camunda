/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;
import io.atomix.cluster.messaging.MessagingException.ConnectionClosed;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.RequestRetriesExhaustedException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.util.logging.RecordingAppender;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.slf4j.Log4jLogger;
import org.apache.logging.slf4j.Log4jMarkerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

final class GrpcErrorMapperTest {
  private final RecordingAppender recorder = new RecordingAppender();
  private final GrpcErrorMapper errorMapper = new GrpcErrorMapper();

  private Logger log;
  private Log4jLogger logger;

  @BeforeEach
  void beforeEach() {
    log = (Logger) LogManager.getLogger(UUID.randomUUID().toString());
    logger = new Log4jLogger(new Log4jMarkerFactory(), log, log.getName());

    recorder.start();
    log.addAppender(recorder);
  }

  @AfterEach
  void tearDown() {
    recorder.stop();
    log.removeAppender(recorder);
  }

  @Test
  void shouldLogResourceExhaustedErrorOnTrace() {
    // given
    final var brokerError = new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "Resources exhausted");
    final BrokerErrorException exception = new BrokerErrorException(brokerError);

    // when
    log.setLevel(Level.TRACE);
    final StatusRuntimeException statusException = errorMapper.mapError(exception, logger);

    // then
    assertThat(statusException.getStatus().getCode()).isEqualTo(Code.RESOURCE_EXHAUSTED);
    assertThat(recorder.getAppendedEvents()).hasSize(1);
    final LogEvent event = recorder.getAppendedEvents().get(0);
    assertThat(event.getLevel()).isEqualTo(Level.TRACE);
  }

  @Test
  void shouldAddDetailsForRequestRetriesExhaustedException() throws InvalidProtocolBufferException {
    // given
    final var exception = new RequestRetriesExhaustedException();
    final BrokerError brokerError =
        new BrokerError(ErrorCode.PARTITION_LEADER_MISMATCH, "Wrong partition");
    final BrokerErrorException detailException = new BrokerErrorException(brokerError);
    final Status expectedDetail =
        StatusProto.fromThrowable(errorMapper.mapError(detailException, NOPLogger.NOP_LOGGER));

    // when
    exception.addSuppressed(detailException);
    log.setLevel(Level.TRACE);
    final StatusRuntimeException statusException = errorMapper.mapError(exception, logger);
    final Status status = StatusProto.fromThrowable(statusException);

    // then
    assertThat(statusException.getStatus().getCode()).isEqualTo(Code.RESOURCE_EXHAUSTED);
    assertThat(recorder.getAppendedEvents()).hasSize(1);
    assertThat(recorder.getAppendedEvents().get(0).getLevel())
        .isEqualTo(Level.TRACE); // resource exhausted
    assertThat(status.getDetailsCount()).isEqualTo(1);

    final Status statusDetail = status.getDetails(0).unpack(Status.class);
    assertThat(statusDetail.getCode()).isEqualTo(expectedDetail.getCode());
    assertThat(statusDetail.getMessage()).isEqualTo(expectedDetail.getMessage());
  }

  @Test
  void shouldLogTimeoutExceptionWithCorrectStacktrace() {
    // given
    final ExecutionException executionException =
        new ExecutionException(new TimeoutException("Timed out after 1s"));

    // when
    log.setLevel(Level.TRACE);
    final StatusRuntimeException statusException = errorMapper.mapError(executionException, logger);

    // then
    assertThat(statusException.getStatus().getCode()).isEqualTo(Code.DEADLINE_EXCEEDED);
    assertThat(recorder.getAppendedEvents()).hasSize(1);
    final LogEvent event = recorder.getAppendedEvents().get(0);
    assertThat(event.getLevel()).isEqualTo(Level.DEBUG);

    assertThat(event.getThrown()).isEqualTo(executionException);
  }

  @Test
  void shouldLogJsonParseExceptionOnDebug() {
    // given
    try {
      MsgPackConverter.convertToMsgPack("{\"json\":\"invalid\"");
      fail("Expected to throw exception");
    } catch (final RuntimeException runtimeException) {
      assertThat(runtimeException.getCause()).isInstanceOf(JsonParseException.class);
      final JsonParseException exception = (JsonParseException) runtimeException.getCause();

      // when
      log.setLevel(Level.DEBUG);
      final StatusRuntimeException statusException = errorMapper.mapError(exception, logger);

      // then
      assertThat(statusException.getStatus().getCode()).isEqualTo(Code.INVALID_ARGUMENT);
      assertThat(recorder.getAppendedEvents()).hasSize(1);
      final LogEvent event = recorder.getAppendedEvents().get(0);
      assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
    }
  }

  @Test
  void shouldLogIllegalArgumentExceptionOnDebugWithBigIntTooLong() {
    // given
    try {
      MsgPackConverter.convertToMsgPack("{\"mybigintistoolong\":123456789012345678901234567890}");
      fail("Expected to throw exception");
    } catch (final RuntimeException runtimeException) {
      assertThat(runtimeException.getCause()).isInstanceOf(IllegalArgumentException.class);
      final IllegalArgumentException exception =
          (IllegalArgumentException) runtimeException.getCause();

      // when
      log.setLevel(Level.DEBUG);
      final StatusRuntimeException statusException = errorMapper.mapError(exception, logger);

      // then
      assertThat(statusException.getStatus().getCode()).isEqualTo(Code.INVALID_ARGUMENT);
      assertThat(recorder.getAppendedEvents()).hasSize(1);
      final LogEvent event = recorder.getAppendedEvents().get(0);
      assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
    }
  }

  @Test
  void shouldReturnAbortedOnConnectionClosed() {
    // given
    final var error = new ConnectionClosed("Closed");

    // when
    log.setLevel(Level.DEBUG);
    final StatusRuntimeException statusException = errorMapper.mapError(error, logger);

    // then
    assertThat(statusException.getStatus().getCode()).isEqualTo(Code.ABORTED);
  }

  @Test
  void shouldLogMaxMessageSizeExceededErrorOnDebug() {
    // given
    final var brokerError =
        new BrokerError(ErrorCode.MALFORMED_REQUEST, "Max message size exceeded");
    final BrokerErrorException exception = new BrokerErrorException(brokerError);

    // when
    log.setLevel(Level.DEBUG);
    final StatusRuntimeException statusException = errorMapper.mapError(exception, logger);

    // then
    assertThat(statusException.getStatus().getCode()).isEqualTo(Code.INVALID_ARGUMENT);
    assertThat(recorder.getAppendedEvents()).hasSize(1);
    final LogEvent event = recorder.getAppendedEvents().getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
  }
}
