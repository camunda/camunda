/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * A cluster whose configuration has no {@code camunda.physical-tenants} section at all, only the
 * implicit {@code default} tenant, which is the root configuration itself. Its file then gains a
 * {@code default} section, and loses it again, without a restart.
 *
 * <p>The brokers run without the recording exporter: with a generic exporter in the root catalog, a
 * declared {@code default} must list {@code data.exporters-assigned}, a key that cannot change at
 * runtime, so the section could not be added without a restart.
 */
@ZeebeIntegration
final class PhysicalTenantDefaultWithoutSectionIT {

  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String DEFAULT = "default";
  private static final String ROOT_ONLY =
      """
      "camunda.data.snapshot-period": "5m"
      """;

  private static final Path FILES = createDirectory();

  @TestZeebe(purgeAfterEach = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withBrokersCount(3)
          .withReplicationFactor(3)
          .withPartitionsCount(1)
          .withBrokerConfig(
              broker ->
                  broker
                      .withUnauthenticatedAccess()
                      .withRecordingExporter(false)
                      .withAdditionalConfigLocation(file(broker.nodeId().id()))
                      .withProperty("physical-tenant-rollout.file-watch.interval", "200ms")
                      .withProperty("physical-tenant-rollout.file-watch.quiet-period", "1s"))
          .build();

  static {
    writeFiles(ROOT_ONLY);
  }

  @Test
  void shouldRollOutADefaultSectionAddedToTheFileAndTakenOutAgain() {
    // given
    final var version = awaitRolledOutAtAnyVersion();
    assertThat(status().get("owner").asText()).isEqualTo("FILE");
    for (final var broker : CLUSTER.brokers().values()) {
      assertThat(defaultConfiguration(broker)).isSameAs(broker.bean(Camunda.class));
    }
    runProcess("before");

    // when
    writeFiles(ROOT_ONLY + "\"camunda.physical-tenants.default.data.snapshot-period\": \"7m\"\n");

    // then
    awaitRolledOut(version + 1);
    for (final var broker : CLUSTER.brokers().values()) {
      assertThat(defaultConfiguration(broker)).isNotSameAs(broker.bean(Camunda.class));
      assertThat(defaultConfiguration(broker).getData().getSnapshotPeriod())
          .isEqualTo(Duration.ofMinutes(7));
    }
    runProcess("with-section");

    // when
    writeFiles(ROOT_ONLY);

    // then
    awaitRolledOut(version + 2);
    for (final var broker : CLUSTER.brokers().values()) {
      assertThat(defaultConfiguration(broker)).isSameAs(broker.bean(Camunda.class));
      assertThat(defaultConfiguration(broker).getData().getSnapshotPeriod())
          .isEqualTo(Duration.ofMinutes(5));
    }
    runProcess("without-section");
  }

  private static Camunda defaultConfiguration(final TestStandaloneBroker broker) {
    return broker.bean(PhysicalTenantLifecycleService.class).configuration(DEFAULT).orElseThrow();
  }

  private static void runProcess(final String processId) {
    for (final var broker : CLUSTER.brokers().values()) {
      try (final CamundaClient client =
          broker.newClientBuilder().preferRestOverGrpc(false).build()) {
        Awaitility.await("deploy and run " + processId + " through " + broker.nodeId())
            .atMost(TIMEOUT)
            .ignoreExceptions()
            .until(
                () -> {
                  client
                      .newDeployResourceCommand()
                      .addProcessModel(
                          Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                          processId + ".bpmn")
                      .send()
                      .join(10, TimeUnit.SECONDS);
                  return client
                          .newCreateInstanceCommand()
                          .bpmnProcessId(processId)
                          .latestVersion()
                          .withResult()
                          .send()
                          .join(10, TimeUnit.SECONDS)
                          .getProcessInstanceKey()
                      > 0;
                });
      }
    }
  }

  private static void awaitRolledOut(final long version) {
    Awaitility.await(
            "version %d of the default tenant is applied by every broker".formatted(version))
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var status = status();
              assertThat(status.get("version").asLong()).isEqualTo(version);
              assertThat(status.get("rolledOut").asBoolean()).isTrue();
              assertThat(status.get("changing").asBoolean()).isFalse();
            });
  }

  private static long awaitRolledOutAtAnyVersion() {
    Awaitility.await("the default tenant is registered and rolled out")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(status().get("rolledOut").asBoolean()).isTrue());
    return status().get("version").asLong();
  }

  private static JsonNode status() {
    try (final var http = HttpClient.newHttpClient()) {
      final var response =
          http.send(
              HttpRequest.newBuilder(broker(0).actuatorUri("physicalTenants", DEFAULT)).build(),
              BodyHandlers.ofString());
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
      return JSON.readTree(response.body());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void writeFiles(final String contents) {
    for (int nodeId = 0; nodeId < 3; nodeId++) {
      try {
        final var file = file(String.valueOf(nodeId));
        Files.writeString(file, contents);
        file.toFile().deleteOnExit();
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private static Path file(final String nodeId) {
    return FILES.resolve("broker-" + nodeId + ".yaml");
  }

  private static Path createDirectory() {
    try {
      final var directory = Files.createTempDirectory("pt-default-without-section");
      directory.toFile().deleteOnExit();
      return directory;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static TestStandaloneBroker broker(final int nodeId) {
    return CLUSTER.brokers().get(MemberId.from(String.valueOf(nodeId)));
  }
}
