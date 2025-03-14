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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import java.time.Duration;
import java.util.Collections;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class GatewayBrokerTransportStepTest {
  private static final Duration TEST_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final BrokerCfg TEST_BROKER_CONFIG = new BrokerCfg();
  private static final Duration TIME_OUT = Duration.ofSeconds(10);

  private final ActorScheduler mockActorSchedulingService = mock(ActorScheduler.class);

  private BrokerStartupContextImpl testBrokerStartupContext;
  private final BrokerInfo mockBrokerInfo = mock(BrokerInfo.class);

  private final GatewayBrokerTransportStep sut = new GatewayBrokerTransportStep();

  @BeforeEach
  void setUp() {
    when(mockActorSchedulingService.submitActor(any()))
        .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

    testBrokerStartupContext =
        new BrokerStartupContextImpl(
            mockBrokerInfo,
            TEST_BROKER_CONFIG,
            mock(SpringBrokerBridge.class),
            mockActorSchedulingService,
            mock(BrokerHealthCheckService.class),
            mock(ExporterRepository.class),
            mock(ClusterServicesImpl.class, RETURNS_DEEP_STUBS),
            mock(BrokerClient.class),
            Collections.emptyList(),
            TEST_SHUTDOWN_TIMEOUT,
            new SecurityConfiguration(),
            mock(UserServices.class),
            mock(PasswordEncoder.class),
            mock(JwtDecoder.class));
    testBrokerStartupContext.setConcurrencyControl(CONCURRENCY_CONTROL);
  }

  @Test
  void shouldHaveDescriptiveName() {
    // when
    final var actual = sut.getName();

    // then
    assertThat(actual).isSameAs("Broker Transport");
  }

  @Nested
  class StartupBehavior {

    private ActorFuture<BrokerStartupContext> startupFuture;

    @BeforeEach
    void setUp() {
      startupFuture = CONCURRENCY_CONTROL.createFuture();
    }

    @Test
    void shouldCompleteFuture() {
      // when
      sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);

      // then
      assertThat(startupFuture).succeedsWithin(TIME_OUT);
      assertThat(startupFuture.join()).isNotNull();
    }

    @Test
    void shouldStartAndInstallTransport() {
      // when
      final int randomNodeId = new Random().nextInt(10);
      when(mockBrokerInfo.getNodeId()).thenReturn(randomNodeId);
      sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);
      await().until(startupFuture::isDone);

      // then
      final var transport = testBrokerStartupContext.getGatewayBrokerTransport();

      assertThat(transport).isNotNull();
      verify(mockActorSchedulingService).submitActor(transport);
    }
  }

  @Nested
  class ShutdownBehavior {

    private AtomixServerTransport mockTransport;

    private ActorFuture<BrokerStartupContext> shutdownFuture;

    @BeforeEach
    void setUp() {

      mockTransport = mock(AtomixServerTransport.class);
      when(mockTransport.closeAsync()).thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

      testBrokerStartupContext.setGatewayBrokerTransport(mockTransport);

      shutdownFuture = CONCURRENCY_CONTROL.createFuture();
    }

    @Test
    void shouldStopAndUninstallServerTransport() {
      // when
      sut.shutdownInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, shutdownFuture);
      await().until(shutdownFuture::isDone);

      // then
      verify(mockTransport).closeAsync();
      final var serverTransport = testBrokerStartupContext.getGatewayBrokerTransport();
      assertThat(serverTransport).isNull();
    }

    @Test
    void shouldCompleteFuture() {
      // when
      sut.shutdownInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, shutdownFuture);

      // then
      assertThat(shutdownFuture).succeedsWithin(TIME_OUT);
      assertThat(shutdownFuture.join()).isNotNull();
    }
  }
}
