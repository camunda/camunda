/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Readiness of a broker serving two physical tenants: UP once every tenant is initialized, DEGRADED
 * (HTTP 200) while another tenant failed, retries, or is recovering. Cases with no initialized
 * tenant never reach a running node (startup waits or aborts); see {@code
 * SchemaReadinessCheckTest}.
 */
@Timeout(600)
@ZeebeIntegration
final class PhysicalTenantReadinessIT {

  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String TENANT_A = "tenanta";
  private static final String UNREACHABLE_ES_URL = "http://localhost:1";
  private static final Duration TIMEOUT = Duration.ofSeconds(90);

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @SuppressWarnings("resource")
  private static final ElasticsearchContainer ES =
      TestSearchContainers.createDefaultElasticsearchContainer();

  private static final String ES_URL;

  static {
    ES.start();
    ES_URL = "http://" + ES.getHttpHostAddress();
  }

  @AfterAll
  static void stopElasticsearch() {
    ES.stop();
  }

  @Test
  void shouldBeUpOnceEveryTenantIsInitialized(@TempDir final Path workingDirectory)
      throws Exception {
    // given
    final var tenants = tenants(Storage.elasticsearch(ES_URL, prefix()), healthyStorage());

    // when
    try (final var broker = startBroker(tenants, workingDirectory)) {

      // then
      awaitReadiness(broker, 200, "UP");
      assertThat(tenantStates(broker)).containsExactly("INITIALIZED", "INITIALIZED");
    }
  }

  @Test
  void shouldBeDegradedWhileOneTenantFailedAndTheOtherIsInitialized(
      @TempDir final Path workingDirectory) throws Exception {
    // given
    final var tenants = tenants(healthyStorage(), failingStorage());

    // when
    try (final var broker = startBroker(tenants, workingDirectory)) {

      // then
      awaitTenantStates(broker, "INITIALIZED", "FAILED");
      awaitReadiness(broker, 200, "DEGRADED");
    }
  }

  @Test
  void shouldBeDegradedWhileOneTenantIsStillRetrying(@TempDir final Path workingDirectory)
      throws Exception {
    // given
    final var tenants =
        tenants(healthyStorage(), Storage.elasticsearch(UNREACHABLE_ES_URL, prefix()));

    // when
    try (final var broker = startBroker(tenants, workingDirectory)) {

      // then
      awaitTenantStates(broker, "INITIALIZED", "RETRYING");
      awaitReadiness(broker, 200, "DEGRADED");
    }
  }

  @Test
  void shouldBeDegradedAsSoonAsATenantEntersRecoveryUntilItLeavesIt(
      @TempDir final Path workingDirectory) throws Exception {
    // given
    final var tenants = tenants(healthyStorage(), healthyStorage());
    try (final var broker = startBroker(tenants, workingDirectory)) {
      awaitReadiness(broker, 200, "UP");

      // when - tenant A enters recovery, without a restart
      changeMode(broker, TENANT_A, "RECOVERING");
      awaitPartitionsOf(broker, TENANT_A, "recovering");

      // then - its schema stays initialized, but the node is DEGRADED and its searches are rejected
      awaitReadiness(broker, 200, "DEGRADED");
      assertThat(tenantStates(broker)).containsExactly("INITIALIZED", "INITIALIZED");
      assertThat(searchStatus(broker, TENANT_A)).isEqualTo(503);
      assertThat(searchStatus(broker, DEFAULT_TENANT)).isEqualTo(200);

      // when - tenant A leaves recovery
      changeMode(broker, TENANT_A, "PROCESSING");

      // then
      awaitReadiness(broker, 200, "UP");
      assertThat(searchStatus(broker, TENANT_A)).isEqualTo(200);
    }
  }

  @Test
  void shouldBeDegradedWhileOneTenantIsDeferredForRecoveryUntilItLeavesRecovery(
      @TempDir final Path workingDirectory) throws Exception {
    // given
    final var tenants = tenants(healthyStorage(), healthyStorage());
    try (final var broker = startBroker(tenants, workingDirectory)) {
      awaitReadiness(broker, 200, "UP");
      changeMode(broker, TENANT_A, "RECOVERING");
      awaitPartitionsOf(broker, TENANT_A, "recovering");

      // when - the broker restarts with tenant A recovering
      broker.stop();
      broker.start();

      // then
      awaitTenantStates(broker, "INITIALIZED", "RECOVERING");
      awaitReadiness(broker, 200, "DEGRADED");

      // when - tenant A leaves recovery
      changeMode(broker, TENANT_A, "PROCESSING");

      // then
      awaitTenantStates(broker, "INITIALIZED", "INITIALIZED");
      awaitReadiness(broker, 200, "UP");
    }
  }

  private static PhysicalTenantsITHelper tenants(
      final Storage defaultTenantStorage, final Storage tenantAStorage) {
    return PhysicalTenantsITHelper.builder()
        .withTenant(DEFAULT_TENANT, defaultTenantStorage)
        .withTenant(TENANT_A, tenantAStorage)
        .build();
  }

  /** Own working directory, so a restart keeps which tenants are recovering. */
  private static TestStandaloneBroker startBroker(
      final PhysicalTenantsITHelper tenants, final Path workingDirectory) {
    return tenants
        .configure(
            new TestStandaloneBroker()
                .withWorkingDirectory(workingDirectory)
                .withUnauthenticatedAccess()
                .withCreateSchema(true))
        .start();
  }

  private static Storage healthyStorage() {
    return Storage.elasticsearch(ES_URL, prefix());
  }

  /** A pre-created role index with a conflicting mapping fails the schema for good. */
  private static Storage failingStorage() {
    final var prefix = prefix();
    sendToElasticsearch(
        "PUT",
        new RoleIndex(prefix, true).getFullQualifiedName(),
        "{\"mappings\":{\"dynamic\":\"strict\",\"properties\":{\"roleId\":{\"type\":\"long\"}}}}");
    return Storage.elasticsearch(ES_URL, prefix);
  }

  private static String prefix() {
    return "readiness" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private static void awaitReadiness(
      final TestStandaloneBroker broker, final int httpStatus, final String status) {
    Awaitility.await("readiness reports %s with HTTP %d".formatted(status, httpStatus))
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var readiness =
                  send(HttpRequest.newBuilder(broker.actuatorUri("health", "readiness")).GET());
              assertThat(readiness.statusCode()).isEqualTo(httpStatus);
              assertThat(OBJECT_MAPPER.readTree(readiness.body()).path("status").asText())
                  .isEqualTo(status);
            });
  }

  private static void awaitTenantStates(
      final TestStandaloneBroker broker,
      final String defaultTenantState,
      final String tenantAState) {
    Awaitility.await("tenants report %s and %s".formatted(defaultTenantState, tenantAState))
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(tenantStates(broker)).containsExactly(defaultTenantState, tenantAState));
  }

  private static List<String> tenantStates(final TestStandaloneBroker broker)
      throws IOException, InterruptedException {
    final JsonNode details =
        OBJECT_MAPPER
            .readTree(send(HttpRequest.newBuilder(broker.actuatorUri("health")).GET()).body())
            .at("/components/physicalTenantSchemaInitialization/details");
    return List.of(
        details.at("/" + DEFAULT_TENANT + "/state").asText(),
        details.at("/" + TENANT_A + "/state").asText());
  }

  private static void changeMode(
      final TestStandaloneBroker broker, final String physicalTenantId, final String mode) {
    final var uri =
        restUri(broker, physicalTenantId, "v2/mode?mode=%s&dryRun=false".formatted(mode));
    Awaitility.await("tenant %s is switched to %s".formatted(physicalTenantId, mode))
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        send(HttpRequest.newBuilder(uri).method("PATCH", BodyPublishers.noBody()))
                            .statusCode())
                    .isEqualTo(200));
  }

  private static void awaitPartitionsOf(
      final TestStandaloneBroker broker, final String physicalTenantId, final String state) {
    final var uri = restUri(broker, physicalTenantId, "v2/topology");
    Awaitility.await("partitions of %s are %s".formatted(physicalTenantId, state))
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var partitionStates =
                  OBJECT_MAPPER
                      .readTree(send(HttpRequest.newBuilder(uri).GET()).body())
                      .findValues("partitions")
                      .stream()
                      .flatMap(partitions -> partitions.findValuesAsText("state").stream())
                      .toList();
              assertThat(partitionStates).isNotEmpty().containsOnly(state);
            });
  }

  private static int searchStatus(final TestStandaloneBroker broker, final String physicalTenantId)
      throws IOException, InterruptedException {
    return send(HttpRequest.newBuilder(
                restUri(broker, physicalTenantId, "v2/process-instances/search"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString("{}")))
        .statusCode();
  }

  private static URI restUri(
      final TestStandaloneBroker broker, final String physicalTenantId, final String path) {
    final var base = broker.restAddress().toString().replaceAll("/+$", "") + "/";
    final var prefix =
        DEFAULT_TENANT.equals(physicalTenantId) ? "" : "physical-tenants/" + physicalTenantId + "/";
    return URI.create(base + prefix + path);
  }

  private static HttpResponse<String> send(final HttpRequest.Builder request)
      throws IOException, InterruptedException {
    return HTTP.send(request.build(), BodyHandlers.ofString());
  }

  private static void sendToElasticsearch(
      final String method, final String index, final String body) {
    try {
      final var response =
          HTTP.send(
              HttpRequest.newBuilder(URI.create(ES_URL + "/" + index))
                  .header("Content-Type", "application/json")
                  .method(method, BodyPublishers.ofString(body))
                  .build(),
              BodyHandlers.ofString());
      assertThat(response.statusCode())
          .as("%s %s: %s", method, index, response.body())
          .isEqualTo(200);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while talking to Elasticsearch", e);
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to talk to Elasticsearch", e);
    }
  }
}
