/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerClockAddRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerClockPinRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerClockResetRequest;
import io.camunda.zeebe.protocol.Protocol;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Clock changes must all be made on the same partition, so that they reach the other partitions in
 * the order they were made.
 */
final class ClockServicesTest {

  private final CamundaAuthentication authentication =
      CamundaAuthentication.of(builder -> builder.user("foo"));
  private StubbedBrokerClient brokerClient;
  private ClockServices services;

  @BeforeEach
  void setUp() {
    brokerClient = new StubbedBrokerClient();
    final var executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(ForkJoinPool.commonPool());
    services =
        new ClockServices(
            "test-tenant",
            brokerClient,
            mock(SecurityContextProvider.class),
            executorProvider,
            mock(BrokerRequestAuthorizationConverter.class));
  }

  @Test
  void shouldSendPinToDeploymentPartition() {
    // when
    services.pinClock(1_000L, authentication);

    // then
    final BrokerClockPinRequest request = brokerClient.getSingleBrokerRequest();
    assertThat(request.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
  }

  @Test
  void shouldSendAddToDeploymentPartition() {
    // when
    services.addClock(1_000L, authentication);

    // then
    final BrokerClockAddRequest request = brokerClient.getSingleBrokerRequest();
    assertThat(request.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
  }

  @Test
  void shouldSendResetToDeploymentPartition() {
    // when
    services.resetClock(authentication);

    // then
    final BrokerClockResetRequest request = brokerClient.getSingleBrokerRequest();
    assertThat(request.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
  }
}
