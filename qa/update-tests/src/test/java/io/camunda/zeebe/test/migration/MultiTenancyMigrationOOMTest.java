/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.HostConfig;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeVolume;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
final class MultiTenancyMigrationOOMTest {
  private static final DockerImageName PRE_MIGRATION_VERSION =
      DockerImageName.parse("camunda/zeebe:8.2.21");
  private static final DockerImageName POST_MIGRATION_VERSION =
      ZeebeTestContainerDefaults.defaultTestImage();

  private final ZeebeVolume volume = ZeebeVolume.newVolume();

  @Container
  private final ZeebeContainer container =
      new ZeebeContainer(PRE_MIGRATION_VERSION)
          .withEnv("ZEEBE_LOG_LEVEL", "debug")
          // memory limit has to be higher initially because of the unbounded cache in process state
          // and to make it faster to generate our large snapshot
          .withEnv("ZEEBE_BROKER_EXPERIMENTAL_ROCKSDB_MEMORYLIMIT", "512MB")
          .withCreateContainerCmdModifier(cmd -> constrainMemory(cmd, 2048))
          .withEnv("ZEEBE_BROKER_EXPERIMENTAL_ENGINE_CACHES_DRGCACHECAPACITY", "5")
          .withZeebeData(volume);

  private void forceSnapshot() {
    final var partitions = PartitionsActuator.of(container);
    partitions.takeSnapshot();
    Awaitility.await("Snapshot is taken on all partitions")
        .atMost(Duration.ofSeconds(60))
        .until(() -> partitions.query().values().stream().allMatch(p -> p.snapshotId() != null));
  }

  private void constrainMemory(final CreateContainerCmd cmd, final long memoryLimitMb) {
    final var hostConfig = Optional.ofNullable(cmd.getHostConfig()).orElseGet(HostConfig::new);
    cmd.withHostConfig(hostConfig.withMemory(memoryLimitMb * 1024 * 1024L)).withUser("0:0");
  }

  private void reconfigureContainerWithNewerVersion() {
    container.stop();
    container
        // constrain memory to "expected" limits: 128MB * 3 + some JVM memory <= 600MB
        // this means 600MB should be enough to run Zeebe, including performing migrations
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_ROCKSDB_MEMORYLIMIT", "128MB")
        .withCreateContainerCmdModifier(cmd -> constrainMemory(cmd, 600))
        .withStartupTimeout(Duration.ofMinutes(2))
        .setDockerImageName(POST_MIGRATION_VERSION.asCanonicalNameString());
  }

  @Nested
  final class ProcessStateTest {
    @RegressionTest("https://github.com/camunda/zeebe/issues/14975")
    void shouldNotOomOnProcessStateMigration() {
      // given
      generateLargeProcessState();

      // when
      reconfigureContainerWithNewerVersion();

      // then - will fail to start with an OOMKilled error if it crashes
      assertThatCode(container::start).doesNotThrowAnyException();
    }

    private void generateLargeProcessState() {
      generateProcessState();
      forceSnapshot();
    }

    private void generateProcessState() {
      final var processCount = 150;
      try (final var client =
          ZeebeClient.newClientBuilder()
              .usePlaintext()
              .gatewayAddress(container.getExternalGatewayAddress())
              .build()) {
        for (int i = 0; i < processCount / 3; i++) {
          CompletableFuture.allOf(
                  deployLargeProcess(client),
                  deployLargeProcess(client),
                  deployLargeProcess(client))
              .join();

          // we found that we can get about ~10 process definitions cached before running out of
          // memory and having to clear the cache
          if (i > 0 && i % 3 == 0) {
            // sending an invalid process forces clearing the unbounded in memory cache
            clearUnboundedInMemoryProcessCache(client);
          }
        }
      }
    }

    private CompletableFuture<?> deployLargeProcess(final ZeebeClient client) {
      return (CompletableFuture<?>)
          client
              .newDeployResourceCommand()
              .addProcessModel(generateProcess(), Strings.newRandomValidBpmnId() + ".bpmn")
              .send();
    }

    private void clearUnboundedInMemoryProcessCache(final ZeebeClient client) {
      final Future<?> failure =
          client
              .newDeployResourceCommand()
              .addResourceBytes("foobar".getBytes(StandardCharsets.UTF_8), "invalid.bpmn")
              .send();
      assertThat(failure).failsWithin(Duration.ofSeconds(30)).withThrowableThat().isNotNull();
    }

    private BpmnModelInstance generateProcess() {
      final var builder = Bpmn.createExecutableProcess(Strings.newRandomValidBpmnId()).startEvent();
      final var name = Strings.newRandomValidBpmnId();
      final var largeVariable = "foo".repeat(524288);
      builder.serviceTask(name, s -> s.zeebeJobType(name).zeebeInput(largeVariable, "bar"));

      return builder.endEvent().done();
    }
  }

  @Nested
  final class DecisionStateTest {
    @RegressionTest("https://github.com/camunda/zeebe/issues/14975")
    void shouldNotOomOnDecisionStateMigration() {
      // given
      generateLargeDecisionState();

      // when
      reconfigureContainerWithNewerVersion();

      // then - will fail to start with an OOMKilled error if it crashes
      assertThatCode(container::start).doesNotThrowAnyException();
    }

    private void generateLargeDecisionState() {
      generateDecisionState();
      forceSnapshot();
    }

    private void generateDecisionState() {
      final var decisionCount = 300;
      try (final var client =
          ZeebeClient.newClientBuilder()
              .usePlaintext()
              .gatewayAddress(container.getExternalGatewayAddress())
              .build()) {
        for (int i = 0; i < decisionCount / 3; i++) {
          CompletableFuture.allOf(
                  deployLargeDecision(client),
                  deployLargeDecision(client),
                  deployLargeDecision(client))
              .join();
        }
      }
    }

    private CompletableFuture<?> deployLargeDecision(final ZeebeClient client) {
      return (CompletableFuture<?>)
          client
              .newDeployResourceCommand()
              .addResourceBytes(generateDecision(), Strings.newRandomValidBpmnId() + ".dmn")
              .send();
    }

    private byte[] generateDecision() {
      final var largeVariable = "foo".repeat(524288);
      try (final var input =
          ClassLoader.getSystemClassLoader().getResourceAsStream("decision.dmn")) {
        final var resource = new String(Objects.requireNonNull(input).readAllBytes());
        return resource.replace("<= REPLACE ME =>", largeVariable).getBytes(StandardCharsets.UTF_8);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
