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

import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClusterServicesStepTest {
  private static final BrokerCfg TEST_CONFIGURATION = new BrokerCfg();
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final Duration TIME_OUT = Duration.ofSeconds(10);

  private BrokerStartupContext mockBrokerStartupContext;
  private ClusterServicesImpl mockClusterServices;

  private ActorFuture<BrokerStartupContext> future;

  private final ClusterServicesStep sut = new ClusterServicesStep();

  @BeforeEach
  void setUp() {
    final int internalApiPort = SocketUtil.getNextAddress().getPort();
    final var internalApiCfg = TEST_CONFIGURATION.getNetwork().getInternalApi();
    internalApiCfg.setPort(internalApiPort);
    internalApiCfg.setHost("localhost");
    internalApiCfg.setAdvertisedPort(internalApiPort);
    internalApiCfg.setAdvertisedHost("localhost");

    mockBrokerStartupContext = mock(BrokerStartupContext.class);

    mockClusterServices = mock(ClusterServicesImpl.class);
    when(mockClusterServices.start()).thenReturn(CompletableFuture.completedFuture(null));
    when(mockClusterServices.stop()).thenReturn(CompletableFuture.completedFuture(null));

    when(mockBrokerStartupContext.getConcurrencyControl()).thenReturn(CONCURRENCY_CONTROL);
    when(mockBrokerStartupContext.getClusterServices()).thenReturn(mockClusterServices);
    when(mockBrokerStartupContext.getBrokerConfiguration()).thenReturn(TEST_CONFIGURATION);

    future = CONCURRENCY_CONTROL.createFuture();
  }

  @Test
  void shouldCompleteFutureOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    assertThat(future.join()).isNotNull();
  }

  @Test
  void shouldRegisterClusterServicesOnStartup() {
    // when
    sut.startupInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    final var argumentCaptor = ArgumentCaptor.forClass(ClusterServicesImpl.class);
    verify(mockBrokerStartupContext).setClusterServices(argumentCaptor.capture());
  }

  @Test
  void shouldCompleteFutureOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    assertThat(future.join()).isNotNull();
  }

  @Test
  void shouldStopClusterServicesOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    verify(mockClusterServices).stop();
  }

  @Test
  void shouldUnregisterClusterServicesOnShutdown() {
    // when
    sut.shutdownInternal(mockBrokerStartupContext, CONCURRENCY_CONTROL, future);
    await().until(future::isDone);

    // then
    verify(mockBrokerStartupContext).setClusterServices(null);
  }
}
