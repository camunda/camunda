/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.atomix.cluster.MemberId;
import io.camunda.configuration.cluster.Zone;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Commands between partitions go through the mailbox when the leader of the receiving partition is
 * out of reach. In one network every leader is reachable, so the leader of the east region acts as
 * if it were in another zone, and counts every leader of its own zone as out of reach, also the
 * ones it leads itself. A deployment is distributed from partition 1 to partition 2 with such a
 * command. It goes into the inbox of partition 2, and the leader of partition 2 drains it.
 */
@ZeebeIntegration
final class MailboxInterPartitionIT {

  private static final String EAST = "mbxInterEast";
  private static final String WEST = "mbxInterWest";
  private static final MemberId EAST_ID = MemberId.from(EAST, 0);
  private static final MemberId WEST_ID = MemberId.from(WEST, 0);
  private static final Set<String> MEMBERS = Set.of(EAST_ID.id(), WEST_ID.id());

  // the members must have the store before the cluster starts
  private static final SharedMailbox MAILBOX = SharedMailbox.registerFor(MEMBERS);

  static {
    MAILBOX.simulateBrokerZone(EAST_ID.id(), "somewhere-else");
  }

  @TestZeebe(purgeAfterEach = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withName("mailbox-inter-partition")
          .withEmbeddedGateway(true)
          .withBrokersCount(2)
          .withPartitionsCount(2)
          .withReplicationFactor(2)
          .multiZone(List.of(new Zone(EAST, 1, 1, 1000), new Zone(WEST, 1, 1, 500)))
          .build();

  @AfterAll
  static void unregister() {
    SharedMailbox.unregister(MEMBERS);
  }

  @Test
  void shouldDistributeACommandToAnotherPartitionThroughTheMailbox() {
    // given
    awaitLeaderOfBothPartitionsIsInTheEastRegion();
    final var east = MAILBOX.brokerView(EAST_ID.id());
    final var sentBefore = east.calls("insert");

    // when
    final long deploymentKey;
    try (final var client = CLUSTER.brokers().get(EAST_ID).newClientBuilder().build()) {
      final var processId = "mailbox-inter-" + UUID.randomUUID();
      deploymentKey =
          client
              .newDeployResourceCommand()
              .addProcessModel(
                  Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                  processId + ".bpmn")
              .send()
              .join()
              .getKey();
    }

    // then - partition 2 got the deployment, and told partition 1 so
    RecordingExporter.await(Duration.ofSeconds(60))
        .until(
            () ->
                RecordingExporter.commandDistributionRecords()
                    .withDistributionIntent(DeploymentIntent.CREATE)
                    .withRecordKey(deploymentKey)
                    .withIntent(CommandDistributionIntent.FINISHED)
                    .exists());
    assertThat(east.calls("insert") - sentBefore)
        .as("the commands to partition 2 were put into its inbox")
        .isGreaterThanOrEqualTo(1);
    assertThat(east.calls("acknowledge"))
        .as("the leader of partition 2 took them out of the inbox")
        .isGreaterThanOrEqualTo(1);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        MAILBOX
                            .store()
                            .claim(
                                2,
                                "check",
                                Set.of(MessageKind.INTER_PARTITION_COMMAND),
                                10,
                                Duration.ofSeconds(1)))
                    .as("nothing is left in the inbox of partition 2")
                    .isEmpty());
  }

  private static void awaitLeaderOfBothPartitionsIsInTheEastRegion() {
    final TestStandaloneBroker east = CLUSTER.brokers().get(EAST_ID);
    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var partitions = PartitionsActuator.of(east).query();
              assertThat(partitions.get(1).role()).asString().isEqualToIgnoringCase("LEADER");
              assertThat(partitions.get(2).role()).asString().isEqualToIgnoringCase("LEADER");
            });
  }
}
