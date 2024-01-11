/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.ZeebePort;
import io.zeebe.containers.ZeebeVolume;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class StateMigrationTest {

  public static final DockerImageName OLD_IMAGE = DockerImageName.parse("camunda/zeebe:8.2.20");
  public static final DockerImageName NEW_IMAGE = DockerImageName.parse("camunda/zeebe:8.3.5");

  private final ZeebeVolume volume = ZeebeVolume.newVolume();
  private ZeebeBrokerContainer broker;

  //  private final ZeebeCluster cluster =
  //      ZeebeCluster.builder()
  //          .withBrokersCount(1)
  //          //          .withGatewaysCount(0)
  //          .withPartitionsCount(1)
  //          //          .withEmbeddedGateway(true)
  //          .withImage(DockerImageName.parse("camunda/zeebe").withTag("8.2.20"))
  //          .build();

  @BeforeEach
  void setup() throws IOException, InterruptedException {
    //    cluster.getBrokers().values().forEach(broker -> broker.withZeebeData(volume));
    //    cluster.start();

    //    final ZeebeVolume volume = new ZeebeVolume();
    broker =
        new ZeebeBrokerContainer(OLD_IMAGE)
            .withZeebeData(volume)
            .withExposedPorts(
                ZeebePort.GATEWAY.getPort(),
                ZeebePort.COMMAND.getPort(),
                ZeebePort.INTERNAL.getPort(),
                ZeebePort.MONITORING.getPort())
            .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
            .withCreateContainerCmdModifier(cmd -> cmd.withUser("1000"));
    broker.start();
  }

  @AfterEach
  void tearDown() {
    broker.stop();
  }

  @Test
  void shouldMigrateState(
      @TempDir final Path tempDir1, @TempDir final Path tempDir2, @TempDir final Path tempDir3)
      throws IOException {
    // given
    volume.extract(tempDir1);
    System.out.println("start: " + getDataSize(tempDir1));

    try (final ZeebeClient client = newClient()) {
      for (int i = 1; i <= 100; i++) {
        final int finalI = i;
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("process%d".formatted(i))
                    .startEvent()
                    .serviceTask("task" + i, t -> t.zeebeJobType("job" + finalI))
                    .endEvent()
                    .documentation("x".repeat(1_000_000))
                    .done(),
                "process%d.bpmn".formatted(i))
            .send()
            .join();

        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process%d".formatted(i))
            .latestVersion()
            .send()
            .join();
      }

      Assertions.assertThat(
              client
                  .newActivateJobsCommand()
                  .jobType("job100")
                  .maxJobsToActivate(1)
                  .requestTimeout(Duration.ofSeconds(10))
                  .send()
                  .join()
                  .getJobs())
          .describedAs("Expected that all jobs are created")
          .hasSize(1);
    }

    broker.stop();
    volume.extract(tempDir2);
    System.out.println("before migration: " + getDataSize(tempDir2));

    // when
    // do stuff on the broker, then stop it
    broker.setDockerImageName(NEW_IMAGE.asCanonicalNameString());
    broker.start();
    //    cluster
    //        .getBrokers()
    //        .values()
    //        .forEach(
    //            broker ->
    //                broker.setDockerImageName(
    //                    DockerImageName.parse("camunda/zeebe")
    //                        .withTag("8.3.5")
    //                        .asCanonicalNameString()));
    //    //
    // ZeebeTestContainerDefaults.defaultTestImage().asCanonicalNameString()));
    //    cluster.start();
    try (final ZeebeClient client = newClient()) {
      Awaitility.await(
              "ensure that the cluster has completed migrating the state by awaiting a response")
          .atMost(Duration.ofMinutes(1))
          .untilAsserted(
              () ->
                  Assertions.assertThatNoException()
                      .isThrownBy(
                          () ->
                              client
                                  .newPublishMessageCommand()
                                  .messageName("msg")
                                  .correlationKey("key")
                                  .send()
                                  .join()));
    }

    // then
    volume.extract(tempDir3);
    System.out.println("after migration: " + getDataSize(tempDir3));
  }

  private ZeebeClient newClient() {
    //    return broker.newClientBuilder().build();

    return ZeebeClient.newClientBuilder()
        .gatewayAddress(broker.getExternalAddress(ZeebePort.GATEWAY.getPort()))
        .usePlaintext()
        .build();
  }

  private static String getDataSize(final Path tempDir) {
    return FileUtils.byteCountToDisplaySize(FileUtils.sizeOfDirectory(tempDir.toFile()));
  }
}
