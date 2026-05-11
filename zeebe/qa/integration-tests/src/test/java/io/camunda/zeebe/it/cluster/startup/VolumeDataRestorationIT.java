/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.startup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.CamundaContainer;
import io.camunda.container.CamundaContainer.BrokerContainer;
import io.camunda.container.volume.CamundaVolume;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test verifying that data written to a {@link CamundaVolume} by a Dockerised broker
 * can be extracted and used by an in-process {@link TestStandaloneBroker}. The test flow is:
 *
 * <ol>
 *   <li>Start a {@link BrokerContainer} with a {@link CamundaVolume} attached
 *   <li>Deploy a BPMN process with a service task and create a process instance
 *   <li>Stop the container and extract the volume data to a local directory
 *   <li>Start a {@link TestStandaloneBroker} using the extracted data directory
 *   <li>Verify the broker can activate and complete the pending job
 * </ol>
 */
class VolumeDataRestorationIT {

  private static final String PROCESS_ID = "volume-restore-test";
  private static final String JOB_TYPE = "volume-restore-task";

  @Test
  void shouldCompleteJobAfterRestoringVolumeData(@TempDir final Path tempDir)
      throws IOException, InterruptedException {
    // given -- a broker container with a volume, containing a process instance with a pending job
    final var volume = CamundaVolume.newVolume();

    try (final var broker =
        new BrokerContainer(CamundaContainer.getBrokerImageName())
            .withCamundaData(volume)
            .withEmbeddedGateway()
            .withUnifiedConfig(
                cfg -> cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none))
            .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false")
            .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true")) {
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

    // when -- extract the volume data and start an in-process broker on it
    final var extractionDir = tempDir.resolve("extracted");
    Files.createDirectories(extractionDir);

    volume.extract(
        extractionDir,
        builder -> builder.withContainerPath(CamundaContainer.DEFAULT_CAMUNDA_DATA_PATH));

    // The extraction preserves the container path structure, so data ends up at
    // extractionDir/usr/local/camunda/data. The broker resolves its data directory as
    // <workingDirectory>/data, so we point the working directory at the "usr/local/camunda"
    // subtree.
    final var workingDir = extractionDir.resolve("usr/local/camunda");
    assertThat(workingDir.resolve("data"))
        .describedAs("Extracted data directory should exist")
        .isDirectory();

    // then -- the in-process broker can replay the log and complete the pending job
    try (final var broker =
        new TestStandaloneBroker()
            .withWorkingDirectory(workingDir)
            .withUnauthenticatedAccess()
            .withUnifiedConfig(cfg -> cfg.getSystem().getUpgrade().setEnableVersionCheck(false))) {
      broker.start();

      try (final var client = broker.newClientBuilder().build()) {
        final var jobs =
            Awaitility.await("Job from restored process instance becomes activatable")
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
}
