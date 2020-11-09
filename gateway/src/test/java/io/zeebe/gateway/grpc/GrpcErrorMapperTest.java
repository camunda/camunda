/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.zeebe.gateway.cmd.BrokerErrorException;
import io.zeebe.gateway.impl.broker.RequestRetriesExhaustedException;
import io.zeebe.gateway.impl.broker.response.BrokerError;
import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.util.logging.RecordingAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.slf4j.Log4jLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.helpers.NOPLogger;

@Execution(ExecutionMode.CONCURRENT)
final class GrpcErrorMapperTest {
  private final RecordingAppender recorder = new RecordingAppender();
  private final Logger log = (Logger) LogManager.getLogger(GrpcErrorMapperTest.class);
  private final Log4jLogger logger = new Log4jLogger(log, log.getName());
  private final GrpcErrorMapper errorMapper = new GrpcErrorMapper();

  @BeforeEach
  void setUp() {
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
    final BrokerError brokerError =
        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "Resources exhausted");
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
    final RequestRetriesExhaustedException exception = new RequestRetriesExhaustedException();
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
    assertThat(recorder.getAppendedEvents()).hasSize(2);
    assertThat(recorder.getAppendedEvents().get(0).getLevel())
        .isEqualTo(Level.TRACE); // partition leader mismatch
    assertThat(recorder.getAppendedEvents().get(1).getLevel())
        .isEqualTo(Level.TRACE); // resource exhausted
    assertThat(status.getDetailsCount()).isEqualTo(1);

    final Status statusDetail = status.getDetails(0).unpack(Status.class);
    assertThat(statusDetail.getCode()).isEqualTo(expectedDetail.getCode());
    assertThat(statusDetail.getMessage()).isEqualTo(expectedDetail.getMessage());
  }
}
