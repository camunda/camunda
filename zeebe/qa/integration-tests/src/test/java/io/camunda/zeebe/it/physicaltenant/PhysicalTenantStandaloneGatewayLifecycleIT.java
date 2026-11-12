/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.GatewayPhysicalTenantFollower;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status.Code;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Three brokers without embedded gateways and one standalone gateway: physical tenants are created,
 * changed and removed through the brokers' {@code physicalTenants} actuator, and the standalone
 * gateway serves them over gRPC without a restart, while the {@code default} tenant keeps serving
 * through it.
 */
@ZeebeIntegration
final class PhysicalTenantStandaloneGatewayLifecycleIT {

  private static final Logger LOG =
      LoggerFactory.getLogger(PhysicalTenantStandaloneGatewayLifecycleIT.class);

  private static final String BOOT_TENANT = "boottenant";
  private static final String AUTHORIZATIONS_ENABLED = "security.authorizations.enabled";
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
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withBrokerConfig(
              broker ->
                  TENANTS
                      .configure(broker.withUnauthenticatedAccess())
                      .withProperty("physical-tenant-rollout.restart-keys", AUTHORIZATIONS_ENABLED))
          .build();

  private CamundaClient defaultClient;
  private PublishLoad defaultLoad;
  private Thread defaultLoadThread;

  @BeforeEach
  void startLoadOnTheDefaultTenant() {
    defaultClient =
        TENANTS
            .newClientBuilder(gateway(), PhysicalTenantsITHelper.DEFAULT_TENANT_ID)
            .preferRestOverGrpc(false)
            .build();
    Awaitility.await("the default tenant serves through the standalone gateway")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .until(() -> publish(defaultClient));
    defaultLoad = new PublishLoad(defaultClient);
    defaultLoadThread = Thread.ofVirtual().start(defaultLoad);
    Awaitility.await("the load on the default tenant runs")
        .until(() -> defaultLoad.succeeded() > 5);
  }

  @AfterEach
  void stopLoadAndAssertTheDefaultTenantNeverFailed() throws InterruptedException {
    defaultLoad.stop();
    defaultLoadThread.join();
    defaultClient.close();
    LOG.info(
        "Load on the default tenant through the standalone gateway: {} accepted, {} failed,"
            + " longest gap {} ms",
        defaultLoad.succeeded(),
        defaultLoad.failed(),
        defaultLoad.longestGap().toMillis());
    assertThat(defaultLoad.failures()).isEmpty();
  }

  @Test
  void shouldServeATenantCreatedAtRuntimeThroughTheStandaloneGateway() {
    // given
    final var tenantId = uniqueTenantId();
    final var started = System.nanoTime();

    // when
    final var response = send("POST", broker(1), tenantId, tenantConfiguration("Created"));

    // then
    assertThat(response.statusCode()).isEqualTo(202);
    awaitServedByGateway(tenantId, 1);
    LOG.info(
        "The standalone gateway served '{}' {} ms after its creation was accepted",
        tenantId,
        Duration.ofNanos(System.nanoTime() - started).toMillis());
    assertThat(
            gateway()
                .bean(BrokerTopologyManager.class)
                .getClusterConfiguration()
                .partitionGroup(tenantId)
                .tenantConfig())
        .hasValueSatisfying(reference -> assertThat(reference.version()).isEqualTo(1));
    assertThat(gateway().bean(DefaultPhysicalTenantRegistry.class).find(tenantId)).isPresent();
    try (final var client = tenantClient(tenantId)) {
      deployProcessWithJob(client, tenantId);
      assertThat(completeInstanceThroughAJobStream(client, tenantId)).isPositive();
      assertThat(completeInstanceThroughJobActivation(client, tenantId)).isPositive();
    }
  }

  @Test
  void shouldServeAFileTenantThatOnlyTheBrokersDeclare() {
    // given
    awaitServedByGateway(BOOT_TENANT, 1);

    // when
    try (final var client = tenantClient(BOOT_TENANT)) {
      deployProcessWithJob(client, BOOT_TENANT);

      // then
      assertThat(completeInstanceThroughAJobStream(client, BOOT_TENANT)).isPositive();
    }
  }

  @Test
  void shouldRejectARemovedTenantAsUnknownOnTheStandaloneGateway() {
    // given
    final var tenantId = uniqueTenantId();
    send("POST", broker(0), tenantId, tenantConfiguration("To remove"));
    awaitRolledOut(tenantId, 1);
    awaitServedByGateway(tenantId, 1);
    try (final var client = tenantClient(tenantId)) {
      deployProcessWithJob(client, tenantId);

      // when
      final var response = send("DELETE", broker(2), tenantId, null);

      // then
      assertThat(response.statusCode()).isEqualTo(202);
      Awaitility.await("the standalone gateway rejects the removed tenant as unknown")
          .atMost(TIMEOUT)
          .untilAsserted(
              () ->
                  assertThatThrownBy(() -> publish(client))
                      .isInstanceOf(ClientStatusException.class)
                      .extracting(e -> ((ClientStatusException) e).getStatusCode())
                      .isEqualTo(Code.NOT_FOUND));
      assertThat(gateway().bean(DefaultPhysicalTenantRegistry.class).find(tenantId)).isEmpty();
      assertThat(gateway().bean(Gateway.class).physicalTenants().known()).doesNotContain(tenantId);
    }
  }

  @Test
  void shouldApplyAChangedTenantsGrpcSecurityOnTheStandaloneGateway() {
    // given
    final var tenantId = uniqueTenantId();
    send("POST", broker(0), tenantId, tenantConfiguration("Before"));
    awaitRolledOut(tenantId, 1);
    awaitServedByGateway(tenantId, 1);
    final var gatewayTenants = gateway().bean(Gateway.class).physicalTenants();
    assertThat(gatewayTenants.securityConfig(tenantId).isAuthorizationsEnabled()).isFalse();
    assertThat(gatewayTenants.authorizationConverter(tenantId).shouldIncludeAuthorizationClaims())
        .isFalse();
    final var changed = tenantConfiguration("After");
    changed.put(AUTHORIZATIONS_ENABLED, "true");

    // when
    final var response = send("PUT", broker(1), tenantId, changed);

    // then
    assertThat(response.statusCode()).isEqualTo(202);
    awaitServedByGateway(tenantId, 2);
    assertThat(gatewayTenants.securityConfig(tenantId).isAuthorizationsEnabled()).isTrue();
    assertThat(gatewayTenants.authorizationConverter(tenantId).shouldIncludeAuthorizationClaims())
        .isTrue();
    assertThat(gatewayTenants.securityConfig(PhysicalTenantsITHelper.DEFAULT_TENANT_ID))
        .extracting(EngineSecurityConfig::isAuthorizationsEnabled)
        .isEqualTo(false);
    assertThat(gateway().bean(DefaultPhysicalTenantRegistry.class).find(tenantId))
        .hasValueSatisfying(tenant -> assertThat(tenant.name()).isEqualTo("After"));
    awaitRolledOut(tenantId, 2);
  }

  private static void awaitServedByGateway(final String tenantId, final long version) {
    Awaitility.await("the standalone gateway serves version %d of %s".formatted(version, tenantId))
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(
                        gateway()
                            .bean(GatewayPhysicalTenantFollower.class)
                            .followedVersion(tenantId))
                    .contains(version));
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

  private static void deployProcessWithJob(final CamundaClient client, final String tenantId) {
    Awaitility.await("deploy a process of " + tenantId + " through the standalone gateway")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .until(
            () ->
                client
                        .newDeployResourceCommand()
                        .addProcessModel(
                            Bpmn.createExecutableProcess(processId(tenantId))
                                .startEvent()
                                .serviceTask("task", task -> task.zeebeJobType(jobType(tenantId)))
                                .endEvent()
                                .done(),
                            processId(tenantId) + ".bpmn")
                        .send()
                        .join(10, TimeUnit.SECONDS)
                    != null);
  }

  private static long completeInstanceThroughAJobStream(
      final CamundaClient client, final String tenantId) {
    final var streamed = new CopyOnWriteArrayList<ActivatedJob>();
    final var stream =
        client
            .newStreamJobsCommand()
            .jobType(jobType(tenantId))
            .consumer(
                job -> {
                  streamed.add(job);
                  client.newCompleteCommand(job.getKey()).send();
                })
            .timeout(Duration.ofSeconds(30))
            .send();
    try {
      Awaitility.await("the job stream of " + tenantId + " reaches a broker")
          .atMost(TIMEOUT)
          .until(() -> streamRegisteredOnEveryBroker(tenantId));
      final var instance =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId(processId(tenantId))
              .latestVersion()
              .withResult()
              .send()
              .join(30, TimeUnit.SECONDS);
      assertThat(streamed)
          .extracting(ActivatedJob::getProcessInstanceKey)
          .contains(instance.getProcessInstanceKey());
      return instance.getProcessInstanceKey();
    } finally {
      stream.cancel(true);
    }
  }

  private static long completeInstanceThroughJobActivation(
      final CamundaClient client, final String tenantId) {
    final var instance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId(tenantId))
            .latestVersion()
            .send()
            .join(30, TimeUnit.SECONDS);
    Awaitility.await("activate and complete the job of " + tenantId)
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var jobs =
                  client
                      .newActivateJobsCommand()
                      .jobType(jobType(tenantId))
                      .maxJobsToActivate(10)
                      .send()
                      .join(10, TimeUnit.SECONDS)
                      .getJobs();
              jobs.forEach(job -> client.newCompleteCommand(job.getKey()).send().join());
              assertThat(jobs)
                  .extracting(ActivatedJob::getProcessInstanceKey)
                  .contains(instance.getProcessInstanceKey());
            });
    return instance.getProcessInstanceKey();
  }

  private static boolean streamRegisteredOnEveryBroker(final String tenantId) {
    return CLUSTER.brokers().values().stream()
        .allMatch(
            broker ->
                JobStreamActuator.of(broker).listRemote(tenantId).stream()
                    .anyMatch(stream -> stream.jobType().equals(jobType(tenantId))));
  }

  private static boolean publish(final CamundaClient client) {
    client
        .newPublishMessageCommand()
        .messageName("measure")
        .correlationKey("key")
        .send()
        .join(5, TimeUnit.SECONDS);
    return true;
  }

  private static CamundaClient tenantClient(final String tenantId) {
    return TENANTS.newClientBuilder(gateway(), tenantId).preferRestOverGrpc(false).build();
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

  private static JsonNode status(final String tenantId) {
    final var response = send("GET", broker(0), tenantId, null);
    assertThat(response.statusCode()).isEqualTo(200);
    try {
      return JSON.readTree(response.body());
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static TestStandaloneGateway gateway() {
    return CLUSTER.gateways().values().iterator().next();
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

  private static String processId(final String tenantId) {
    return "process-" + tenantId;
  }

  private static String jobType(final String tenantId) {
    return "work-" + tenantId;
  }

  private static String uniqueTenantId() {
    return "gw" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  private static final class PublishLoad implements Runnable {
    private final CamundaClient client;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger succeeded = new AtomicInteger();
    private final CopyOnWriteArrayList<String> failures = new CopyOnWriteArrayList<>();
    private volatile long longestGapNanos;

    private PublishLoad(final CamundaClient client) {
      this.client = client;
    }

    @Override
    public void run() {
      var lastSuccess = System.nanoTime();
      while (running.get()) {
        try {
          publish(client);
          final var now = System.nanoTime();
          if (succeeded.getAndIncrement() > 0) {
            longestGapNanos = Math.max(longestGapNanos, now - lastSuccess);
          }
          lastSuccess = now;
        } catch (final RuntimeException e) {
          failures.add(e.toString());
        }
      }
    }

    private void stop() {
      running.set(false);
    }

    private int succeeded() {
      return succeeded.get();
    }

    private int failed() {
      return failures.size();
    }

    private List<String> failures() {
      return failures;
    }

    private Duration longestGap() {
      return Duration.ofNanos(longestGapNanos);
    }
  }
}
