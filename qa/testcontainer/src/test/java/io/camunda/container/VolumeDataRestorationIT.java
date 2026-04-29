/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.CamundaContainer.BrokerContainer;
import io.camunda.container.volume.CamundaVolume;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Integration test verifying that a {@link CamundaVolume} preserves broker state across container
 * restarts. The test flow is:
 *
 * <ol>
 *   <li>Start a {@link BrokerContainer} with a {@link CamundaVolume} attached
 *   <li>Deploy a BPMN process with a service task and create a process instance
 *   <li>Stop the container
 *   <li>Start a new {@link BrokerContainer} mounting the same volume
 *   <li>Verify the new broker can activate and complete the pending job
 * </ol>
 */
class VolumeDataRestorationIT {

  private static final String PROCESS_ID = "volume-restore-test";
  private static final String JOB_TYPE = "volume-restore-task";

  @Test
  void shouldCompleteJobAfterRestartingBrokerWithSameVolume() {
    // given -- a broker container with a volume, containing a process instance with a pending job
    final var volume = CamundaVolume.newVolume();

    try (final var broker = newBroker(volume)) {
      broker.start();

      try (final var client =
          CamundaClient.newClientBuilder().restAddress(broker.getRestAddress()).build()) {
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                    .endEvent()
                    .done(),
                "volume-restore-test.bpmn")
            .send()
            .join();

        client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();
      }
    }

    // when -- start a new broker container mounting the same volume
    try (final var broker = newBroker(volume)) {
      broker.start();

      // then -- the pending job from the previous container is activatable and completable
      try (final var client =
          CamundaClient.newClientBuilder().restAddress(broker.getRestAddress()).build()) {
        final var jobs =
            Awaitility.await("Job from restored volume data becomes activatable")
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .ignoreExceptions()
                .until(
                    () ->
                        client
                            .newActivateJobsCommand()
                            .jobType(JOB_TYPE)
                            .maxJobsToActivate(1)
                            .timeout(Duration.ofSeconds(30))
                            .send()
                            .join()
                            .getJobs(),
                    j -> !j.isEmpty());

        assertThat(jobs)
            .describedAs("Should find the pending job from the restored volume data")
            .hasSize(1);

        client.newCompleteCommand(jobs.getFirst()).send().join();
      }
    }
  }

  private static BrokerContainer newBroker(final CamundaVolume volume) {
    return new BrokerContainer(CamundaContainer.getBrokerImageName())
        .withCamundaData(volume)
        .withEmbeddedGateway()
        .withUnifiedConfig(
            cfg -> cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none))
        .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false");
  }
}
