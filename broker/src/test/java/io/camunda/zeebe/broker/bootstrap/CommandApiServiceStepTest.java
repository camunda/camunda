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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiServiceImpl;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CommandApiServiceStepTest {
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final BrokerCfg TEST_BROKER_CONFIG = new BrokerCfg();
  private static final BrokerInfo TEST_BROKER_INFO = new BrokerInfo(0, "localhost");
  private static final Duration TIME_OUT = Duration.ofSeconds(10);

  static {
    final var commandApiCfg = TEST_BROKER_CONFIG.getNetwork().getCommandApi();
    commandApiCfg.setHost("localhost");
    commandApiCfg.setAdvertisedHost("localhost");
  }

  private final ActorScheduler mockActorSchedulingService = mock(ActorScheduler.class);

  private BrokerStartupContextImpl testBrokerStartupContext;

  private final CommandApiServiceStep sut = new CommandApiServiceStep();

  @BeforeEach
  void setUp() {
    when(mockActorSchedulingService.submitActor(any()))
        .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

    testBrokerStartupContext =
        new BrokerStartupContextImpl(
            TEST_BROKER_INFO,
            TEST_BROKER_CONFIG,
            mock(SpringBrokerBridge.class),
            mockActorSchedulingService,
            mock(BrokerHealthCheckService.class),
            mock(ExporterRepository.class),
            Collections.emptyList());
    testBrokerStartupContext.setConcurrencyControl(CONCURRENCY_CONTROL);
  }

  @Test
  void shouldHaveDescriptiveName() {
    // when
    final var actual = sut.getName();

    // then
    assertThat(actual).isSameAs("Command API");
  }

  @Nested
  class StartupBehavior {

    private ActorFuture<BrokerStartupContext> startupFuture;

    @BeforeEach
    void setUp() {
      startupFuture = CONCURRENCY_CONTROL.createFuture();

      final var port = SocketUtil.getNextAddress().getPort();
      final var commandApiCfg = TEST_BROKER_CONFIG.getNetwork().getCommandApi();
      commandApiCfg.setPort(port);
      commandApiCfg.setAdvertisedPort(port);
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
    void shouldStartAndInstallServerTransport() {
      // when
      sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);
      await().until(startupFuture::isDone);

      // then
      final var serverTransport = testBrokerStartupContext.getCommandApiServerTransport();

      assertThat(serverTransport).isNotNull();
      verify(mockActorSchedulingService).submitActor(serverTransport);
    }

    @Test
    void shouldStartAndInstallCommandApiService() {
      // when
      sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);
      await().until(startupFuture::isDone);

      // then
      final var commandApiService = testBrokerStartupContext.getCommandApiService();

      assertThat(commandApiService).isNotNull();
      verify(mockActorSchedulingService).submitActor(commandApiService);
    }

    @Test
    void shouldAddCommandApiServiceAsPartitionListener() {
      // when
      sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);
      await().until(startupFuture::isDone);

      // then
      final var commandApiService = testBrokerStartupContext.getCommandApiService();

      assertThat(commandApiService).isNotNull();
      assertThat(testBrokerStartupContext.getPartitionListeners()).contains(commandApiService);
    }

    @Test
    void shouldAddCommandApiServiceAsDiskSpaceUsageListener() {
      // given
      final var mockDiskSpaceUsageMonitor = mock(DiskSpaceUsageMonitor.class);
      testBrokerStartupContext.setDiskSpaceUsageMonitor(mockDiskSpaceUsageMonitor);

      // when
      sut.startupInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, startupFuture);
      await().until(startupFuture::isDone);

      // then
      final var commandApiService = testBrokerStartupContext.getCommandApiService();

      assertThat(commandApiService).isNotNull();
      verify(mockDiskSpaceUsageMonitor).addDiskUsageListener(commandApiService);
    }
  }

  @Nested
  class ShutdownBehavior {

    private CommandApiServiceImpl mockCommandApiService;
    private AtomixServerTransport mockAtomixServerTransport;

    private ActorFuture<BrokerStartupContext> shutdownFuture;

    @BeforeEach
    void setUp() {
      mockCommandApiService = mock(CommandApiServiceImpl.class);
      when(mockCommandApiService.closeAsync())
          .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

      mockAtomixServerTransport = mock(AtomixServerTransport.class);
      when(mockAtomixServerTransport.closeAsync())
          .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

      testBrokerStartupContext.setCommandApiServerTransport(mockAtomixServerTransport);
      testBrokerStartupContext.setCommandApiService(mockCommandApiService);
      testBrokerStartupContext.addPartitionListener(mockCommandApiService);
      testBrokerStartupContext.addDiskSpaceUsageListener(mockCommandApiService);

      shutdownFuture = CONCURRENCY_CONTROL.createFuture();
    }

    @Test
    void shouldRemoveCommandApiFromDiskSpaceUsageListenerList() {
      // given
      final var mockDiskSpaceUsageMonitor = mock(DiskSpaceUsageMonitor.class);
      testBrokerStartupContext.setDiskSpaceUsageMonitor(mockDiskSpaceUsageMonitor);

      // when
      sut.shutdownInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, shutdownFuture);
      await().until(shutdownFuture::isDone);

      // then
      verify(mockDiskSpaceUsageMonitor).removeDiskUsageListener(mockCommandApiService);
    }

    @Test
    void shouldRemoveCommandApiFromPartitionListenerList() {
      // when
      sut.shutdownInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, shutdownFuture);
      await().until(shutdownFuture::isDone);

      // then
      assertThat(testBrokerStartupContext.getPartitionListeners())
          .doesNotContain(mockCommandApiService);
    }

    @Test
    void shouldStopAndUninstallCommandApiService() {
      // when
      sut.shutdownInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, shutdownFuture);
      await().until(shutdownFuture::isDone);

      // then
      verify(mockCommandApiService).closeAsync();
      final var commandApiService = testBrokerStartupContext.getCommandApiService();
      assertThat(commandApiService).isNull();
    }

    @Test
    void shouldStopAndUninstallServerTransport() {
      // when
      sut.shutdownInternal(testBrokerStartupContext, CONCURRENCY_CONTROL, shutdownFuture);
      await().until(shutdownFuture::isDone);

      // then
      verify(mockAtomixServerTransport).closeAsync();
      final var serverTransport = testBrokerStartupContext.getCommandApiServerTransport();
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
