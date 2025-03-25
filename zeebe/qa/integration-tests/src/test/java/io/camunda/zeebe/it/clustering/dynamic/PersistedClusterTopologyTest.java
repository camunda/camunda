/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@ZeebeIntegration
@Timeout(2 * 60)
@AutoCloseResources
public class PersistedClusterTopologyTest {

  private static final int CLUSTER_SIZE = 3;
  private static final int PARTITION_COUNT = 3;
  private static final int REPLICATION_FACTOR = 3;

  @AutoCloseResource private ZeebeClient client;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withGatewaysCount(1)
          .withEmbeddedGateway(true)
          .withBrokersCount(CLUSTER_SIZE)
          .withPartitionsCount(PARTITION_COUNT)
          .withReplicationFactor(REPLICATION_FACTOR)
          .withGatewayConfig(
              g ->
                  g.gatewayConfig()
                      .getCluster()
                      .getMembership()
                      // Decrease the timeouts for fast convergence of gateway topology. When the
                      // broker is shutdown, the topology update takes at least 10 seconds with
                      // the
                      // default values.
                      .setSyncInterval(Duration.ofSeconds(1))
                      .setFailureTimeout(Duration.ofSeconds(5)))
          .build();

  @Test
  void canHandleTopologyRequestsWhenBroker0IsRemoved() {
    // given
    cluster.awaitCompleteTopology();
    client = cluster.newClientBuilder().build();

    ///  Overview of the process being deployed
    ///                      _____________________________
    //     START_EVENT ->   |         service1           |     -------> END_EVENT
    //                      |-----(message catch event)--|                 ^
    //                                        |          _______________   |
    //                                        |---------| serviceCatch | --|
    //                                                  ---------------
    final var deploymentEvent =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("processes/catch_event.bpmn")
            .send()
            .join();

    cluster.shutdown();

    // when
    cluster.setPartitionCount(1);
    cluster.start();
    cluster.awaitCompleteTopology(
        CLUSTER_SIZE, PARTITION_COUNT, REPLICATION_FACTOR, Duration.ofSeconds(10));

    final var processId =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(
                deploymentEvent.getProcesses().getFirst().getProcessDefinitionKey())
            .send()
            .join();

    final var result =
        client
            .newPublishMessageCommand()
            .messageName("catch_event")
            .correlationKey("11")
            .send()
            .join();

    // then
    // there are no jobs to activate because of the successful message catch event triggered
    final var activatedJobs =
        client.newActivateJobsCommand().jobType("service1").maxJobsToActivate(1).send().join();
    assertThat(activatedJobs.getJobs()).isEmpty();
  }
}
