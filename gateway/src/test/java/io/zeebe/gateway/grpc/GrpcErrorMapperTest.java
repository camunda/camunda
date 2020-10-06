/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.zeebe.gateway.cmd.BrokerErrorException;
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

@Execution(ExecutionMode.CONCURRENT)
final class GrpcErrorMapperTest {
  private final RecordingAppender recorder = new RecordingAppender();
  private final Logger log = (Logger) LogManager.getLogger(GrpcErrorMapperTest.class);
  private final GrpcErrorMapper errorMapper =
      new GrpcErrorMapper(new Log4jLogger(log, log.getName()));

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
    final StatusRuntimeException statusException = errorMapper.mapError(exception);

    // then
    assertThat(statusException.getStatus().getCode()).isEqualTo(Code.RESOURCE_EXHAUSTED);
    assertThat(recorder.getAppendedEvents()).hasSize(1);
    final LogEvent event = recorder.getAppendedEvents().get(0);
    assertThat(event.getLevel()).isEqualTo(Level.TRACE);
  }
}
