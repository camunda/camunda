/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.time.Duration;
import java.util.Random;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RequestIdGeneratorStepTest {
  private static final Duration TEST_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
  private static final BrokerCfg TEST_BROKER_CONFIG = new BrokerCfg();
  private static final Duration TIME_OUT = Duration.ofSeconds(10);
  private MockBrokerStartupContext testBrokerStartupContext;
  private final BrokerInfo mockBrokerInfo = mock(BrokerInfo.class);
  private final TestConcurrencyControl spyConcurrencyControl = spy(new TestConcurrencyControl());

  private final RequestIdGeneratorStep sut = new RequestIdGeneratorStep();

  @BeforeEach
  void setUp() {

    testBrokerStartupContext = new MockBrokerStartupContext();
    testBrokerStartupContext.setBrokerInfo(mockBrokerInfo);
    testBrokerStartupContext.setBrokerConfiguration(TEST_BROKER_CONFIG);
    testBrokerStartupContext.setShutdownTimeout(TEST_SHUTDOWN_TIMEOUT);
    testBrokerStartupContext.setConcurrencyControl(spyConcurrencyControl);
  }

  @Test
  void shouldHaveDescriptiveName() {
    // when
    final var actual = sut.getName();

    // then
    assertThat(actual).isSameAs("Request Id Generator");
  }

  @Nested
  class StartupBehavior {

    private ActorFuture<BrokerStartupContext> startupFuture;

    @BeforeEach
    void setUp() {
      startupFuture = spyConcurrencyControl.createFuture();
      when(spyConcurrencyControl.<BrokerStartupContext>createFuture()).thenReturn(startupFuture);
    }

    @Test
    void shouldCompleteFuture() {
      // when
      sut.startup(testBrokerStartupContext);

      // then
      assertThat(startupFuture).succeedsWithin(TIME_OUT);
      assertThat(startupFuture.join()).isNotNull();
    }

    @Test
    void shouldStartAndInstallRequestIdGenerator() {
      // when
      final int randomNodeId = new Random().nextInt(10);
      when(mockBrokerInfo.getNodeId()).thenReturn(randomNodeId);
      sut.startup(testBrokerStartupContext);
      await().until(startupFuture::isDone);

      // then
      final var requestIdGenerator = testBrokerStartupContext.getRequestIdGenerator();

      assertThat(requestIdGenerator).isNotNull();
      assertThat(requestIdGenerator.nodeId()).isEqualTo(randomNodeId);
    }
  }

  @Nested
  class ShutdownBehavior {

    private ActorFuture<BrokerStartupContext> shutdownFuture;

    @BeforeEach
    void setUp() {
      final var mockIdGenerator = mock(SnowflakeIdGenerator.class);
      testBrokerStartupContext.setRequestIdGenerator(mockIdGenerator);
      shutdownFuture = spyConcurrencyControl.createFuture();
      when(spyConcurrencyControl.<BrokerStartupContext>createFuture()).thenReturn(shutdownFuture);
    }

    @Test
    void shouldStopAndUninstallRequestIdGenerator() {
      // when
      sut.shutdown(testBrokerStartupContext);
      await().until(shutdownFuture::isDone);

      // then
      final var requestIdGenerator = testBrokerStartupContext.getRequestIdGenerator();
      assertThat(requestIdGenerator).isNull();
    }

    @Test
    void shouldCompleteFuture() {
      // when
      sut.shutdown(testBrokerStartupContext);

      // then
      assertThat(shutdownFuture).succeedsWithin(TIME_OUT);
      assertThat(shutdownFuture.join()).isNotNull();
    }
  }
}
