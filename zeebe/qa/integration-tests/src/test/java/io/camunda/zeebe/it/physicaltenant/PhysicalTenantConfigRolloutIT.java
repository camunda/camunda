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
import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Creates, updates and removes physical tenants on a cluster of three brokers through the {@code
 * physicalTenants} actuator endpoint of any broker, and follows the rollout of each change to every
 * member through dynamic-config, without restarting a node.
 */
@ZeebeIntegration
final class PhysicalTenantConfigRolloutIT {

  private static final String BOOT_TENANT = "boottenant";
  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(BOOT_TENANT, Storage.none())
          .build();

  @TestZeebe(purgeAfterEach = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withBrokersCount(3)
          .withReplicationFactor(3)
          .withPartitionsCount(1)
          .withBrokerConfig(broker -> TENANTS.configure(broker.withUnauthenticatedAccess()))
          .build();

  @Test
  void shouldServeATenantCreatedThroughABrokerThatIsNotTheCoordinatorOnEveryBroker() {
    // given
    final var tenantId = uniqueTenantId();

    // when
    final var response = send("POST", broker(2), tenantId, tenantConfiguration("Created"));

    // then
    assertThat(response.statusCode()).isEqualTo(202);
    awaitRolledOut(tenantId, 1);
    for (final var broker : CLUSTER.brokers().values()) {
      try (final var client =
          TENANTS.newClientBuilder(broker, tenantId).preferRestOverGrpc(false).build()) {
        assertThat(runProcess(client, "process-" + tenantId)).isPositive();
      }
    }
    assertThat(status(tenantId).get("owner").asText()).isEqualTo("API");
  }

  @Test
  void shouldRestartOnlyTheUpdatedTenantOnEveryBroker() {
    // given
    final var tenantId = createRolledOut(uniqueTenantId());
    final Map<MemberId, Object> bootTenantContexts = new HashMap<>();
    CLUSTER
        .brokers()
        .forEach((id, broker) -> bootTenantContexts.put(id, moduleContext(broker, BOOT_TENANT)));
    final var updated = tenantConfiguration("Updated");
    updated.put("data.snapshot-period", "7m");

    // when
    final var response = send("PUT", broker(1), tenantId, updated);

    // then
    assertThat(response.statusCode()).isEqualTo(202);
    awaitRolledOut(tenantId, 2);
    CLUSTER
        .brokers()
        .forEach(
            (id, broker) -> {
              assertThat(
                      broker
                          .bean(PhysicalTenantLifecycleService.class)
                          .configuration(tenantId)
                          .orElseThrow()
                          .getData()
                          .getSnapshotPeriod())
                  .isEqualTo(Duration.ofMinutes(7));
              assertThat(moduleContext(broker, BOOT_TENANT)).isSameAs(bootTenantContexts.get(id));
              assertThat(appliedVersionMetric(broker, tenantId)).isEqualTo(2);
            });
  }

  @Test
  void shouldRejectAChangeOfAPropertyThatCannotChangeAndKeepTheOldState() {
    // given
    final var tenantId = createRolledOut(uniqueTenantId());
    final var changed = tenantConfiguration("Created");
    changed.put("data.secondary-storage.type", "rdbms");

    // when
    final var response = send("PUT", broker(0), tenantId, changed);

    // then
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("data.secondary-storage.type", "storage-location");
    assertThat(status(tenantId).get("version").asLong()).isEqualTo(1);
  }

  @Test
  void shouldRejectASecondChangeWhileOneRollsOut() {
    // given
    final var first = uniqueTenantId();
    final var second = uniqueTenantId();
    send("POST", broker(0), first, tenantConfiguration("First"));

    // when
    final var response = send("POST", broker(1), second, tenantConfiguration("Second"));

    // then
    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(response.body()).contains("IN_PROGRESS");
    awaitRolledOut(first, 1);
  }

  @Test
  void shouldApplyAChangeOnABrokerThatWasDownOnceItReturns() {
    // given
    final var tenantId = createRolledOut(uniqueTenantId());
    final var stopped = CLUSTER.brokers().get(MemberId.from("2"));
    stopped.stop();
    final var updated = tenantConfiguration("While a broker is down");
    updated.put("data.snapshot-period", "9m");

    // when
    final var response = send("PUT", broker(0), tenantId, updated);
    Awaitility.await("the running brokers apply the change")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var applied = status(tenantId).get("appliedVersions");
              assertThat(applied.get("0").asLong()).isEqualTo(2);
              assertThat(applied.get("1").asLong()).isEqualTo(2);
              assertThat(applied.get("2").asLong()).isEqualTo(1);
            });
    awaitPortReleased(stopped.mappedPort(TestZeebePort.CLUSTER));
    stopped.start();

    // then
    assertThat(response.statusCode()).isEqualTo(202);
    awaitRolledOut(tenantId, 2);
    assertThat(
            stopped
                .bean(PhysicalTenantLifecycleService.class)
                .configuration(tenantId)
                .orElseThrow()
                .getData()
                .getSnapshotPeriod())
        .isEqualTo(Duration.ofMinutes(9));
    try (final var client =
        TENANTS.newClientBuilder(stopped, tenantId).preferRestOverGrpc(false).build()) {
      assertThat(runProcess(client, "process-" + tenantId)).isPositive();
    }
  }

  @Test
  void shouldRejectAnApiChangeOfATenantOwnedByTheFile() {
    // given
    Awaitility.await("the coordinator registers the boot tenant from its file")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> assertThat(status(BOOT_TENANT).get("owner").asText()).isEqualTo("FILE"));
    awaitRolledOut(BOOT_TENANT, 1);

    // when
    final var response = send("PUT", broker(1), BOOT_TENANT, tenantConfiguration("Hijacked"));

    // then
    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(response.body()).contains("owned by FILE");
  }

  @Test
  void shouldRemoveATenantKeepItsDataAndReserveItsId() {
    // given
    final var tenantId = createRolledOut(uniqueTenantId());

    // when
    final var response = send("DELETE", broker(1), tenantId, null);

    // then
    assertThat(response.statusCode()).isEqualTo(202);
    Awaitility.await("the tenant is removed on every broker")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(status(tenantId).get("state").asText()).isEqualTo("REMOVED");
              assertThat(CLUSTER.brokers().values())
                  .allSatisfy(
                      broker ->
                          assertThat(
                                  broker.bean(DefaultPhysicalTenantRegistry.class).find(tenantId))
                              .isEmpty());
            });
    assertThat(CLUSTER.brokers().values())
        .allSatisfy(
            broker -> assertThat(Files.exists(tenantDataDirectory(broker, tenantId))).isTrue());
    final var recreate = send("POST", broker(0), tenantId, tenantConfiguration("Again"));
    assertThat(recreate.statusCode()).isEqualTo(400);
    assertThat(recreate.body()).contains("stays reserved");
  }

  private String createRolledOut(final String tenantId) {
    Awaitility.await("the cluster accepts the creation of " + tenantId)
        .atMost(TIMEOUT)
        .until(
            () ->
                send("POST", broker(0), tenantId, tenantConfiguration("Created")).statusCode()
                    == 202);
    awaitRolledOut(tenantId, 1);
    return tenantId;
  }

  private static void awaitRolledOut(final String tenantId, final long version) {
    Awaitility.await("version %d of %s is applied by every broker".formatted(version, tenantId))
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var status = status(tenantId);
              assertThat(status.get("version").asLong()).isEqualTo(version);
              assertThat(status.get("rolledOut").asBoolean()).isTrue();
              assertThat(status.get("changing").asBoolean()).isFalse();
            });
  }

  private static JsonNode status(final String tenantId) {
    final var response = send("GET", broker(0), tenantId, null);
    assertThat(response.statusCode()).isEqualTo(200);
    try {
      return JSON.readTree(response.body());
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static long runProcess(final CamundaClient client, final String processId) {
    Awaitility.await("deploy " + processId)
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .until(
            () ->
                client
                        .newDeployResourceCommand()
                        .addProcessModel(
                            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                            processId + ".bpmn")
                        .send()
                        .join(10, TimeUnit.SECONDS)
                    != null);
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .withResult()
        .send()
        .join(30, TimeUnit.SECONDS)
        .getProcessInstanceKey();
  }

  private static Map<String, String> tenantConfiguration(final String name) {
    final Map<String, String> configuration = new HashMap<>();
    configuration.put("name", name);
    configuration.put("data.secondary-storage.type", "none");
    configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
    if (broker(0)
        .unifiedConfig()
        .getData()
        .getExporters()
        .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID)) {
      configuration.put("data.exporters-assigned[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
    }
    return configuration;
  }

  private static Object moduleContext(final TestStandaloneBroker broker, final String tenantId) {
    return broker
        .bean(Broker.class)
        .getBrokerContext()
        .getPhysicalTenantModules()
        .context(tenantId)
        .orElseThrow();
  }

  private static double appliedVersionMetric(
      final TestStandaloneBroker broker, final String tenantId) {
    return broker
        .bean(MeterRegistry.class)
        .get("zeebe.physical.tenant.config.applied.version")
        .tag("physicalTenant", tenantId)
        .gauge()
        .value();
  }

  private static Path tenantDataDirectory(
      final TestStandaloneBroker broker, final String tenantId) {
    return Path.of(
            broker
                .bean(Broker.class)
                .getSystemContext()
                .getBrokerConfiguration()
                .getData()
                .getDirectory())
        .resolve(tenantId)
        .resolve("partitions");
  }

  private static void awaitPortReleased(final int port) {
    Awaitility.await("port %d is released".formatted(port))
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .until(
            () -> {
              try (final var socket = new ServerSocket(port)) {
                return socket.isBound();
              }
            });
  }

  private static TestStandaloneBroker broker(final int nodeId) {
    return CLUSTER.brokers().get(MemberId.from(String.valueOf(nodeId)));
  }

  private static HttpResponse<String> send(
      final String method,
      final TestStandaloneBroker broker,
      final String tenantId,
      final Map<String, String> body) {
    try (final var http = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder(broker.actuatorUri("physicalTenants", tenantId))
              .header("Content-Type", "application/json")
              .method(
                  method,
                  body == null
                      ? BodyPublishers.noBody()
                      : BodyPublishers.ofString(JSON.writeValueAsString(body)))
              .build();
      return http.send(request, BodyHandlers.ofString());
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String uniqueTenantId() {
    return "rt" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }
}
