/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.health;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_ENV_VAR;
import static io.camunda.zeebe.it.util.ZeebeContainerUtil.newClientBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.grpc.Status.Code;
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
import org.junit.jupiter.api.extension.RegisterExtension;
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
          .withEnv("ZEEBE_BROKER_DATA_DISK_FREESPACE_PROCESSING", "8MB")
          .withEnv("ZEEBE_BROKER_DATA_DISK_FREESPACE_REPLICATION", "1MB")
          .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
          .withEnv(CREATE_SCHEMA_ENV_VAR, "false");

  @SuppressWarnings("JUnitMalformedDeclaration")
  @RegisterExtension
  private final ContainerLogsDumper logsDumper =
      new ContainerLogsDumper(() -> Map.of("broker", container));

  private CamundaClient client;

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
        .variables(Map.of("key", "abc".repeat(4096)))
        .timeToLive(Duration.ZERO)
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
      client = newClientBuilder(engine).build();
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
              () -> {
                // Ensure at least one snapshot with a compactable processed position before going
                // out of disk space
                partitionsClient.takeSnapshot();
                assertThatThrownBy(DiskSpaceRecoveryIT.this::publishMessage)
                    .hasRootCauseMessage(
                        "RESOURCE_EXHAUSTED: Cannot accept requests for partition 1. Broker is out of disk space");
              });

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
      client = newClientBuilder(engine).build();
    }

    @Test
    void shouldNotProcessWhenOutOfDiskSpaceOnStart() {
      // given
      var retryCount = 0;

      // when
      while (retryCount < 3) {
        try {
          publishMessage();
        } catch (final ClientStatusException e) {
          retryCount++;

          if (e.getStatusCode() == Code.DEADLINE_EXCEEDED) {
            continue;
          }

          // then
          assertThat(e.getStatusCode()).isEqualTo(Code.RESOURCE_EXHAUSTED);
          assertThat(e)
              .hasRootCauseMessage(
                  "RESOURCE_EXHAUSTED: Cannot accept requests for partition 1. Broker is out of disk space");
        }
      }

      assertThat(retryCount)
          .as(
              "Expected at least one out of three requests to not timeout; the container may be broken")
          .isLessThan(4);
    }
  }
}
