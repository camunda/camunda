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
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.util.ByteValue;
import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.ZeebePort;
import io.zeebe.containers.ZeebeVolume;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class StateMigrationTest {

  public static final int NUMBER_OF_PROCESSES_TO_DEPLOY = 11_339;
  public static final boolean SKIP_SNAPSHOT = false;

  private static final Logger LOG = LoggerFactory.getLogger("StateMigrationTest");

  private static final DockerImageName OLD_IMAGE = DockerImageName.parse("camunda/zeebe:8.2.20");
  private static final DockerImageName NEW_IMAGE = DockerImageName.parse("camunda/zeebe:8.3.5");

  private final ZeebeVolume volume = ZeebeVolume.newVolume();
  private final ZeebeBrokerContainer broker =
      new ZeebeBrokerContainer(OLD_IMAGE)
          .withZeebeData(volume)
          .withExposedPorts(
              ZeebePort.GATEWAY.getPort(),
              ZeebePort.COMMAND.getPort(),
              ZeebePort.INTERNAL.getPort(),
              ZeebePort.MONITORING.getPort())
          .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
          .withCreateContainerCmdModifier(cmd -> cmd.withUser("1000"));

  @BeforeEach
  void setup() {
    startAndAwaitStartup(broker);
  }

  @AfterEach
  void tearDown() {
    stop(broker);
  }

  @Test
  void shouldMigrateState(
      @TempDir final Path tempDir1,
      @TempDir final Path tempDir2,
      @TempDir final Path tempDir3,
      @TempDir final Path tempDir4)
      throws IOException, InterruptedException {
    // given
    volume.extract(tempDir1);
    LOG.info("Disk size initially: " + getDataSize(tempDir1));

    try (final ZeebeClient client = newClient(broker)) {
      LOG.info("Deploying and starting {} processes...", NUMBER_OF_PROCESSES_TO_DEPLOY);
      for (int i = 1; i <= NUMBER_OF_PROCESSES_TO_DEPLOY; i++) {
        final int finalI = i;
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("process%d".formatted(i))
                    .startEvent()
                    .serviceTask("task" + i, t -> t.zeebeJobType("job" + finalI))
                    .endEvent()
                    .documentation(RandomStringUtils.randomAlphabetic(100_000))
                    .done(),
                "process%d.bpmn".formatted(i))
            .send()
            .join();

        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process%d".formatted(i))
            .latestVersion()
            .variable("x", RandomStringUtils.randomAlphabetic(100_000))
            .send()
            .join();
      }

      Assertions.assertThat(
              client
                  .newActivateJobsCommand()
                  .jobType("job" + NUMBER_OF_PROCESSES_TO_DEPLOY)
                  .maxJobsToActivate(1)
                  .requestTimeout(Duration.ofSeconds(10))
                  .send()
                  .join()
                  .getJobs())
          .describedAs("Expected that all jobs are created")
          .hasSize(1);
      LOG.info("Finished starting {} processes", NUMBER_OF_PROCESSES_TO_DEPLOY);
    }

    takeSnapshot(broker);

    stop(broker);
    volume.extract(tempDir2);
    LOG.info("Disk size before restart: " + getDataSize(tempDir2));

    startAndAwaitStartup(broker);
    takeSnapshot(broker);
    stop(broker);
    volume.extract(tempDir3);
    LOG.info("Disk size before migration: " + getDataSize(tempDir3));

    // when
    LOG.info("Upgrading broker to version {}", NEW_IMAGE.getVersionPart());
    broker.setDockerImageName(NEW_IMAGE.asCanonicalNameString());
    startAndAwaitStartup(broker);
    takeSnapshot(broker);
    stop(broker);

    // then
    volume.extract(tempDir4);
    LOG.info("Disk size after migration: " + getDataSize(tempDir4));
  }

  private static void takeSnapshot(final ZeebeBrokerContainer broker) throws InterruptedException {
    if (SKIP_SNAPSHOT) {
      LOG.info(
          "Not taking a snapshot, because SKIP_SNAPSHOT is set to true, but still waiting 10s");
      Thread.sleep(Duration.ofSeconds(10).toMillis());
      return;
    }
    LOG.info("Take a snapshot...");
    PartitionsActuator.of(broker).takeSnapshot();
    Thread.sleep(Duration.ofSeconds(10).toMillis());
    LOG.info("Snapshot taken (probably)");
  }

  private static void stop(final ZeebeBrokerContainer broker) {
    LOG.info("Waiting for broker to stop...");
    broker.stop();
    LOG.info("Broker stopped");
  }

  private static void startAndAwaitStartup(final ZeebeBrokerContainer broker) {
    LOG.info("Waiting for broker to start...");
    final Instant startTime = Instant.now();
    broker.start();
    try (final ZeebeClient client = newClient(broker)) {
      Awaitility.await(
              "ensure that the cluster has completed migrating the state by awaiting a response")
          .atMost(Duration.ofMinutes(1))
          .untilAsserted(
              () ->
                  Assertions.assertThatNoException()
                      .isThrownBy(
                          () ->
                              Assertions.assertThat(
                                      client
                                          .newPublishMessageCommand()
                                          .messageName("msg")
                                          .correlationKey("key")
                                          .send()
                                          .join()
                                          .getMessageKey())
                                  .isGreaterThan(0)));
    }
    LOG.info("Broker started after {}", Duration.between(startTime, Instant.now()));
  }

  private static ZeebeClient newClient(final ZeebeBrokerContainer broker) {
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(broker.getExternalAddress(ZeebePort.GATEWAY.getPort()))
        .usePlaintext()
        .build();
  }

  private static String getDataSize(final Path tempDir) {
    //
    //    // first delete the runtime folder to force the snapshot links to be resolved
    //    try {
    //      // data is a copy of the container's volume, so we can delete it without any worries
    //      FileUtil.deleteFolder(
    //          tempDir.resolve("usr/local/zeebe/data/raft-partition/partitions/1/runtime"));
    //    } catch (final IOException e) {
    //      throw new RuntimeException(e);
    //    }

    // snapshot is located in: usr/local/zeebe/data/raft-partition/partitions/1/snapshots/
    // measure runtime because snapshot is linked, making it hard to measure size
    // runtime is located in: usr/local/zeebe/data/raft-partition/partitions/1/runtime/
    final long sizeOfDirectory =
        sizeOfDirectory(
            tempDir.resolve("usr/local/zeebe/data/raft-partition/partitions/1/runtime"));
    return ByteValue.prettyPrint(sizeOfDirectory);
  }

  private static long sizeOfDirectory(final Path folder) {
    try (final var stream = Files.walk(folder)) {
      return stream
          .filter(p -> p.toFile().isFile())
          .mapToLong(
              p -> {
                if (Files.isSymbolicLink(p)) {
                  try {
                    return Files.readSymbolicLink(p).toFile().length();
                  } catch (final IOException e) {
                    throw new RuntimeException(e);
                  }
                }
                return p.toFile().length();
              })
          .sum();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
