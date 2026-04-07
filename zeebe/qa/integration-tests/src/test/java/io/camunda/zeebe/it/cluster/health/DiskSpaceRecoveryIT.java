/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.health;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_ENV_VAR;
import static io.camunda.application.commons.security.CamundaSecurityConfiguration.AUTHORIZATION_CHECKS_ENV_VAR;
import static io.camunda.application.commons.security.CamundaSecurityConfiguration.UNPROTECTED_API_ENV_VAR;
import static io.camunda.zeebe.it.util.ZeebeContainerUtil.newClientBuilder;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.CamundaContainer.BrokerContainer;
import io.camunda.container.volume.CamundaVolume;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.grpc.Status.Code;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
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
  private final CamundaVolume volume = createVolume();
  private CamundaClient client;

  @AfterEach
  void afterEach() {
    CloseHelper.quietClose(volume);
  }

  private CamundaVolume createVolume() {
    final var options = Map.of("type", "tmpfs", "device", "tmpfs", "o", "size=16m");
    return CamundaVolume.newVolume(cmd -> cmd.withDriver("local").withDriverOpts(options));
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
    private final BrokerContainer broker =
        new BrokerContainer(ZeebeTestContainerDefaults.defaultTestImage())
            .withCamundaData(volume)
            .withRecordingExporter()
            .withUnifiedConfig(
                cfg -> {
                  cfg.getCluster().getRaft().setPreferSnapshotReplicationThreshold(0);
                  cfg.getData()
                      .getPrimaryStorage()
                      .getDisk()
                      .getFreeSpace()
                      .setProcessing(DataSize.ofMegabytes(8));
                  cfg.getData()
                      .getPrimaryStorage()
                      .getDisk()
                      .getFreeSpace()
                      .setReplication(DataSize.ofMegabytes(1));
                  cfg.getData()
                      .getPrimaryStorage()
                      .getLogStream()
                      .setLogSegmentSize(DataSize.ofMegabytes(1));
                  cfg.getCluster().getNetwork().setMaxMessageSize(DataSize.ofMegabytes(1));
                  cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                })
            .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
            .withEnv(UNPROTECTED_API_ENV_VAR, "true")
            .withEnv(AUTHORIZATION_CHECKS_ENV_VAR, "false");

    @BeforeEach
    void beforeEach() {
      client = newClientBuilder(broker).build();
    }

    @Test
    void shouldRecoverAfterOutOfDiskSpaceAfterExporting() throws InterruptedException {
      // given
      final var partitionsClient = PartitionsActuator.of(broker);
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
      Awaitility.await()
          .atMost(Duration.ofMinutes(3))
          .until(
              () -> {
                final var partitionStatus = partitionsClient.query();
                final var processedPosition = partitionStatus.get(1).processedPosition();
                final var exportedPosition = partitionStatus.get(1).exportedPosition();
                return exportedPosition >= processedPosition;
              });

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
    private final BrokerContainer broker =
        new BrokerContainer(ZeebeTestContainerDefaults.defaultTestImage())
            .withCamundaData(volume)
            .withUnifiedConfig(
                cfg -> {
                  cfg.getData()
                      .getPrimaryStorage()
                      .getDisk()
                      .getFreeSpace()
                      .setProcessing(DataSize.ofMegabytes(16));
                  cfg.getData()
                      .getPrimaryStorage()
                      .getDisk()
                      .getFreeSpace()
                      .setReplication(DataSize.ofMegabytes(10));
                  cfg.getCluster().getRaft().setPreferSnapshotReplicationThreshold(0);
                  cfg.getData()
                      .getPrimaryStorage()
                      .getLogStream()
                      .setLogSegmentSize(DataSize.ofMegabytes(1));
                  cfg.getCluster().getNetwork().setMaxMessageSize(DataSize.ofMegabytes(1));
                  cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                })
            .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
            .withEnv(UNPROTECTED_API_ENV_VAR, "true");

    @BeforeEach
    void beforeEach() {
      client = newClientBuilder(broker).build();
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
