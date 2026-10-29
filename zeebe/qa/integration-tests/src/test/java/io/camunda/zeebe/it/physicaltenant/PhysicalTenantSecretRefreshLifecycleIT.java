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
import io.camunda.application.commons.secrets.InternalConfigRefresher;
import io.camunda.application.commons.secrets.InternalConfigRefreshers;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
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
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adds, replaces and removes a physical tenant on a running broker, and follows the refresher of
 * its secrets: it starts with the tenant, keeps its consumers and takes the new interval when the
 * tenant is replaced, and stops and closes its consumers when the tenant is removed.
 */
@ZeebeIntegration
final class PhysicalTenantSecretRefreshLifecycleIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String SECRET = "db-password";
  private static final Map<String, String> CONFIGURED =
      Map.of("password", "camunda.secrets." + SECRET);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .build();

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @TempDir private Path secrets;
  private Path internalConfig;

  @BeforeEach
  void startBrokerWithAnInternalConfigStore() throws IOException {
    final var jobs = Files.createDirectory(secrets.resolve("jobs"));
    internalConfig = Files.createDirectory(secrets.resolve("internal"));
    writeSecret("initial");
    broker
        .withProperty("camunda.secrets.stores.file.default.path", jobs.toString())
        .withProperty(
            "camunda.secrets.stores.file.default.internal-config.path", internalConfig.toString())
        .start()
        .awaitCompleteTopology();
  }

  @Test
  void shouldStartTheRefresherOfATenantAddedAtRuntime() throws IOException {
    // given
    final var tenantId = uniqueTenantId();
    final var consumer = new RecordingConsumer();

    // when
    lifecycle().add(tenantId, tenantConfiguration(Duration.ofMillis(100)));
    refresherOf(tenantId).register(consumer, Map.of("password", "initial"));
    writeSecret("rotated");

    // then
    Awaitility.await("the rotated secret reaches the consumer")
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(consumer.applied).contains(Map.of("password", "rotated")));
  }

  @Test
  void shouldKeepTheConsumersOfAReplacedTenantAndUseItsNewInterval() throws IOException {
    // given
    final var tenantId = uniqueTenantId();
    lifecycle().add(tenantId, tenantConfiguration(Duration.ofHours(1)));
    final var refresher = refresherOf(tenantId);
    final var consumer = new RecordingConsumer();
    refresher.register(consumer, Map.of("password", "initial"));

    // when
    lifecycle().replace(tenantId, tenantConfiguration(Duration.ofMillis(100)));
    writeSecret("rotated");

    // then
    assertThat(refresherOf(tenantId)).isSameAs(refresher);
    Awaitility.await("the rotated secret reaches the consumer at the new interval")
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(consumer.applied).contains(Map.of("password", "rotated")));
    assertThat(consumer.closed).isFalse();
  }

  @Test
  void shouldStopTheRefresherAndCloseItsConsumersWhenTheTenantIsRemoved() throws IOException {
    // given
    final var tenantId = uniqueTenantId();
    lifecycle().add(tenantId, tenantConfiguration(Duration.ofMillis(50)));
    final var refresher = refresherOf(tenantId);
    final var consumer = new RecordingConsumer();
    refresher.register(consumer, Map.of("password", "initial"));

    // when
    lifecycle().remove(tenantId);
    writeSecret("rotated");

    // then
    assertThat(consumer.closed).isTrue();
    assertThat(refresher.isClosed()).isTrue();
    assertThat(refreshers().forPhysicalTenant(tenantId)).isEmpty();
    Awaitility.await("no refresh reaches the consumer of a removed tenant")
        .during(Duration.ofMillis(500))
        .atMost(Duration.ofSeconds(5))
        .until(() -> consumer.applied.isEmpty());
  }

  @Test
  void shouldReportAFailedRefreshOnlyInTheTenantsOwnHealth() throws Exception {
    // given
    final var tenantId = uniqueTenantId();
    lifecycle().add(tenantId, tenantConfiguration(Duration.ofMillis(50)));
    awaitTenantHealth(tenantId, "UP");
    final var consumer = new RecordingConsumer();
    consumer.fail = true;
    refresherOf(tenantId).register(consumer, Map.of("password", "initial"));

    // when
    writeSecret("rotated-but-rejected");

    // then
    final var degraded = awaitTenantHealth(tenantId, "DEGRADED");
    assertThat(degraded.at("/details/detail").asText())
        .contains("recording", "cannot connect")
        .doesNotContain("rotated-but-rejected");
    assertThat(get("health", "liveness").statusCode()).isEqualTo(200);
    assertThat(get("health", "readiness").statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(get("health", "readiness").body()).path("status").asText())
        .isEqualTo("UP");

    // when
    consumer.fail = false;

    // then
    awaitTenantHealth(tenantId, "UP");
  }

  private JsonNode awaitTenantHealth(final String tenantId, final String status) {
    final var health = new AtomicReference<JsonNode>();
    Awaitility.await("physical tenant '%s' reports %s".formatted(tenantId, status))
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              health.set(JSON.readTree(get("health", "physicalTenants", tenantId).body()));
              assertThat(health.get().path("status").asText()).isEqualTo(status);
            });
    return health.get();
  }

  private HttpResponse<String> get(final String... path) throws Exception {
    try (final var http = HttpClient.newHttpClient()) {
      return http.send(
          HttpRequest.newBuilder(broker.actuatorUri(path)).GET().build(), BodyHandlers.ofString());
    }
  }

  private InternalConfigRefresher refresherOf(final String tenantId) {
    return refreshers().forPhysicalTenant(tenantId).orElseThrow();
  }

  private InternalConfigRefreshers refreshers() {
    return broker.bean(InternalConfigRefreshers.class);
  }

  private PhysicalTenantLifecycleService lifecycle() {
    return broker.bean(PhysicalTenantLifecycleService.class);
  }

  private void writeSecret(final String value) throws IOException {
    Files.writeString(internalConfig.resolve(SECRET), value);
  }

  private Map<String, Object> tenantConfiguration(final Duration refreshInterval) {
    final Map<String, Object> configuration = new HashMap<>();
    configuration.put("data.secondary-storage.type", "none");
    configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
    configuration.put("secrets.refresh-interval", refreshInterval.toMillis() + "ms");
    if (broker
        .unifiedConfig()
        .getData()
        .getExporters()
        .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID)) {
      configuration.put("data.exporters-assigned[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
    }
    return configuration;
  }

  private static String uniqueTenantId() {
    return "sr" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  private static final class RecordingConsumer implements InternalConfigRefresher.Consumer {

    private final List<Map<String, String>> applied = new CopyOnWriteArrayList<>();
    private volatile boolean closed;
    private volatile boolean fail;

    @Override
    public String name() {
      return "recording";
    }

    @Override
    public Map<String, String> configured() {
      return CONFIGURED;
    }

    @Override
    public void apply(final Map<String, String> resolved) {
      if (fail) {
        throw new IllegalStateException("cannot connect");
      }
      applied.add(resolved);
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
