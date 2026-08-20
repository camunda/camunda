/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.Network;

@ExtendWith(ContainerStateExtension.class)
final class NewGatewayOldBrokerJobStreamingTest {

  private static final String PROCESS_ID = "new-gateway-old-broker-job-stream-proc";
  private static final String JOB_TYPE = "new-gateway-old-broker-job-stream-type";
  private static Network network;

  @BeforeAll
  static void setUp() {
    network = Network.newNetwork();
  }

  @AfterAll
  static void tearDown() {
    Optional.ofNullable(network).ifPresent(Network::close);
  }

  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  @Test
  void shouldStreamJobsFromOldBrokerThroughNewGateway(final ContainerState state) {
    // given
    state.withNetwork(network).withOldBroker().withNewGateway().start(true);

    final List<ActivatedJob> streamedJobs = new CopyOnWriteArrayList<>();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    state
        .client()
        .newDeployResourceCommand()
        .addProcessModel(process, PROCESS_ID + ".bpmn")
        .send()
        .join();

    final var stream =
        state.client().newStreamJobsCommand().jobType(JOB_TYPE).consumer(streamedJobs::add).send();
    try {
      // when: an instance producing a job of the streamed type is created through the new gateway
      state
          .client()
          .newCreateInstanceCommand()
          .bpmnProcessId(PROCESS_ID)
          .latestVersion()
          .send()
          .join();

      // then: the old broker still pushes the job to the new gateway's stream over the legacy wire
      Awaitility.await("job streamed from the old broker through the new gateway")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(() -> assertThat(streamedJobs).hasSize(1));
    } finally {
      stream.cancel(true);
    }
  }
}
