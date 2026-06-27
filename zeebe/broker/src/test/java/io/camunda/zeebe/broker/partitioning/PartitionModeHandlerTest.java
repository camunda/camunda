/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class PartitionModeHandlerTest {

  private static final String GROUP = PartitionManagerImpl.DEFAULT_GROUP_NAME;
  private static final String NON_DEFAULT_GROUP = "tenant-2";
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();

  private BrokerStartupContext brokerStartupContext;
  private ClusterConfigurationService clusterConfigurationService;
  private TopologyManagerImpl topologyManager;
  private Map<String, PartitionManager> partitionManagers;
  private PartitionManagerImpl normalManager;
  private RecoveryPartitionManager recoveryManager;
  private MockedStatic<PartitionManager> partitionManagerStatic;
  private PartitionModeHandler handler;

  @BeforeEach
  void setUp() {
    clusterConfigurationService = mock(ClusterConfigurationService.class);
    topologyManager = mock(TopologyManagerImpl.class);
    partitionManagers = new HashMap<>();

    normalManager = mock(PartitionManagerImpl.class);
    when(normalManager.start()).thenReturn(CompletableActorFuture.completed(null));
    when(normalManager.stop()).thenReturn(CompletableActorFuture.completed(null));

    recoveryManager = mock(RecoveryPartitionManager.class);
    when(recoveryManager.start()).thenReturn(CompletableActorFuture.completed(null));
    when(recoveryManager.stop()).thenReturn(CompletableActorFuture.completed(null));

    brokerStartupContext = mock(BrokerStartupContext.class);
    when(brokerStartupContext.getConcurrencyControl()).thenReturn(CONCURRENCY_CONTROL);
    when(brokerStartupContext.getClusterConfigurationService())
        .thenReturn(clusterConfigurationService);
    when(brokerStartupContext.getPartitionManagers()).thenReturn(partitionManagers);

    partitionManagerStatic = mockStatic(PartitionManager.class);
    partitionManagerStatic
        .when(() -> PartitionManager.isDefaultPhysicalTenant(GROUP))
        .thenReturn(true);
    partitionManagerStatic
        .when(() -> PartitionManager.createPartitionManager(any(), any(), any()))
        .thenReturn(normalManager);
    partitionManagerStatic
        .when(() -> PartitionManager.createRecoveryPartitionManager(any(), any(), any()))
        .thenReturn(recoveryManager);

    handler = new PartitionModeHandler(brokerStartupContext, GROUP, topologyManager);
  }

  @AfterEach
  void tearDown() {
    partitionManagerStatic.close();
  }

  private void givenCurrentManager(final String tenantId, final PartitionManager current) {
    partitionManagers.put(tenantId, current);
  }

  @Nested
  class EnterRecovery {

    @BeforeEach
    void setUp() {
      givenCurrentManager(GROUP, normalManager);
    }

    @Test
    void shouldCompleteSuccessfully() {
      // when
      final ActorFuture<Void> result = handler.enterRecovery();

      // then
      assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldStopCurrentManagerAndStartRecoveryManager() {
      // when
      handler.enterRecovery();

      // then
      verify(normalManager).stop();
      verify(recoveryManager).start();
    }

    @Test
    void shouldPublishRecoveryManagerOnContext() {
      // when
      handler.enterRecovery();

      // then
      verify(brokerStartupContext).addPartitionManager(GROUP, recoveryManager);
    }

    @Test
    void shouldNotRegisterPartitionExecutors() {
      // when — partition change executors are registered by the manager itself on start()
      handler.enterRecovery();

      // then
      verify(clusterConfigurationService, never()).registerPartitionChangeExecutors(any(), any());
    }

    @Test
    void shouldFailWhenRecoveryManagerStartFails() {
      // given
      when(recoveryManager.start())
          .thenReturn(
              CompletableActorFuture.completedExceptionally(new RuntimeException("start failed")));

      // when
      final ActorFuture<Void> result = handler.enterRecovery();

      // then
      assertThat(result.isCompletedExceptionally()).isTrue();
    }

    @Test
    void shouldNotPublishOnFailure() {
      // given
      when(recoveryManager.start())
          .thenReturn(
              CompletableActorFuture.completedExceptionally(new RuntimeException("start failed")));

      // when
      handler.enterRecovery();

      // then
      verify(brokerStartupContext, never()).addPartitionManager(GROUP, recoveryManager);
    }

    @Test
    void shouldNoOpWhenAlreadyInRecovery() {
      // given
      givenCurrentManager(GROUP, recoveryManager);

      // when
      handler.enterRecovery();

      // then
      verify(recoveryManager, never()).stop();
      verify(recoveryManager, never()).start();
    }

    @Test
    void shouldFailWhenNoManagerPresent() {
      // given
      partitionManagers.remove(GROUP);

      // when
      final ActorFuture<Void> result = handler.enterRecovery();

      // then
      assertThat(result.isCompletedExceptionally()).isTrue();
    }
  }

  @Nested
  class ExitRecovery {

    @BeforeEach
    void setUp() {
      givenCurrentManager(GROUP, recoveryManager);
    }

    @Test
    void shouldCompleteSuccessfully() {
      // when
      final ActorFuture<Void> result = handler.exitRecovery();

      // then
      assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldStopRecoveryManagerAndStartNormalManager() {
      // when
      handler.exitRecovery();

      // then
      verify(recoveryManager).stop();
      verify(normalManager).start();
    }

    @Test
    void shouldNotRegisterPartitionExecutors() {
      // when — partition change executors are registered by the manager itself on start()
      handler.exitRecovery();

      // then
      verify(clusterConfigurationService, never()).registerPartitionChangeExecutors(any(), any());
    }

    @Test
    void shouldNoOpWhenAlreadyInNormalMode() {
      // given
      givenCurrentManager(GROUP, normalManager);

      // when
      handler.exitRecovery();

      // then
      verify(normalManager, never()).stop();
      verify(normalManager, never()).start();
    }
  }

  @Nested
  class NonDefaultTenant {

    private PartitionModeHandler nonDefaultHandler;

    @BeforeEach
    void setUp() {
      givenCurrentManager(NON_DEFAULT_GROUP, normalManager);
      nonDefaultHandler =
          new PartitionModeHandler(brokerStartupContext, NON_DEFAULT_GROUP, topologyManager);
    }

    @Test
    void shouldTransitionWithoutRegisteringExecutors() {
      // when
      final ActorFuture<Void> result = nonDefaultHandler.enterRecovery();

      // then
      assertThat(result.isCompletedExceptionally()).isFalse();
      verify(recoveryManager).start();
      verify(brokerStartupContext).addPartitionManager(NON_DEFAULT_GROUP, recoveryManager);
      // only the default tenant participates in cluster configuration changes
      verify(clusterConfigurationService, never()).registerPartitionChangeExecutors(any(), any());
    }

    @Test
    void shouldNotRegisterModeExecutorOnRegister() {
      // when
      nonDefaultHandler.register();

      // then
      verify(clusterConfigurationService, never()).registerModeChangeExecutor(any());
    }
  }

  @Nested
  class Register {

    @Test
    void shouldRegisterModeExecutor() {
      // when
      handler.register();

      // then
      verify(clusterConfigurationService).registerModeChangeExecutor(handler);
    }

    @Test
    void shouldNotRegisterPartitionExecutors() {
      // when — partition change executors are owned by the partition manager
      handler.register();

      // then
      verify(clusterConfigurationService, never()).registerPartitionChangeExecutors(any(), any());
    }
  }

  @Nested
  class Close {

    @Test
    void shouldRemoveModeExecutorOnClose() {
      // when
      handler.closeAsync();

      // then
      verify(clusterConfigurationService).removeModeChangeExecutor();
    }

    @Test
    void shouldNotRemovePartitionExecutorOnClose() {
      // when — partition change executors are removed by the partition manager on stop()
      handler.closeAsync();

      // then
      verify(clusterConfigurationService, never()).removePartitionChangeExecutor();
    }
  }
}
