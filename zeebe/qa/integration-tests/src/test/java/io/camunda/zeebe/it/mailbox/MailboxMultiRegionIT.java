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
import io.camunda.client.CamundaClient;
import io.camunda.configuration.cluster.Zone;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Two regions in one network. The leader of the partition is in the east region. The gateway of the
 * west region acts as if it could not see it: it finds the leader in the mailbox, and the request
 * is answered through the database, not over the network.
 */
@ZeebeIntegration
final class MailboxMultiRegionIT {

  private static final String EAST = "mbxEast";
  private static final String WEST = "mbxWest";
  private static final MemberId EAST_ID = MemberId.from(EAST, 0);
  private static final MemberId WEST_ID = MemberId.from(WEST, 0);
  private static final Set<String> MEMBERS = Set.of(EAST_ID.id(), WEST_ID.id());

  // the members must have the store before the cluster starts
  private static final SharedMailbox MAILBOX = SharedMailbox.registerFor(MEMBERS);

  @TestZeebe(purgeAfterEach = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withName("mailbox-multi-region")
          .withEmbeddedGateway(true)
          .withBrokersCount(2)
          .withPartitionsCount(1)
          .withReplicationFactor(2)
          .multiZone(List.of(new Zone(EAST, 1, 1, 1000), new Zone(WEST, 1, 1, 500)))
          .build();

  @AfterAll
  static void unregister() {
    SharedMailbox.unregister(MEMBERS);
  }

  @Test
  void shouldAnswerARequestOfTheOtherRegionThroughTheMailbox() {
    // given
    awaitGatewayKnowsTheLeader();
    final var gateway = MAILBOX.gatewayView(WEST_ID.id());
    final var leader = MAILBOX.brokerView(EAST_ID.id());
    final var requestsBefore = gateway.calls("insert");

    // when
    try (final var client = clientOf(WEST_ID)) {
      final var deployment = deploy(client);
      final var instance = createInstance(client, deployment);

      // then
      assertThat(deployment).isPositive();
      assertThat(instance).isPositive();
    }

    assertThat(gateway.calls("insert") - requestsBefore)
        .as("the requests of the west gateway were put into the inbox")
        .isGreaterThanOrEqualTo(2);
    assertThat(leader.calls("putResponse"))
        .as("the east leader put the responses into the outbox")
        .isGreaterThanOrEqualTo(2);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(MAILBOX.store().claim(1, "check", 10, Duration.ofSeconds(1)))
                    .as("the leader took the requests out of the inbox")
                    .isEmpty());
  }

  @Test
  void shouldNotUseTheMailboxInTheRegionOfTheLeader() {
    // given
    awaitGatewayKnowsTheLeader();
    final var gateway = MAILBOX.gatewayView(EAST_ID.id());
    final var requestsBefore = gateway.calls("insert");

    // when
    try (final var client = clientOf(EAST_ID)) {
      final var deployment = deploy(client);

      // then
      assertThat(deployment).isPositive();
    }
    assertThat(gateway.calls("insert")).isEqualTo(requestsBefore);
  }

  @Test
  void shouldPollAndCompleteJobsFromTheOtherRegion() {
    // given
    awaitGatewayKnowsTheLeader();
    final var gateway = MAILBOX.gatewayView(WEST_ID.id());
    final var jobType = "mailbox-job-" + UUID.randomUUID();
    final var requestsBefore = gateway.calls("insert");

    try (final var client = clientOf(WEST_ID)) {
      final var processId = "mailbox-jobs-" + UUID.randomUUID();
      final var definitionKey =
          client
              .newDeployResourceCommand()
              .addProcessModel(
                  Bpmn.createExecutableProcess(processId)
                      .startEvent()
                      .serviceTask("task", task -> task.zeebeJobType(jobType))
                      .endEvent()
                      .done(),
                  processId + ".bpmn")
              .send()
              .join()
              .getProcesses()
              .get(0)
              .getProcessDefinitionKey();
      createInstance(client, definitionKey);

      // when - a worker of the west region polls for the job
      final var jobs =
          client
              .newActivateJobsCommand()
              .jobType(jobType)
              .maxJobsToActivate(1)
              .requestTimeout(Duration.ofSeconds(30))
              .send()
              .join()
              .getJobs();

      // then
      assertThat(jobs).hasSize(1);
      client.newCompleteCommand(jobs.get(0).getKey()).send().join();
    }

    assertThat(gateway.calls("insert") - requestsBefore)
        .as("deploy, create, activate and complete went through the inbox")
        .isGreaterThanOrEqualTo(4);
  }

  /**
   * The gateway of the west region first sends over the network, until its copy of the recorded
   * leaders has the leader. A test that wants the mailbox must wait for that copy.
   */
  private static void awaitGatewayKnowsTheLeader() {
    awaitLeaderIsInTheEastRegion();
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(MAILBOX.store().findLeaders())
                    .extracting(PartitionLeader::nodeId)
                    .contains(EAST_ID.id()));
    final var reads = MAILBOX.gatewayView(WEST_ID.id());
    final var readsBefore = reads.calls("findLeaders");
    // a read that started after the record exists, and the next one starts after it ended
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> reads.calls("findLeaders") >= readsBefore + 2);
  }

  private static void awaitLeaderIsInTheEastRegion() {
    final var east = (TestStandaloneBroker) CLUSTER.brokers().get(EAST_ID);
    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(PartitionsActuator.of(east).query().get(1))
                    .extracting(status -> status.role())
                    .asString()
                    .isEqualToIgnoringCase("LEADER"));
  }

  private static CamundaClient clientOf(final MemberId member) {
    return ((TestStandaloneBroker) CLUSTER.brokers().get(member)).newClientBuilder().build();
  }

  private static long deploy(final CamundaClient client) {
    final var processId = "mailbox-" + UUID.randomUUID();
    final var process = Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    final var response =
        client
            .newDeployResourceCommand()
            .addProcessModel(process, processId + ".bpmn")
            .send()
            .join();
    return response.getProcesses().get(0).getProcessDefinitionKey();
  }

  private static long createInstance(final CamundaClient client, final long definitionKey) {
    return client
        .newCreateInstanceCommand()
        .processDefinitionKey(definitionKey)
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
