/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.test.util.testcontainers.ZeebeTestContainerDefaults;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

final class LongPollingIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(LongPollingIT.class);
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("foo"))
          .endEvent()
          .done();

  private Network network;
  private ZeebeCluster cluster;

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper gatewayLogsWatcher =
      new ContainerLogsDumper(() -> cluster.getGateways(), LOGGER);

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper brokerLogsWatcher =
      new ContainerLogsDumper(() -> cluster.getBrokers(), LOGGER);

  @BeforeEach
  void beforeEach() {
    network = Network.newNetwork();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(cluster, network);
  }

  // regression test of https://github.com/camunda/zeebe/issues/9658
  @Test
  void shouldActivateAndCompleteJobsInTime() {
    // given
    cluster =
        ZeebeCluster.builder()
            .withBrokersCount(1)
            .withGatewaysCount(1)
            .withEmbeddedGateway(false)
            .withPartitionsCount(3)
            .withReplicationFactor(1)
            .withBrokerImage(ZeebeTestContainerDefaults.defaultTestImage())
            .withGatewayImage(ZeebeTestContainerDefaults.defaultTestImage())
            .build();
    cluster.start();
    final var zeebeClient = cluster.newClientBuilder().build();
    final var deploymentEvent =
        zeebeClient
            .newDeployResourceCommand()
            .addProcessModel(PROCESS, "process.bpmn")
            .send()
            .join();
    // open the worker first and cause so to run into long polling mode
    zeebeClient
        .newWorker()
        .jobType("foo")
        .handler((c, j) -> c.newCompleteCommand(j).send())
        .timeout(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(10))
        .open();

    // when
    final var resultZeebeFuture =
        zeebeClient
            .newCreateInstanceCommand()
            .processDefinitionKey(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey())
            .withResult()
            .send();

    // then
    Assertions.assertThat((CompletionStage<ProcessInstanceResult>) resultZeebeFuture)
        .succeedsWithin(2, TimeUnit.SECONDS)
        .isNotNull();
  }
}
