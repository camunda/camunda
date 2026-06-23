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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.zeebe.broker.jobstream.JobStreamService;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.partitioning.RecoveryPartitionManager;
import io.camunda.zeebe.broker.partitioning.startup.ZeebePartitionFactory;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.management.BrokerAdminServiceImpl;
import io.camunda.zeebe.broker.transport.adminapi.AdminApiRequestHandler;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PartitionManagerStepTest {
  public static final Duration TEST_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final BrokerCfg TEST_BROKER_CONFIG = new BrokerCfg();
  private static final Duration TIME_OUT = Duration.ofSeconds(10);
  private static final String PHYSICAL_TENANT_ID = "custom";

  static {
    final var networkCfg = TEST_BROKER_CONFIG.getGateway().getNetwork();
    networkCfg.setHost("localhost");
  }

  private final Logger log = LoggerFactory.getLogger(PartitionManagerStepTest.class);
  private final PartitionManagerStep sut = new PartitionManagerStep(PHYSICAL_TENANT_ID);
  private MockBrokerStartupContext testBrokerStartupContext;

  @Test
  void shouldHaveDescriptiveName() {
    // when
    final var actual = sut.getName();

    // then
    assertThat(actual).isEqualTo("Partition Manager [custom]");
  }

  @Nested
  class StartupBehavior {

    private ActorFuture<BrokerStartupContext> startupFuture;
    private ActorScheduler actorScheduler;
    private ClusterConfiguration mockClusterConfiguration;

    @BeforeEach
    void setUp() {
      actorScheduler = ActorScheduler.newActorScheduler().build();
      actorScheduler.start();
      startupFuture = CONCURRENCY_CONTROL.createFuture();

      testBrokerStartupContext = new MockBrokerStartupContext();
      testBrokerStartupContext.setBrokerInfo(new BrokerInfo());
      testBrokerStartupContext.setBrokerConfiguration(TEST_BROKER_CONFIG);
      testBrokerStartupContext.setActorSchedulingService(actorScheduler);
      testBrokerStartupContext.setShutdownTimeout(TEST_SHUTDOWN_TIMEOUT);
      testBrokerStartupContext.setConcurrencyControl(CONCURRENCY_CONTROL);
      testBrokerStartupContext.setAdminApiService(mock(AdminApiRequestHandler.class));
      testBrokerStartupContext.setBrokerAdminService(mock(BrokerAdminServiceImpl.class));
      testBrokerStartupContext.setJobStreamService(mock(JobStreamService.class));
      final ClusterConfigurationService clusterConfigurationService =
          mock(ClusterConfigurationService.class);
      when(clusterConfigurationService.getPartitionDistribution())
          .thenReturn(PartitionDistribution.NO_PARTITIONS);
      mockClusterConfiguration = mock(ClusterConfiguration.class);
      when(clusterConfigurationService.getInitialClusterConfiguration())
          .thenReturn(mockClusterConfiguration);
      when(mockClusterConfiguration.getMember(any())).thenReturn(MemberState.uninitialized());

      testBrokerStartupContext.setClusterConfigurationService(clusterConfigurationService);

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
      final var partitionManager =
          testBrokerStartupContext.getPartitionManagers().get(PHYSICAL_TENANT_ID);
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
      final var partitionManager =
          testBrokerStartupContext.getPartitionManagers().get(PHYSICAL_TENANT_ID);
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

    @Test
    void shouldStartRecoveryPartitionManager() {
      // given
      final var memberState = MemberState.uninitialized().toRecovering();
      when(mockClusterConfiguration.getMember(any())).thenReturn(memberState);

      // when
      sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);
      startupFuture.join();

      // then
      final var partitionManager =
          testBrokerStartupContext.getPartitionManagers().get(PHYSICAL_TENANT_ID);

      assertThat(partitionManager).isInstanceOf(RecoveryPartitionManager.class);
    }

    @Test
    void shouldScopeSearchClientProxyToPartitionGroup() throws Exception {
      // given
      final var scopedProxy = mock(SearchClientsProxy.class);
      final var searchClientsProxy = mock(SearchClientsProxy.class);
      when(searchClientsProxy.withPhysicalTenant(PHYSICAL_TENANT_ID)).thenReturn(scopedProxy);
      testBrokerStartupContext.setSearchClientsProxy(searchClientsProxy);

      // when
      sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);
      assertThat(startupFuture).succeedsWithin(TIME_OUT);

      // then — the scoped proxy (not the original unscoped one) is wired into ZeebePartitionFactory
      final var partitionManager =
          (PartitionManagerImpl)
              testBrokerStartupContext.getPartitionManagers().get(PHYSICAL_TENANT_ID);
      final var factoryField = PartitionManagerImpl.class.getDeclaredField("zeebePartitionFactory");
      factoryField.setAccessible(true);
      final var zeebeFactory = factoryField.get(partitionManager);

      final var proxyField = ZeebePartitionFactory.class.getDeclaredField("searchClientsProxy");
      proxyField.setAccessible(true);

      assertThat(proxyField.get(zeebeFactory)).isSameAs(scopedProxy);
    }

    @Test
    void shouldNotFailWhenSearchClientProxyIsNull() {
      // given
      testBrokerStartupContext.setSearchClientsProxy(null);

      // when
      sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);

      // then
      assertThat(startupFuture).succeedsWithin(TIME_OUT);
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

      testBrokerStartupContext = new MockBrokerStartupContext();
      testBrokerStartupContext.setBrokerInfo(new BrokerInfo());
      testBrokerStartupContext.setBrokerConfiguration(TEST_BROKER_CONFIG);
      testBrokerStartupContext.setActorSchedulingService(mock(ActorScheduler.class));
      testBrokerStartupContext.setShutdownTimeout(TEST_SHUTDOWN_TIMEOUT);
      testBrokerStartupContext.setConcurrencyControl(CONCURRENCY_CONTROL);

      testBrokerStartupContext.setClusterConfigurationService(
          mock(ClusterConfigurationService.class));
      // Startup wires the step's topology manager. The mock actor scheduler makes the partition
      // manager submission fail, so the step stores no manager; we inject the mock ourselves.
      final ActorFuture<BrokerStartupContext> startupFuture = CONCURRENCY_CONTROL.createFuture();
      sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);
      testBrokerStartupContext.addPartitionManager(PHYSICAL_TENANT_ID, mockPartitionManager);
      shutdownFuture = CONCURRENCY_CONTROL.createFuture();
    }

    @Test
    void shouldStopAndUninstallEmbeddedGateway() {
      // when
      sut.shutdownInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, shutdownFuture);
      await().until(shutdownFuture::isDone);

      // then
      verify(mockPartitionManager).stop();
      final var partitionManager =
          testBrokerStartupContext.getPartitionManagers().get(PHYSICAL_TENANT_ID);
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

    @Test
    void shouldCompleteFutureOnRecoveryPartitionManager() {
      // given
      final var recoveryPartitionManager = mock(RecoveryPartitionManager.class);
      when(recoveryPartitionManager.stop()).thenReturn(CompletableActorFuture.completed(null));
      testBrokerStartupContext.addPartitionManager(PHYSICAL_TENANT_ID, recoveryPartitionManager);

      // when
      sut.shutdownInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, shutdownFuture);

      // then
      assertThat(shutdownFuture).succeedsWithin(TIME_OUT);
      assertThat(shutdownFuture.join()).isNotNull();
      assertThat(testBrokerStartupContext.getPartitionManagers()).isEmpty();
    }
  }
}
