/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.configuration.Zone;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.util.List;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class ZeebeIntegrationExtensionTest {

  private static final List<Zone> ZONE_CONFIGS =
      List.of(new Zone("zoneA", 2, 2, 1000), new Zone("zoneB", 1, 1, 500));

  @TestZeebe(
      autoStart = false,
      awaitStarted = false,
      awaitReady = false,
      awaitCompleteTopology = false,
      purgeAfterEach = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withBrokersCount(3)
          .withPartitionsCount(2)
          .withReplicationFactor(3)
          .multiZone(ZONE_CONFIGS)
          .build();

  @TestZeebe(
      autoStart = false,
      awaitStarted = false,
      awaitReady = false,
      awaitCompleteTopology = false,
      purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withUnifiedConfig(
              uc -> {
                uc.getCluster().setZone("zoneA");
                uc.getCluster().setNodeId(1);
              });

  @Test
  void shouldCreateWorkingDirectoriesForZoneAwareClusterBrokers() {
    // given
    final var brokerA0 = CLUSTER.brokers().get(MemberId.from("zoneA", 0));
    final var brokerA1 = CLUSTER.brokers().get(MemberId.from("zoneA", 1));
    final var brokerB0 = CLUSTER.brokers().get(MemberId.from("zoneB", 0));

    // then
    assertWorkingDirectory(brokerA0, "broker-zoneA_0");
    assertWorkingDirectory(brokerA1, "broker-zoneA_1");
    assertWorkingDirectory(brokerB0, "broker-zoneB_0");
  }

  @Test
  void shouldCreateManagedDirectoryForZoneAwareStandaloneBroker() {
    // given
    final var workingDirectory = BROKER.getWorkingDirectory();

    // then
    assertThat(workingDirectory).isNotNull().exists().isDirectory();
    assertThat(workingDirectory.getFileName()).hasToString("broker-zoneA_1");
    assertThat(workingDirectory.getParent().getFileName().toString())
        .startsWith("junit-broker-zoneA_1");
  }

  private static void assertWorkingDirectory(
      final TestStandaloneBroker broker, final String expectedDirectoryName) {
    assertThat(broker.getWorkingDirectory()).isNotNull().exists().isDirectory();
    assertThat(broker.getWorkingDirectory().getFileName()).hasToString(expectedDirectoryName);
  }
}
