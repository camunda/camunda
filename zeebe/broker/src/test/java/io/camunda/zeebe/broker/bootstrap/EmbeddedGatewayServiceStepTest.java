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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class EmbeddedGatewayServiceStepTest {
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final BrokerCfg TEST_BROKER_CONFIG = new BrokerCfg();
  private static final Duration TIME_OUT = Duration.ofSeconds(10);
  private static final Duration TEST_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

  static {
    final var networkCfg = TEST_BROKER_CONFIG.getGateway().getNetwork();
    networkCfg.setHost("localhost");
  }

  private final EmbeddedGatewayServiceStep sut = new EmbeddedGatewayServiceStep();
  private BrokerStartupContextImpl testBrokerStartupContext;

  @Test
  void shouldHaveDescriptiveName() {
    // when
    final var actual = sut.getName();

    // then
    assertThat(actual).isSameAs("Embedded Gateway");
  }

  @Nested
  class StartupBehavior {

    private ActorFuture<BrokerStartupContext> startupFuture;
    private ActorScheduler actorScheduler;
    private Actor actor;

    @BeforeEach
    void setUp() {
      actorScheduler = ActorScheduler.newActorScheduler().build();
      actorScheduler.start();
      startupFuture = CONCURRENCY_CONTROL.createFuture();

      testBrokerStartupContext =
          new BrokerStartupContextImpl(
              mock(BrokerInfo.class),
              TEST_BROKER_CONFIG,
              mock(SpringBrokerBridge.class),
              actorScheduler,
              mock(BrokerHealthCheckService.class),
              mock(ExporterRepository.class),
              mock(ClusterServicesImpl.class, RETURNS_DEEP_STUBS),
              mock(BrokerClient.class),
              Collections.emptyList(),
              TEST_SHUTDOWN_TIMEOUT,
              new SecurityConfiguration(),
              mock(UserServices.class),
              mock(PasswordEncoder.class),
              null);

      final var port = SocketUtil.getNextAddress().getPort();
      final var commandApiCfg = TEST_BROKER_CONFIG.getGateway().getNetwork();
      commandApiCfg.setPort(port);

      actor = Actor.newActor().build();
      actorScheduler.submitActor(actor);
    }

    @AfterEach
    void tearDown() {
      final var embeddedGatewayService = testBrokerStartupContext.getEmbeddedGatewayService();
      if (embeddedGatewayService != null) {
        embeddedGatewayService.close();
      }
      actorScheduler.stop();
    }

    @Test
    void shouldCompleteFuture() {
      // when
      actor.run(
          () -> {
            sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);
          });

      // then
      assertThat(startupFuture).succeedsWithin(TIME_OUT);
      assertThat(startupFuture.join()).isNotNull();
    }

    @Test
    void shouldStartAndInstallEmbeddedGatewayService() {
      // when
      actor.run(
          () -> {
            sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);
          });
      await().until(startupFuture::isDone);

      // then
      final var embeddedGatewayService = testBrokerStartupContext.getEmbeddedGatewayService();
      assertThat(embeddedGatewayService).isNotNull();
    }
  }

  @Nested
  class ShutdownBehavior {

    private EmbeddedGatewayService mockEmbeddedGatewayService;

    private ActorFuture<BrokerStartupContext> shutdownFuture;

    @BeforeEach
    void setUp() {
      mockEmbeddedGatewayService = mock(EmbeddedGatewayService.class);

      testBrokerStartupContext =
          new BrokerStartupContextImpl(
              mock(BrokerInfo.class),
              TEST_BROKER_CONFIG,
              mock(SpringBrokerBridge.class),
              mock(ActorScheduler.class),
              mock(BrokerHealthCheckService.class),
              mock(ExporterRepository.class),
              mock(ClusterServicesImpl.class, RETURNS_DEEP_STUBS),
              mock(BrokerClient.class),
              Collections.emptyList(),
              TEST_SHUTDOWN_TIMEOUT,
              new SecurityConfiguration(),
              mock(UserServices.class),
              mock(PasswordEncoder.class),
              null);

      testBrokerStartupContext.setEmbeddedGatewayService(mockEmbeddedGatewayService);
      shutdownFuture = CONCURRENCY_CONTROL.createFuture();
    }

    @Test
    void shouldStopAndUninstallEmbeddedGateway() {
      // when
      sut.shutdownInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, shutdownFuture);
      await().until(shutdownFuture::isDone);

      // then
      verify(mockEmbeddedGatewayService).close();
      final var embeddedGatewayService = testBrokerStartupContext.getEmbeddedGatewayService();
      assertThat(embeddedGatewayService).isNull();
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
