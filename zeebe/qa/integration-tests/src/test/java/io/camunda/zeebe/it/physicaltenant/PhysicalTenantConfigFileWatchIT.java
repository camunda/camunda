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
import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenant;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.gateway.GatewayPhysicalTenantFollower;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status.Code;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Three brokers without embedded gateways, each reading its own configuration file through {@code
 * spring.config.additional-location}, and one standalone gateway. The test writes the files as a
 * ConfigMap update would, and the cluster follows them without a restart.
 */
@ZeebeIntegration
final class PhysicalTenantConfigFileWatchIT {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantConfigFileWatchIT.class);
  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final Duration QUIET_PERIOD = Duration.ofSeconds(1);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String DEFAULT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;

  private static final String CHANGED = uniqueTenantId("ch");
  private static final String TOGGLED = uniqueTenantId("tg");
  private static final String NOT_ON_THE_COORDINATOR = uniqueTenantId("nc");
  private static final String CHANGED_WHILE_DOWN = uniqueTenantId("cd");

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder().withTenant(DEFAULT, Storage.none()).build();

  private static final Path FILES = createDirectory();
  // what every broker's file declares, by tenant: its subtree
  private static final Map<String, Map<String, String>> DECLARED = new ConcurrentHashMap<>();

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
                      .withAdditionalConfigLocation(file(broker.nodeId().id()))
                      .withProperty("physical-tenant-rollout.file-watch.interval", "200ms")
                      .withProperty(
                          "physical-tenant-rollout.file-watch.quiet-period",
                          QUIET_PERIOD.toMillis() + "ms"))
          .build();

  static {
    final var declaredDefault = tenantConfiguration("Default from the file");
    declaredDefault.remove("security.initialization.default-roles.admin.users[0]");
    DECLARED.put(DEFAULT, declaredDefault);
    List.of(CHANGED, TOGGLED, NOT_ON_THE_COORDINATOR, CHANGED_WHILE_DOWN)
        .forEach(tenantId -> DECLARED.put(tenantId, tenantConfiguration("Boot " + tenantId)));
    writeFiles(0, 1, 2);
  }

  @Test
  void shouldRollOutAChangedTenantOnlyOnceTheFileIsStable() throws IOException {
    // given
    awaitRolledOut(CHANGED, 1);
    assertThat(status(CHANGED).get("owner").asText()).isEqualTo("FILE");
    final var changed = tenantConfiguration("Changed " + CHANGED);
    changed.put("data.snapshot-period", "7m");
    final Map<String, Long> availabilityVersions = new HashMap<>();
    DECLARED
        .keySet()
        .forEach(tenantId -> availabilityVersions.put(tenantId, availabilityVersion(tenantId)));

    // when
    for (int nodeId = 0; nodeId < 3; nodeId++) {
      Files.writeString(file(String.valueOf(nodeId)), yaml(Map.of(DEFAULT, DECLARED.get(DEFAULT))));
    }
    DECLARED.put(CHANGED, changed);
    writeFiles(0, 1, 2);

    // then
    awaitRolledOut(CHANGED, 2);
    for (final var broker : CLUSTER.brokers().values()) {
      assertThat(
              broker
                  .bean(PhysicalTenantLifecycleService.class)
                  .configuration(CHANGED)
                  .orElseThrow()
                  .getData()
                  .getSnapshotPeriod())
          .isEqualTo(Duration.ofMinutes(7));
    }
    availabilityVersions.forEach(
        (tenantId, availability) ->
            assertThat(availabilityVersion(tenantId))
                .as("the availability of %s never changed", tenantId)
                .isEqualTo(availability));
    awaitServedByGateway(CHANGED, 2);
    assertThat(gatewayTenant(CHANGED).name()).isEqualTo("Changed " + CHANGED);
  }

  @Test
  void shouldCreateATenantAddedToTheFile() {
    // given
    final var added = uniqueTenantId("ad");
    final var started = System.nanoTime();

    // when
    DECLARED.put(added, tenantConfiguration("Added " + added));
    writeFiles(0, 1, 2);

    // then
    awaitRolledOut(added, 1);
    LOG.info(
        "Tenant added to the files rolled out to 3 brokers {} ms after the files were written",
        (System.nanoTime() - started) / 1_000_000);
    assertThat(status(added).get("owner").asText()).isEqualTo("FILE");
    awaitServedByGateway(added, 1);
    try (final var client = tenantClient(added)) {
      deploy(client, added);
      assertThat(runProcess(client, added)).isPositive();
    }
  }

  @Test
  void shouldDisableATenantTakenOutOfTheFileKeepItsDataAndEnableItWhenPutBack() {
    // given
    awaitRolledOut(TOGGLED, 1);
    awaitServedByGateway(TOGGLED, 1);
    final var declared = DECLARED.get(TOGGLED);
    try (final var client = tenantClient(TOGGLED)) {
      deploy(client, TOGGLED);
      runProcess(client, TOGGLED);
    }

    // when
    DECLARED.remove(TOGGLED);
    writeFiles(0, 1, 2);

    // then
    Awaitility.await("the tenant is disabled and stopped on every broker")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var status = status(TOGGLED);
              assertThat(status.get("state").asText()).isEqualTo("DISABLED");
              assertThat(status.get("changing").asBoolean()).isFalse();
              assertThat(CLUSTER.brokers().values())
                  .allSatisfy(
                      broker ->
                          assertThat(broker.bean(DefaultPhysicalTenantRegistry.class).find(TOGGLED))
                              .isEmpty());
              assertThat(gateway().bean(DefaultPhysicalTenantRegistry.class).find(TOGGLED))
                  .isEmpty();
            });
    assertThat(CLUSTER.brokers().values())
        .allSatisfy(
            broker -> assertThat(Files.exists(tenantDataDirectory(broker, TOGGLED))).isTrue());
    try (final var client = tenantClient(TOGGLED)) {
      assertThatThrownBy(() -> publish(client))
          .isInstanceOf(ClientStatusException.class)
          .extracting(e -> ((ClientStatusException) e).getStatusCode())
          .isEqualTo(Code.NOT_FOUND);
    }

    // when
    DECLARED.put(TOGGLED, declared);
    writeFiles(0, 1, 2);

    // then
    Awaitility.await("the tenant is enabled and runs on every broker again")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var status = status(TOGGLED);
              assertThat(status.get("state").asText()).isEqualTo("ENABLED");
              assertThat(status.get("version").asLong()).isEqualTo(1);
              assertThat(status.get("rolledOut").asBoolean()).isTrue();
              assertThat(status.get("changing").asBoolean()).isFalse();
              assertThat(CLUSTER.brokers().values())
                  .allSatisfy(
                      broker ->
                          assertThat(broker.bean(DefaultPhysicalTenantRegistry.class).find(TOGGLED))
                              .isPresent());
            });
    Awaitility.await("the gateway serves the tenant again")
        .atMost(TIMEOUT)
        .until(() -> gateway().bean(DefaultPhysicalTenantRegistry.class).find(TOGGLED).isPresent());
    try (final var client = tenantClient(TOGGLED)) {
      assertThat(runProcess(client, TOGGLED)).isPositive();
    }
  }

  @Test
  void shouldRollOutAChangeOfTheDefaultTenantFromTheFile() throws InterruptedException {
    // given
    final var version = awaitRolledOutAtAnyVersion(DEFAULT);
    final Map<MemberId, Object> contexts = new HashMap<>();
    CLUSTER.brokers().forEach((id, broker) -> contexts.put(id, moduleContext(broker, DEFAULT)));
    final var defaultClient = tenantClient(DEFAULT);
    final var load = new PublishLoad(defaultClient);
    final var loadThread = Thread.ofVirtual().start(load);
    Awaitility.await("the load on the default tenant runs").until(() -> load.succeeded() > 5);
    final var changed = new TreeMap<>(DECLARED.get(DEFAULT));
    changed.put("name", "Default changed in the file");
    changed.put("data.snapshot-period", "11m");
    final var started = System.nanoTime();

    // when
    DECLARED.put(DEFAULT, changed);
    writeFiles(0, 1, 2);

    // then
    try {
      awaitRolledOut(DEFAULT, version + 1);
      LOG.info(
          "Change of the default tenant rolled out to 3 brokers {} ms after the files were written",
          (System.nanoTime() - started) / 1_000_000);
      CLUSTER
          .brokers()
          .forEach(
              (id, broker) -> {
                assertThat(
                        broker
                            .bean(PhysicalTenantLifecycleService.class)
                            .configuration(DEFAULT)
                            .orElseThrow()
                            .getData()
                            .getSnapshotPeriod())
                    .isEqualTo(Duration.ofMinutes(11));
                assertThat(moduleContext(broker, DEFAULT)).isNotSameAs(contexts.get(id));
                assertThat(broker.bean(DefaultPhysicalTenantRegistry.class).find(DEFAULT))
                    .map(PhysicalTenant::name)
                    .contains("Default changed in the file");
              });
      awaitServedByGateway(DEFAULT, version + 1);
      assertThat(gatewayTenant(DEFAULT).name()).isEqualTo("Default changed in the file");
      Awaitility.await("the default tenant serves through the gateway after the rollout")
          .atMost(TIMEOUT)
          .ignoreExceptions()
          .until(() -> publish(defaultClient));
    } finally {
      load.stop();
      loadThread.join();
      defaultClient.close();
      LOG.info(
          "Load on the default tenant through the standalone gateway during its rollout: {}"
              + " accepted, {} failed, longest gap {} ms",
          load.succeeded(),
          load.failed(),
          load.longestGap().toMillis());
    }
  }

  @Test
  void shouldActOnTheFileOfTheCoordinatorOnly() {
    // given
    awaitRolledOut(NOT_ON_THE_COORDINATOR, 1);

    // when
    DECLARED.put(NOT_ON_THE_COORDINATOR, tenantConfiguration("Not on 0 " + NOT_ON_THE_COORDINATOR));
    writeFiles(1, 2);

    // then
    Awaitility.await("the change stays on the members that are not the coordinator")
        .during(QUIET_PERIOD.multipliedBy(4))
        .atMost(QUIET_PERIOD.multipliedBy(6))
        .untilAsserted(
            () -> assertThat(status(NOT_ON_THE_COORDINATOR).get("version").asLong()).isEqualTo(1));

    // when
    writeFiles(0);

    // then
    awaitRolledOut(NOT_ON_THE_COORDINATOR, 2);
  }

  @Test
  void shouldActOnTheFileOfACoordinatorThatChangedWhileItWasDown() {
    // given
    awaitRolledOut(CHANGED_WHILE_DOWN, 1);
    final var coordinator = broker(0);
    coordinator.stop();

    // when
    DECLARED.put(CHANGED_WHILE_DOWN, tenantConfiguration("While down " + CHANGED_WHILE_DOWN));
    writeFiles(0, 1, 2);
    Awaitility.await("no member acts while the coordinator is down")
        .during(QUIET_PERIOD.multipliedBy(3))
        .atMost(QUIET_PERIOD.multipliedBy(5))
        .untilAsserted(() -> assertThat(localVersion(broker(1), CHANGED_WHILE_DOWN)).isEqualTo(1));
    coordinator.start();
    CLUSTER.awaitCompleteTopology();

    // then
    awaitRolledOut(CHANGED_WHILE_DOWN, 2);
  }

  private static void writeFiles(final int... nodeIds) {
    final var contents = yaml(DECLARED);
    for (final var nodeId : nodeIds) {
      try {
        final var file = file(String.valueOf(nodeId));
        Files.writeString(file, contents);
        file.toFile().deleteOnExit();
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private static String yaml(final Map<String, Map<String, String>> tenants) {
    final var yaml = new StringBuilder();
    new TreeMap<>(tenants)
        .forEach(
            (tenantId, subtree) ->
                new TreeMap<>(subtree)
                    .forEach(
                        (name, value) ->
                            yaml.append('"')
                                .append("camunda.physical-tenants.")
                                .append(tenantId)
                                .append('.')
                                .append(name)
                                .append("\": \"")
                                .append(value)
                                .append("\"\n")));
    return yaml.toString();
  }

  private static Path file(final String nodeId) {
    return FILES.resolve("broker-" + nodeId + ".yaml");
  }

  private static Path createDirectory() {
    try {
      final var directory = Files.createTempDirectory("pt-file-watch");
      directory.toFile().deleteOnExit();
      return directory;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, String> tenantConfiguration(final String name) {
    final Map<String, String> configuration = new TreeMap<>();
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

  private static long awaitRolledOutAtAnyVersion(final String tenantId) {
    Awaitility.await("%s is registered and rolled out".formatted(tenantId))
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var status = status(tenantId);
              assertThat(status.get("rolledOut").asBoolean()).isTrue();
              assertThat(status.get("changing").asBoolean()).isFalse();
            });
    return status(tenantId).get("version").asLong();
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

  private static PhysicalTenant gatewayTenant(final String tenantId) {
    return gateway().bean(DefaultPhysicalTenantRegistry.class).find(tenantId).orElseThrow();
  }

  private static void deploy(final CamundaClient client, final String tenantId) {
    Awaitility.await("deploy the process of " + tenantId)
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .until(
            () ->
                client
                        .newDeployResourceCommand()
                        .addProcessModel(
                            Bpmn.createExecutableProcess(processId(tenantId))
                                .startEvent()
                                .endEvent()
                                .done(),
                            processId(tenantId) + ".bpmn")
                        .send()
                        .join(10, TimeUnit.SECONDS)
                    != null);
  }

  private static long runProcess(final CamundaClient client, final String tenantId) {
    return Awaitility.await("run the process of " + tenantId)
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .until(
            () ->
                client
                    .newCreateInstanceCommand()
                    .bpmnProcessId(processId(tenantId))
                    .latestVersion()
                    .withResult()
                    .send()
                    .join(10, TimeUnit.SECONDS)
                    .getProcessInstanceKey(),
            key -> key > 0);
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

  private static Object moduleContext(final TestStandaloneBroker broker, final String tenantId) {
    return broker
        .bean(Broker.class)
        .getBrokerContext()
        .getPhysicalTenantModules()
        .context(tenantId)
        .orElseThrow();
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

  private static CamundaClient tenantClient(final String tenantId) {
    return TENANTS.newClientBuilder(gateway(), tenantId).preferRestOverGrpc(false).build();
  }

  private static JsonNode status(final String tenantId) {
    return status(broker(0), tenantId);
  }

  private static long availabilityVersion(final String tenantId) {
    return broker(0)
        .bean(Broker.class)
        .getBrokerContext()
        .getClusterConfigurationService()
        .getCurrentClusterConfiguration()
        .partitionGroup(tenantId)
        .availability()
        .version();
  }

  private static long localVersion(final TestStandaloneBroker broker, final String tenantId) {
    return broker
        .bean(Broker.class)
        .getBrokerContext()
        .getClusterConfigurationService()
        .getCurrentClusterConfiguration()
        .partitionGroup(tenantId)
        .tenantConfig()
        .orElseThrow()
        .version();
  }

  private static JsonNode status(final TestStandaloneBroker broker, final String tenantId) {
    try (final var http = HttpClient.newHttpClient()) {
      final var response =
          http.send(
              HttpRequest.newBuilder(broker.actuatorUri("physicalTenants", tenantId))
                  .method("GET", BodyPublishers.noBody())
                  .build(),
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

  private static TestStandaloneGateway gateway() {
    return CLUSTER.gateways().values().iterator().next();
  }

  private static TestStandaloneBroker broker(final int nodeId) {
    return CLUSTER.brokers().get(MemberId.from(String.valueOf(nodeId)));
  }

  private static String processId(final String tenantId) {
    return "process-" + tenantId;
  }

  private static String uniqueTenantId(final String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
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

    private Duration longestGap() {
      return Duration.ofNanos(longestGapNanos);
    }
  }
}
