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
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.jobstream.JobStreamService;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.management.BrokerAdminServiceImpl;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.transport.adminapi.AdminApiRequestHandler;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class PartitionManagerStepTest {
  public static final Duration TEST_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final BrokerCfg TEST_BROKER_CONFIG = new BrokerCfg();
  private static final Duration TIME_OUT = Duration.ofSeconds(10);

  static {
    final var networkCfg = TEST_BROKER_CONFIG.getGateway().getNetwork();
    networkCfg.setHost("localhost");
  }

  private final Logger log = LoggerFactory.getLogger(PartitionManagerStepTest.class);
  private final PartitionManagerStep sut = new PartitionManagerStep();
  private BrokerStartupContextImpl testBrokerStartupContext;

  @Test
  void shouldHaveDescriptiveName() {
    // when
    final var actual = sut.getName();

    // then
    assertThat(actual).isSameAs("Partition Manager");
  }

  @Nested
  class StartupBehavior {

    private ActorFuture<BrokerStartupContext> startupFuture;
    private ActorScheduler actorScheduler;

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
              mock(JwtDecoder.class));
      testBrokerStartupContext.setConcurrencyControl(CONCURRENCY_CONTROL);
      testBrokerStartupContext.setAdminApiService(mock(AdminApiRequestHandler.class));
      testBrokerStartupContext.setBrokerAdminService(mock(BrokerAdminServiceImpl.class));
      testBrokerStartupContext.setJobStreamService(mock(JobStreamService.class));
      final ClusterConfigurationService mockClusterTopology =
          mock(ClusterConfigurationService.class);
      when(mockClusterTopology.getPartitionDistribution())
          .thenReturn(PartitionDistribution.NO_PARTITIONS);
      testBrokerStartupContext.setClusterConfigurationService(mockClusterTopology);

      final var memberConfig = new MemberConfig();
      final var member = new Member(memberConfig);

      final var mockMembershipService = mock(ClusterMembershipService.class);
      when(mockMembershipService.getLocalMember()).thenReturn(member);

      when(testBrokerStartupContext.getClusterServices().getMembershipService())
          .thenReturn(mockMembershipService);

      final var port = SocketUtil.getNextAddress().getPort();
      final var commandApiCfg = TEST_BROKER_CONFIG.getGateway().getNetwork();
      commandApiCfg.setPort(port);
    }

    @AfterEach
    void tearDown() {
      final var partitionManager = testBrokerStartupContext.getPartitionManager();
      if (partitionManager != null) {
        partitionManager.stop().join();
      }
      try {
        actorScheduler.stop();
      } catch (final IllegalStateException e) {
        log.debug("ActorScheduler was already stopped.");
      }
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
    void shouldStartAndInstallEmbeddedGatewayService() {
      // when
      sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);
      await().until(startupFuture::isDone);

      // then
      final var partitionManager = testBrokerStartupContext.getPartitionManager();
      assertThat(partitionManager).isNotNull();
    }

    @Test
    void shouldHandleSyncFailOfStart() throws Exception {
      // given
      actorScheduler.close();

      // when
      sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);

      // then
      assertThat(startupFuture)
          .failsWithin(Duration.ZERO)
          .withThrowableOfType(ExecutionException.class)
          .withRootCauseInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  class ShutdownBehavior {

    private PartitionManagerImpl mockPartitionManager;

    private ActorFuture<BrokerStartupContext> shutdownFuture;

    @BeforeEach
    void setUp() {
      mockPartitionManager = mock(PartitionManagerImpl.class);
      when(mockPartitionManager.stop()).thenReturn(CompletableActorFuture.completed(null));

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
              mock(JwtDecoder.class));

      testBrokerStartupContext.setPartitionManager(mockPartitionManager);
      final ClusterConfigurationService mockClusterTopology =
          mock(ClusterConfigurationService.class);
      testBrokerStartupContext.setClusterConfigurationService(mockClusterTopology);
      shutdownFuture = CONCURRENCY_CONTROL.createFuture();
    }

    @Test
    void shouldStopAndUninstallEmbeddedGateway() {
      // when
      sut.shutdownInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, shutdownFuture);
      await().until(shutdownFuture::isDone);

      // then
      verify(mockPartitionManager).stop();
      final var partitionManager = testBrokerStartupContext.getPartitionManager();
      assertThat(partitionManager).isNull();
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
