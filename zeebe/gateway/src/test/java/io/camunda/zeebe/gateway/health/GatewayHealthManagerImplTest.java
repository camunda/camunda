/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.zeebe.gateway.health.impl.GatewayHealthManagerImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.HealthStatusManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class GatewayHealthManagerImplTest {
  private GatewayHealthManagerImpl gatewayHealthManagerImpl;
  private HealthStatusManager statusManager;

  @BeforeEach
  void setUp() {
    statusManager = mock(HealthStatusManager.class);
    gatewayHealthManagerImpl = new GatewayHealthManagerImpl(statusManager);
  }

  @Test
  void shouldHaveNonServingStatusOnCreation() {
    // then
    assertThat(gatewayHealthManagerImpl.getStatus()).isEqualTo(Status.INITIAL);

    verify(statusManager).setStatus(GatewayGrpc.SERVICE_NAME, ServingStatus.NOT_SERVING);
    verify(statusManager)
        .setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, ServingStatus.NOT_SERVING);
    verifyNoMoreInteractions(statusManager);
  }

  @Test
  void shouldSetServingStatusOnRunningStatus() {
    // when
    gatewayHealthManagerImpl.setStatus(Status.RUNNING);
    // then
    verify(statusManager).setStatus(GatewayGrpc.SERVICE_NAME, ServingStatus.SERVING);
    verify(statusManager)
        .setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, ServingStatus.SERVING);
  }

  @Test
  void shouldSetNonServingStatusOnStartingStatus() {
    // when
    gatewayHealthManagerImpl.setStatus(Status.STARTING);
    // then
    verify(statusManager, times(2)).setStatus(GatewayGrpc.SERVICE_NAME, ServingStatus.NOT_SERVING);
    verify(statusManager, times(2))
        .setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, ServingStatus.NOT_SERVING);
    verifyNoMoreInteractions(statusManager);
  }

  @Test
  void shouldNotSetOtherStatusIfInTheShutdownStatus() {
    // given
    gatewayHealthManagerImpl.setStatus(Status.SHUTDOWN);
    // when
    gatewayHealthManagerImpl.setStatus(Status.RUNNING);
    // then
    verify(statusManager, never()).setStatus(GatewayGrpc.SERVICE_NAME, ServingStatus.SERVING);
    verify(statusManager, never())
        .setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, ServingStatus.SERVING);
  }
}
