/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.system.monitoring.BrokerStepMetrics;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.startup.StartupStep;
import io.prometheus.client.Gauge.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BrokerStepMetricDecoratorTest {

  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final String DELEGATE_STEP_NAME = "delegate step";

  private BrokerStartupContext mockBrokerStartupContext;
  private BrokerStepMetrics mockBrokerStepMetrics;
  private StartupStep<BrokerStartupContext> mockStep;
  private BrokerStepMetricDecorator sut;
  private ActorFuture<BrokerStartupContext> startupFuture;
  private ActorFuture<BrokerStartupContext> shutdownFuture;
  private Timer mockStartupTimer;
  private Timer mockCloseTimer;

  @BeforeEach
  void setUp() {
    mockStartupTimer = mock(Timer.class);
    mockCloseTimer = mock(Timer.class);

    mockBrokerStepMetrics = mock(BrokerStepMetrics.class);
    when(mockBrokerStepMetrics.createStartupTimer(any())).thenReturn(mockStartupTimer);
    when(mockBrokerStepMetrics.createCloseTimer(any())).thenReturn(mockCloseTimer);

    mockBrokerStartupContext = mock(BrokerStartupContext.class);
    when(mockBrokerStartupContext.getConcurrencyControl()).thenReturn(CONCURRENCY_CONTROL);

    startupFuture = CONCURRENCY_CONTROL.createFuture();
    shutdownFuture = CONCURRENCY_CONTROL.createFuture();

    mockStep = mock(StartupStep.class);
    when(mockStep.startup(mockBrokerStartupContext)).thenReturn(startupFuture);
    when(mockStep.shutdown(mockBrokerStartupContext)).thenReturn(shutdownFuture);
    when(mockStep.getName()).thenReturn(DELEGATE_STEP_NAME);

    sut = new BrokerStepMetricDecorator(mockBrokerStepMetrics, mockStep);
  }

  @Test
  void shouldCallStartupOnDelegate() {
    // when
    sut.startup(mockBrokerStartupContext);

    // then
    verify(mockStep).startup(mockBrokerStartupContext);
  }

  @Test
  void shouldCallShutdownOnDelegate() {
    // when
    sut.shutdown(mockBrokerStartupContext);

    // then
    verify(mockStep).shutdown(mockBrokerStartupContext);
  }

  @Test
  void shouldUpdateStartStepDuration() {
    // when
    sut.startup(mockBrokerStartupContext);
    startupFuture.complete(mockBrokerStartupContext);

    // then
    verify(mockBrokerStepMetrics).createStartupTimer(eq(DELEGATE_STEP_NAME));
    verify(mockStartupTimer).close();
    verifyNoMoreInteractions(mockCloseTimer, mockStartupTimer, mockBrokerStepMetrics);
  }

  @Test
  void shouldUpdateShutdownStepDuration() {
    // when
    sut.shutdown(mockBrokerStartupContext);
    shutdownFuture.complete(mockBrokerStartupContext);

    // then
    verify(mockBrokerStepMetrics).createCloseTimer(eq(DELEGATE_STEP_NAME));
    verify(mockCloseTimer).close();
    verifyNoMoreInteractions(mockCloseTimer, mockStartupTimer, mockBrokerStepMetrics);
  }

  @Test
  void shouldReturnNameOfDelegate() {
    // when
    final var actual = sut.getName();

    // then
    assertThat(actual).isEqualTo(DELEGATE_STEP_NAME);
  }
}
