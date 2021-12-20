/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.network;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.Awaitility;

final class DeploymentDistributionTestCase implements AsymmetricNetworkPartitionTestCase {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeploymentDistributionTestCase.class);

  @Override
  public void given(final ZeebeClient client) {}

  @Override
  public CompletableFuture<?> when(final ZeebeClient client) {
    final var process = Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    client.newDeployCommand().addProcessModel(process, "process.bpmn").send().join();
    return null;
  }

  @Override
  public void then(final ZeebeClient client, final CompletableFuture<?> whenFuture) {
    final var topology = client.newTopologyRequest().send().join();

    final var partitions =
        IntStream.range(1, topology.getPartitionsCount() + 1).boxed().collect(Collectors.toSet());

    Awaitility.await("should be able to create instances on all partitions")
        .ignoreExceptions()
        .atMost(Duration.ofMinutes(1))
        .until(
            () -> {
              final var processInstanceEvent =
                  client
                      .newCreateInstanceCommand()
                      .bpmnProcessId("process")
                      .latestVersion()
                      .send()
                      .join();

              return Protocol.decodePartitionId(processInstanceEvent.getProcessInstanceKey());
            },
            (partitionId) -> {
              LOGGER.info("Instance created on partition: {}", partitionId);
              partitions.remove(partitionId);
              return partitions.isEmpty();
            });
  }
}
