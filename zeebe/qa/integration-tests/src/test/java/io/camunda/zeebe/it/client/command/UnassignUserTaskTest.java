/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
@AutoCloseResources
class UnassignUserTaskTest {

  @TestZeebe(clusterSize = 1, partitionCount = 1, replicationFactor = 1)
  private static final TestCluster CLUSTER =
      TestCluster.builder().withEmbeddedGateway(false).withGatewaysCount(1).build();

  @AutoCloseResource private ZeebeClient client;

  private long userTaskKey;

  @BeforeEach
  void initClientAndInstances() {
    final var gateway = CLUSTER.availableGateway();
    client =
        CLUSTER
            .newClientBuilder()
            .restAddress(gateway.restAddress())
            .defaultRequestTimeout(Duration.ofSeconds(15))
            .build();
    final ZeebeResourcesHelper resourcesHelper = new ZeebeResourcesHelper(client);
    userTaskKey = resourcesHelper.createSingleUserTask();
  }

  @Test
  void shouldUnassignUserTask() {
    // given
    client.newUserTaskAssignCommand(userTaskKey).assignee("foobar").send().join();

    // when
    client.newUserTaskUnassignCommand(userTaskKey).send().join();

    // then
    ZeebeAssertHelper.assertUserTaskAssigned(
        userTaskKey,
        2,
        (userTask) -> {
          assertThat(userTask.getAssignee()).isEmpty();
          assertThat(userTask.getAction()).isEqualTo("unassign");
        });
  }
}
