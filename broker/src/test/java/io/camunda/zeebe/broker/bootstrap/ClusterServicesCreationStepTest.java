/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ClusterServicesCreationStepTest {
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final BrokerCfg TEST_CONFIGURATION = new BrokerCfg();

  private BrokerStartupContext mockBrokerStartupContext;

  private ActorFuture<BrokerStartupContext> future;

  private final ClusterServicesCreationStep sut = new ClusterServicesCreationStep();

  @BeforeEach
  void setUp() {

    final int internalApiPort = SocketUtil.getNextAddress().getPort();
    final var internalApiCfg = TEST_CONFIGURATION.getNetwork().getInternalApi();
    internalApiCfg.setPort(internalApiPort);
    internalApiCfg.setHost("localhost");
    internalApiCfg.setAdvertisedPort(internalApiPort);
    internalApiCfg.setAdvertisedHost("localhost");

    mockBrokerStartupContext = mock(BrokerStartupContext.class, Mockito.RETURNS_DEEP_STUBS);
    when(mockBrokerStartupContext.getConcurrencyControl()).thenReturn(CONCURRENCY_CONTROL);
    when(mockBrokerStartupContext.getBrokerConfiguration()).thenReturn(TEST_CONFIGURATION);
    when(mockBrokerStartupContext.getClusterServices().stop())
        .thenReturn(CompletableFuture.completedFuture(null));

    future = CONCURRENCY_CONTROL.createFuture();
  }

  @Test
  void shouldCompleteFutureOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isFalse();
    assertThat(future.join()).isNotNull();
  }

  @Test
  void shouldRegisterClusterServicesOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    verify(mockBrokerStartupContext).setClusterServices(Mockito.notNull());
  }

  @Test
  void shouldCompleteFutureOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isFalse();
    assertThat(future.join()).isNotNull();
  }

  @Test
  void shouldStopClusterServicesOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    verify(mockBrokerStartupContext.getClusterServices()).stop();
  }

  @Test
  void shouldUnregisterClusterServicesOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    verify(mockBrokerStartupContext).setClusterServices(null);
  }
}
