/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiServiceImpl;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CommandApiServicePartitionStepTest {

  private static final int PARTITION_ID = 1;
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();

  private PartitionStartupContext mockContext;
  private ActorSchedulingService mockSchedulingService;
  private DiskSpaceUsageMonitor mockDiskSpaceUsageMonitor;

  private final CommandApiServicePartitionStep sut =
      new CommandApiServicePartitionStep(PARTITION_ID);

  @BeforeEach
  void setUp() {
    mockContext = mock(PartitionStartupContext.class);
    mockSchedulingService = mock(ActorSchedulingService.class);
    mockDiskSpaceUsageMonitor = mock(DiskSpaceUsageMonitor.class);

    final var partitionMetadata = mock(PartitionMetadata.class);
    when(partitionMetadata.id())
        .thenReturn(PartitionId.from(Protocol.DEFAULT_PARTITION_GROUP_NAME, PARTITION_ID));

    when(mockContext.concurrencyControl()).thenReturn(CONCURRENCY_CONTROL);
    when(mockContext.schedulingService()).thenReturn(mockSchedulingService);
    when(mockContext.diskSpaceUsageMonitor()).thenReturn(mockDiskSpaceUsageMonitor);
    when(mockContext.gatewayBrokerTransport()).thenReturn(mock(AtomixServerTransport.class));
    when(mockContext.brokerConfig()).thenReturn(new BrokerCfg());
    when(mockContext.partitionMetadata()).thenReturn(partitionMetadata);

    when(mockSchedulingService.submitActor(any()))
        .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));
  }

  @Test
  void shouldHaveDescriptiveName() {
    assertThat(sut.getName()).isEqualTo("Partition 1 - Command API Service");
  }

  @Nested
  class StartupBehavior {

    @Test
    void shouldCompleteFuture() {
      // when
      final var result = sut.startup(mockContext);

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
    }

    @Test
    void shouldSubmitCommandApiServiceActor() {
      // when
      final var result = sut.startup(mockContext);
      await().until(result::isDone);

      // then
      verify(mockSchedulingService).submitActor(any(CommandApiServiceImpl.class));
    }

    @Test
    void shouldRegisterCommandApiServiceAsDiskSpaceListener() {
      // when
      final var result = sut.startup(mockContext);
      await().until(result::isDone);

      // then
      verify(mockDiskSpaceUsageMonitor).addDiskUsageListener(any(CommandApiServiceImpl.class));
    }

    @Test
    void shouldStoreCommandApiServiceOnContext() {
      // when
      final var result = sut.startup(mockContext);
      await().until(result::isDone);

      // then
      verify(mockContext).commandApiService(any(CommandApiServiceImpl.class));
    }

    @Test
    void shouldCloseActorAndCompleteExceptionallyWhenSubmitActorFails() {
      // given
      final var submitError = new RuntimeException("scheduler rejected");
      when(mockSchedulingService.submitActor(any()))
          .thenReturn(CONCURRENCY_CONTROL.failedFuture(submitError));

      // when
      final var result = sut.startup(mockContext);
      await().until(result::isDone);

      // then — future completes exceptionally and the actor is closed to release sub-actors
      assertThat(result).failsWithin(TIMEOUT).withThrowableOfType(Exception.class);
      verify(mockSchedulingService).submitActor(any(CommandApiServiceImpl.class));
    }

    @Test
    void shouldCloseActorAndCompleteExceptionallyWhenDiskSpaceListenerRegistrationFails() {
      // given
      final var listenerError = new RuntimeException("monitor unavailable");
      doThrow(listenerError).when(mockDiskSpaceUsageMonitor).addDiskUsageListener(any());

      // when
      final var result = sut.startup(mockContext);
      await().until(result::isDone);

      // then — future completes exceptionally and the actor is closed
      assertThat(result).failsWithin(TIMEOUT).withThrowableOfType(Exception.class);
    }
  }

  @Nested
  class ShutdownBehavior {

    private CommandApiServiceImpl mockCommandApiService;

    @BeforeEach
    void setUp() {
      mockCommandApiService = mock(CommandApiServiceImpl.class);
      when(mockCommandApiService.closeAsync())
          .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));
      when(mockContext.commandApiService()).thenReturn(mockCommandApiService);
    }

    @Test
    void shouldCompleteFuture() {
      // when
      final var result = sut.shutdown(mockContext);

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
    }

    @Test
    void shouldRemoveDiskSpaceListener() {
      // when
      final var result = sut.shutdown(mockContext);
      await().until(result::isDone);

      // then
      verify(mockDiskSpaceUsageMonitor).removeDiskUsageListener(mockCommandApiService);
    }

    @Test
    void shouldCloseCommandApiService() {
      // when
      final var result = sut.shutdown(mockContext);
      await().until(result::isDone);

      // then
      verify(mockCommandApiService).closeAsync();
    }

    @Test
    void shouldClearCommandApiServiceFromContext() {
      // when
      final var result = sut.shutdown(mockContext);
      await().until(result::isDone);

      // then
      verify(mockContext).commandApiService(null);
    }

    @Test
    void shouldCompleteImmediatelyWhenCommandApiServiceIsNull() {
      // given
      when(mockContext.commandApiService()).thenReturn(null);

      // when
      final var result = sut.shutdown(mockContext);

      // then
      assertThat(result).succeedsWithin(TIMEOUT);
      verify(mockCommandApiService, never()).closeAsync();
    }
  }
}
