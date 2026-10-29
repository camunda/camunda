/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenantHealth;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.cluster.PhysicalTenantRegistry;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.PhysicalTenantChangeException;
import io.camunda.zeebe.broker.PhysicalTenantChangeException.Reason;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.broker.system.PhysicalTenantModules;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status.Code;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class PhysicalTenantRuntimeLifecycleIT {

  private static final String BOOT_TENANT = "boottenant";
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(BOOT_TENANT, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();
  private final HttpClient http = HttpClient.newHttpClient();

  @AfterEach
  void tearDown() {
    CloseHelper.quietCloseAll(closeables);
    http.close();
  }

  @Test
  void shouldServeATenantAddedAtRuntime() {
    // given
    final var tenantId = uniqueTenantId();
    final var client = grpcClient(tenantId);
    final var jobType = "type-" + tenantId;

    // when
    lifecycle().add(tenantId, tenantConfiguration("Added at runtime"));

    // then
    final var stream =
        client
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(job -> client.newCompleteCommand(job).send())
            .send();
    closeables.add(() -> stream.cancel(true));
    deployServiceTaskProcess(client, "process-" + tenantId, jobType);
    final var result =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process-" + tenantId)
            .latestVersion()
            .withResult()
            .send()
            .join(30, TimeUnit.SECONDS);
    assertThat(result.getProcessInstanceKey()).isPositive();
    awaitHealth(tenantId, State.HEALTHY);
  }

  @Test
  void shouldRestartOnlyTheReplacedTenantWhileAnotherServes() throws Exception {
    // given
    final var tenantId = uniqueTenantId();
    final var added = lifecycle().add(tenantId, tenantConfiguration("Before"));
    awaitHealth(tenantId, State.HEALTHY);
    final var transitions = recordHealthTransitions(tenantId);
    final var defaultClient = grpcClient(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
    final var load = new ContinuousLoad(defaultClient);
    final var livenessFailures = new AtomicInteger();
    final var probing = new AtomicBoolean(true);
    final var prober =
        Thread.ofVirtual()
            .start(
                () -> {
                  while (probing.get()) {
                    if (status("/actuator/health/liveness") != 200) {
                      livenessFailures.incrementAndGet();
                    }
                  }
                });
    load.start();
    Awaitility.await().until(() -> load.succeeded.get() > 10);

    // when
    final var changed = new HashMap<>(tenantConfiguration("After"));
    changed.put("data.snapshot-period", "7m");
    final var replaced = lifecycle().replace(tenantId, changed);
    awaitHealth(tenantId, State.HEALTHY);
    final var afterRestart = load.succeeded.get();
    Awaitility.await().until(() -> load.succeeded.get() > afterRestart + 10);
    load.stop();
    probing.set(false);
    prober.join();

    // then
    assertThat(load.failed.get()).isZero();
    assertThat(livenessFailures.get()).isZero();
    assertThat(transitions).containsSubsequence(State.RESTARTING, State.HEALTHY);
    assertThat(replaced.incarnationId()).isEqualTo(added.incarnationId());
    assertThat(replaced.name()).isEqualTo("After");
    assertThat(
            modulesOfBroker()
                .context(tenantId)
                .orElseThrow()
                .config()
                .getData()
                .getSnapshotPeriod())
        .isEqualTo(Duration.ofMinutes(7));
    publishMessage(grpcClient(tenantId));
  }

  @Test
  void shouldRejectAnInvalidChangeAndKeepTheOldState() {
    // given
    final var tenantId = uniqueTenantId();
    lifecycle().add(tenantId, tenantConfiguration("Valid"));
    awaitHealth(tenantId, State.HEALTHY);
    final var invalid = new HashMap<>(tenantConfiguration("Invalid"));
    invalid.put("data.snapshot-period", "1s");

    // when
    final var thrown = catchThrowable(() -> lifecycle().replace(tenantId, invalid));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            PhysicalTenantChangeException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.INVALID))
        .hasMessageContaining("Snapshot period");
    assertThat(registry().health(tenantId)).contains(PhysicalTenantHealth.of(State.HEALTHY));
    assertThat(registry().find(tenantId).orElseThrow().name()).isEqualTo("Valid");
    assertThat(
            modulesOfBroker()
                .context(tenantId)
                .orElseThrow()
                .config()
                .getData()
                .getSnapshotPeriod())
        .isNotEqualTo(Duration.ofSeconds(1));
    publishMessage(grpcClient(tenantId));
  }

  @Test
  void shouldRejectAnInvalidAddAndLeaveNoTrace() {
    // given
    final var tenantId = uniqueTenantId();
    final var invalid = new HashMap<>(tenantConfiguration("Invalid"));
    invalid.put("cluster.size", 3);

    // when
    final var thrown = catchThrowable(() -> lifecycle().add(tenantId, invalid));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            PhysicalTenantChangeException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.INVALID));
    assertThat(registry().known()).doesNotContain(tenantId);
    assertThat(modulesOfBroker().running()).doesNotContain(tenantId);
  }

  @Test
  void shouldRejectRequestsForARemovedTenantAndKeepItsData() {
    // given
    final var tenantId = uniqueTenantId();
    lifecycle().add(tenantId, tenantConfiguration("Removed"));
    awaitHealth(tenantId, State.HEALTHY);
    final var client = grpcClient(tenantId);
    publishMessage(client);

    // when
    lifecycle().remove(tenantId);

    // then
    final var rejected = catchThrowable(() -> publishMessage(client));
    assertThat(rejected)
        .isInstanceOfSatisfying(
            ClientStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(Code.NOT_FOUND));
    assertThat(registry().health(tenantId)).contains(PhysicalTenantHealth.of(State.REMOVED));
    assertThat(status("/actuator/health/physicalTenants/" + tenantId)).isEqualTo(404);
    assertThat(Files.isDirectory(dataDirectory().resolve(tenantId).resolve("partitions"))).isTrue();
    assertThat(catchThrowable(() -> lifecycle().add(tenantId, tenantConfiguration("Re-added"))))
        .isInstanceOfSatisfying(
            PhysicalTenantChangeException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.CONFLICT));
  }

  @Test
  void shouldForgetTheLastRestartOfARemovedTenant() {
    // given
    final var tenantId = uniqueTenantId();
    lifecycle().add(tenantId, tenantConfiguration("Restarted"));
    awaitHealth(tenantId, State.HEALTHY);
    lifecycle().replace(tenantId, tenantConfiguration("Restarted again"));
    awaitHealth(tenantId, State.HEALTHY);
    assertThat(lifecycle().lastRestart(tenantId)).isPresent();

    // when
    lifecycle().remove(tenantId);

    // then
    assertThat(lifecycle().lastRestart(tenantId)).isEmpty();
  }

  @Test
  void shouldRejectRestRequestsForATenantWithoutSecurityChain() {
    // given
    final var tenantId = uniqueTenantId();
    lifecycle().add(tenantId, tenantConfiguration("No chain"));
    awaitHealth(tenantId, State.HEALTHY);

    // when
    final var addedTenant = restStatus("/physical-tenants/" + tenantId + "/v2/topology");
    final var bootTenant = restStatus("/physical-tenants/" + BOOT_TENANT + "/v2/topology");

    // then
    assertThat(addedTenant).isEqualTo(404);
    assertThat(bootTenant).isEqualTo(200);
  }

  @Test
  void shouldRejectRestRequestsForARemovedTenantThatStillHasASecurityChain() {
    // given
    final var path = "/physical-tenants/" + BOOT_TENANT + "/v2/topology";
    assertThat(restStatus(path)).isEqualTo(200);

    // when
    lifecycle().remove(BOOT_TENANT);

    // then
    assertThat(restStatus(path)).isEqualTo(404);
  }

  @Test
  void shouldExposeTenantHealthThroughTheActuator() {
    // given
    final var tenantId = uniqueTenantId();
    lifecycle().add(tenantId, tenantConfiguration("Observed"));
    awaitHealth(tenantId, State.HEALTHY);
    final var path = "/actuator/health/physicalTenants/" + tenantId;
    assertThat(body(path)).contains("\"status\":\"UP\"").contains("\"name\":\"Observed\"");

    // when
    registry().reportHealth(tenantId, new PhysicalTenantHealth(State.DEGRADED, "stale secret"));

    // then
    assertThat(body(path)).contains("\"status\":\"DEGRADED\"").contains("stale secret");
    assertThat(status("/actuator/health/liveness")).isEqualTo(200);
    assertThat(status("/actuator/health/readiness")).isEqualTo(200);
  }

  private void awaitHealth(final String tenantId, final State state) {
    Awaitility.await("physical tenant '%s' is %s".formatted(tenantId, state))
        .atMost(TIMEOUT)
        .until(
            () ->
                registry().health(tenantId).map(PhysicalTenantHealth::state).orElse(null) == state);
  }

  private List<State> recordHealthTransitions(final String tenantId) {
    final List<State> transitions = new CopyOnWriteArrayList<>();
    final var listener =
        new PhysicalTenantRegistry.Listener() {
          @Override
          public void onHealthChanged(
              final String physicalTenantId, final PhysicalTenantHealth health) {
            if (physicalTenantId.equals(tenantId)) {
              transitions.add(health.state());
            }
          }
        };
    registry().addListener(listener);
    closeables.add(() -> registry().removeListener(listener));
    return transitions;
  }

  private Map<String, Object> tenantConfiguration(final String name) {
    final Map<String, Object> configuration = new HashMap<>();
    configuration.put("name", name);
    configuration.put("data.secondary-storage.type", "none");
    configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
    if (broker
        .unifiedConfig()
        .getData()
        .getExporters()
        .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID)) {
      configuration.put("data.exporters-assigned[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
    }
    return configuration;
  }

  private CamundaClient grpcClient(final String tenantId) {
    final var client = TENANTS.newClientBuilder(broker, tenantId).preferRestOverGrpc(false).build();
    closeables.add(client);
    return client;
  }

  private static void deployServiceTaskProcess(
      final CamundaClient client, final String processId, final String jobType) {
    Awaitility.await("deploy to " + processId)
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .until(
            () ->
                client
                        .newDeployResourceCommand()
                        .addProcessModel(
                            Bpmn.createExecutableProcess(processId)
                                .startEvent()
                                .serviceTask("task", t -> t.zeebeJobType(jobType))
                                .endEvent()
                                .done(),
                            processId + ".bpmn")
                        .send()
                        .join(10, TimeUnit.SECONDS)
                    != null);
  }

  private static void publishMessage(final CamundaClient client) {
    client
        .newPublishMessageCommand()
        .messageName("lifecycle")
        .correlationKey(UUID.randomUUID().toString())
        .send()
        .join(10, TimeUnit.SECONDS);
  }

  private PhysicalTenantLifecycleService lifecycle() {
    return broker.bean(PhysicalTenantLifecycleService.class);
  }

  private DefaultPhysicalTenantRegistry registry() {
    return broker.bean(DefaultPhysicalTenantRegistry.class);
  }

  private PhysicalTenantModules modulesOfBroker() {
    return broker.bean(Broker.class).getBrokerContext().getPhysicalTenantModules();
  }

  private Path dataDirectory() {
    return Path.of(
        broker
            .bean(Broker.class)
            .getSystemContext()
            .getBrokerConfiguration()
            .getData()
            .getDirectory());
  }

  private int status(final String actuatorPath) {
    return send(broker.monitoringUri(actuatorPath)).statusCode();
  }

  private String body(final String actuatorPath) {
    return send(broker.monitoringUri(actuatorPath)).body();
  }

  private int restStatus(final String path) {
    return send(URI.create(broker.restAddress().toString().replaceAll("/$", "") + path))
        .statusCode();
  }

  private HttpResponse<String> send(final URI uri) {
    try {
      return http.send(
          HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
          BodyHandlers.ofString());
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String uniqueTenantId() {
    return "rt" + Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()), 36);
  }

  private static final class ContinuousLoad {
    private final CamundaClient client;
    private final AtomicInteger succeeded = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread thread;

    private ContinuousLoad(final CamundaClient client) {
      this.client = client;
    }

    private void start() {
      thread =
          Thread.ofVirtual()
              .start(
                  () -> {
                    while (running.get()) {
                      try {
                        publishMessage(client);
                        succeeded.incrementAndGet();
                      } catch (final RuntimeException e) {
                        failed.incrementAndGet();
                      }
                    }
                  });
    }

    private void stop() throws InterruptedException {
      running.set(false);
      thread.join();
    }
  }
}
