/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.system.monitoring.BrokerStepMetrics;
import io.camunda.zeebe.broker.system.monitoring.BrokerStepMetricsDoc;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BrokerStepMetricDecoratorTest {

  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final String DELEGATE_STEP_NAME = "delegate step";

  private final MockClock clock = new MockClock();
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
  private final BrokerStepMetrics brokerStepMetrics = spy(new BrokerStepMetrics(registry));

  private BrokerStartupContext mockBrokerStartupContext;
  private StartupStep<BrokerStartupContext> mockStep;
  private BrokerStepMetricDecorator sut;
  private ActorFuture<BrokerStartupContext> startupFuture;
  private ActorFuture<BrokerStartupContext> shutdownFuture;

  @BeforeEach
  void setUp() {

    mockBrokerStartupContext = mock(BrokerStartupContext.class);
    when(mockBrokerStartupContext.getConcurrencyControl()).thenReturn(CONCURRENCY_CONTROL);

    startupFuture = CONCURRENCY_CONTROL.createFuture();
    shutdownFuture = CONCURRENCY_CONTROL.createFuture();

    mockStep = mock(StartupStep.class);
    when(mockStep.startup(mockBrokerStartupContext)).thenReturn(startupFuture);
    when(mockStep.shutdown(mockBrokerStartupContext)).thenReturn(shutdownFuture);
    when(mockStep.getName()).thenReturn(DELEGATE_STEP_NAME);

    sut = new BrokerStepMetricDecorator(brokerStepMetrics, mockStep);
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
    sut.startup(mockBrokerStartupContext);
    clock.addSeconds(1); // fix operation duration to be exactly one second
    startupFuture.complete(mockBrokerStartupContext);

    // then
    final var gauge = registry.get(BrokerStepMetricsDoc.STARTUP.getName()).timeGauge();
    verify(brokerStepMetrics, Mockito.times(1)).createStartupTimer(any());
    verifyNoMoreInteractions(brokerStepMetrics);
    assertThat(gauge.value(TimeUnit.MILLISECONDS)).isEqualTo(TimeUnit.SECONDS.toMillis(1));
  }

  @Test
  void shouldUpdateShutdownStepDuration() {
    // when
    sut.shutdown(mockBrokerStartupContext);
    clock.addSeconds(1); // fix operation duration to 1 second
    shutdownFuture.complete(mockBrokerStartupContext);

    // then
    final var gauge = registry.get(BrokerStepMetricsDoc.CLOSE.getName()).timeGauge();
    verify(brokerStepMetrics, Mockito.times(1)).createCloseTimer(any());
    verifyNoMoreInteractions(brokerStepMetrics);
    assertThat(gauge.value(TimeUnit.MILLISECONDS)).isEqualTo(TimeUnit.SECONDS.toMillis(1));
  }

  @Test
  void shouldReturnNameOfDelegate() {
    // when
    final var actual = sut.getName();

    // then
    assertThat(actual).isEqualTo(DELEGATE_STEP_NAME);
  }
}
