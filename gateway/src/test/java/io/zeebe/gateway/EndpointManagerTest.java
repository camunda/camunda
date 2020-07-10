/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.StatusRuntimeException;
import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.api.workflow.CreateWorkflowInstanceStub;
import io.zeebe.gateway.impl.broker.response.BrokerError;
import io.zeebe.gateway.impl.broker.response.BrokerErrorResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceRequest;
import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.util.logging.RecordingAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.assertj.core.groups.Tuple;
import org.junit.Test;

public final class EndpointManagerTest extends GatewayTest {

  private RecordingAppender recorder;
  private Logger log;

  @Override
  public void setUp() {
    super.setUp();
    recorder = new RecordingAppender();
    recorder.start();
    log = (Logger) LogManager.getLogger(Loggers.GATEWAY_LOGGER.getName());
    log.addAppender(recorder);
  }

  @Override
  public void tearDown() {
    super.tearDown();
    recorder.stop();
    log.removeAppender(recorder);
  }

  @Test
  public void shouldNotLogErrorOnSuccess() {
    // given
    new CreateWorkflowInstanceStub().registerWith(brokerClient);

    // when
    client.createWorkflowInstance(CreateWorkflowInstanceRequest.newBuilder().build());

    // and verify that we did not log at error level
    assertThat(recorder.getAppendedEvents())
        .extracting(LogEvent::getLevel)
        .doesNotContain(Level.ERROR);
  }

  @Test
  public void shouldLogBrokerError() {
    // given
    new CreateWorkflowInstanceStub()
        .respondWith(
            new BrokerErrorResponse<>(
                new BrokerError(
                    ErrorCode.INTERNAL_ERROR,
                    "Expected to create workflow instance, but internal error occurred")))
        .registerWith(brokerClient);

    // when
    assertThatThrownBy(
            () -> client.createWorkflowInstance(CreateWorkflowInstanceRequest.newBuilder().build()))
        .isInstanceOf(StatusRuntimeException.class);

    // and verify that we logged the BrokerError at error level
    assertThat(recorder.getAppendedEvents())
        .extracting(
            LogEvent::getLevel,
            logEvent -> logEvent.getSource().getClassName(),
            logEvent -> logEvent.getSource().getMethodName())
        .contains(Tuple.tuple(Level.ERROR, "io.zeebe.gateway.EndpointManager", "convertThrowable"));
  }
}
