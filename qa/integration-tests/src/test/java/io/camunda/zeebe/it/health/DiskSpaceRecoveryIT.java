/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.health;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeVolume;
import io.zeebe.containers.engine.ContainerEngine;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Validates that a broker can recover from running out of disk space after compaction.
 *
 * <p>NOTE: this test is split into two nested classes, as the container configuration changes
 * across both tests, and we use the Testcontainers extension to manage its lifecycle. This is an
 * unfortunate limitation until <a
 * href="https://github.com/camunda-community-hub/zeebe-test-container/issues/322">this issue</a> is
 * complete.
 */
@Testcontainers
final class DiskSpaceRecoveryIT {
  private final ZeebeVolume volume = createVolume();
  private final ZeebeContainer container =
      new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
          .withZeebeData(volume)
          .withEnv("ZEEBE_BROKER_EXPERIMENTAL_RAFT_PREFERSNAPSHOTREPLICATIONTHRESHOLD", "0")
          .withEnv("ZEEBE_BROKER_DATA_LOGSEGMENTSIZE", "1MB")
          .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "1MB")
          .withEnv("ZEEBE_BROKER_DATA_DISK_FREESPACE_PROCESSING", "10MB")
          .withEnv("ZEEBE_BROKER_DATA_DISK_FREESPACE_REPLICATION", "1MB");

  private ZeebeClient client;

  @AfterEach
  void afterEach() {
    CloseHelper.quietClose(volume);
  }

  private ZeebeVolume createVolume() {
    final var options = Map.of("type", "tmpfs", "device", "tmpfs", "o", "size=16m");
    return ZeebeVolume.newVolume(cmd -> cmd.withDriver("local").withDriverOpts(options));
  }

  private void publishMessage() {
    client
        .newPublishMessageCommand()
        .messageName("test")
        .correlationKey(String.valueOf(1))
        .variables(Map.of("key", "x".repeat(4096)))
        .send()
        .join();
  }

  @Nested
  final class WithStandardContainerTest {
    @Container
    private final ContainerEngine engine =
        ContainerEngine.builder()
            .withDebugReceiverPort(SocketUtil.getNextAddress().getPort())
            .withContainer(container)
            .withAutoAcknowledge(true)
            .build();

    @BeforeEach
    void beforeEach() {
      client = engine.createClient();
    }

    @Test
    void shouldRecoverAfterOutOfDiskSpaceAfterExporting()
        throws InterruptedException, TimeoutException {
      // given
      final var partitionsClient = PartitionsActuator.of(container);
      partitionsClient.pauseExporting();

      // fill out the disk as fast as possible
      await("until the disk is full")
          .atMost(Duration.ofMinutes(3))
          .pollInterval(1, TimeUnit.MICROSECONDS)
          .untilAsserted(
              () ->
                  assertThatThrownBy(DiskSpaceRecoveryIT.this::publishMessage)
                      .hasRootCauseMessage(
                          "RESOURCE_EXHAUSTED: Cannot accept requests for partition 1. Broker is out of disk space"));

      // when
      partitionsClient.resumeExporting();
      // wait until all records are exported
      engine.waitForIdleState(Duration.ofMinutes(5));
      // trigger a snapshot
      partitionsClient.takeSnapshot();

      // then
      await("until the disk is not full anymore")
          .atMost(Duration.ofMinutes(3))
          .pollDelay(Duration.ZERO)
          .pollInterval(Duration.ofSeconds(1))
          .untilAsserted(
              () -> assertThatNoException().isThrownBy(DiskSpaceRecoveryIT.this::publishMessage));
    }
  }

  @Nested
  final class WithAlreadyDiskFullTest {
    @Container
    private final ContainerEngine engine =
        ContainerEngine.builder()
            .withDebugReceiverPort(SocketUtil.getNextAddress().getPort())
            .withContainer(
                container
                    .withEnv("ZEEBE_BROKER_DATA_DISK_FREESPACE_PROCESSING", "16MB")
                    .withEnv("ZEEBE_BROKER_DATA_DISK_FREESPACE_REPLICATION", "10MB"))
            .build();

    @BeforeEach
    void beforeEach() {
      client = engine.createClient();
    }

    @Test
    void shouldNotProcessWhenOutOfDiskSpaceOnStart() {
      // when - then
      assertThatThrownBy(DiskSpaceRecoveryIT.this::publishMessage)
          .hasRootCauseMessage(
              "RESOURCE_EXHAUSTED: Cannot accept requests for partition 1. Broker is out of disk space");
    }
  }
}
