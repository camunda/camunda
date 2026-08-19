/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.zeebe.qa.util.actuator.PrometheusActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Verifies the per-physical-tenant isolation of schema initialization on a live Elasticsearch: a
 * tenant whose schema cannot be applied is degraded on its own, the node still serves the healthy
 * tenants, and the degraded tenant recovers in the background once the cause is removed — with no
 * restart and no operator action. See {@code
 * docs/adr/management/005-per-physical-tenant-schema-initialization.md}.
 *
 * <p>The failure is injected at schema-initialization time by pre-creating one of tenant A's
 * indices with a strict, conflicting mapping, so the tenant's very first attempt fails while
 * everything else about the cluster stays healthy — no network games, one container, and a recovery
 * that is a single index deletion. The same trick is used by {@code SchemaManagerStartupIT}.
 *
 * <p>The degraded tenant is deliberately <em>not</em> the default one: until the module-specific
 * search-engine readiness indicators are replaced ({@code #51861}), a degraded default tenant still
 * pulls down the readiness group, so isolation is only observable for a non-default tenant.
 */
@Timeout(300)
@ZeebeIntegration
final class PhysicalTenantSchemaInitializationIsolationIT {

  private static final String TENANT_A = "tenanta";
  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String DEFAULT_PREFIX = "defaultprefix";
  private static final String TENANT_A_PREFIX = "tenantaprefix";

  private static final String READINESS_GAUGE = "camunda_physical_tenant_secondary_storage_ready";

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @SuppressWarnings("resource")
  private static final ElasticsearchContainer ES =
      TestSearchContainers.createDefaultElasticsearchContainer();

  private static final String ES_URL;
  private static final PhysicalTenantsITHelper TENANTS;

  /** The conflicting index has to exist before the broker boots, or the first attempt succeeds. */
  static {
    ES.start();
    ES_URL = "http://" + ES.getHttpHostAddress();
    try {
      createConflictingRoleIndex();
    } catch (final RuntimeException e) {
      // class initialization is about to fail, and @AfterAll will not run to release the container
      ES.stop();
      throw e;
    }
    TENANTS =
        PhysicalTenantsITHelper.builder()
            .withTenant(DEFAULT_TENANT, Storage.elasticsearch(ES_URL, DEFAULT_PREFIX))
            .withTenant(TENANT_A, Storage.elasticsearch(ES_URL, TENANT_A_PREFIX))
            .build();
  }

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker().withUnauthenticatedAccess().withCreateSchema(true));

  @AfterAll
  static void stopElasticsearch() {
    ES.stop();
  }

  @Test
  void shouldDegradeOnlyTheTenantWhoseSchemaFailedAndRecoverItInTheBackground() throws Exception {
    // given - the node started and was admitted even though tenant A never initialized, which is
    // the behaviour that used to require the whole context to come down
    assertThat(readinessGaugeFor(DEFAULT_TENANT)).isEqualTo(1);
    assertThat(readinessGaugeFor(TENANT_A)).isZero();

    // then - the healthy tenant is served ...
    assertThat(searchProcessInstances(DEFAULT_TENANT).statusCode()).isEqualTo(200);

    // ... while the degraded one is rejected per request, with a hint that this is transient
    final HttpResponse<String> degraded = searchProcessInstances(TENANT_A);
    assertThat(degraded.statusCode()).isEqualTo(503);
    assertThat(degraded.headers().firstValue("Retry-After")).hasValue("5");
    assertThat(degraded.body())
        .contains("Physical tenant '" + TENANT_A + "' is degraded")
        .contains("\"status\":503");

    // when - the cause is removed, without restarting the node
    deleteConflictingRoleIndex();

    // then - the background retry loop initializes tenant A on its own
    Awaitility.await("tenant A recovers without a restart")
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(() -> assertThat(readinessGaugeFor(TENANT_A)).isEqualTo(1));
    Awaitility.await("tenant A is served again")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> assertThat(searchProcessInstances(TENANT_A).statusCode()).isEqualTo(200));

    // and - the healthy tenant was never affected
    assertThat(readinessGaugeFor(DEFAULT_TENANT)).isEqualTo(1);
  }

  private HttpResponse<String> searchProcessInstances(final String physicalTenantId)
      throws IOException, InterruptedException {
    final var request =
        HttpRequest.newBuilder(restUriFor(physicalTenantId, "v2/process-instances/search"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString("{}"))
            .build();
    return HTTP.send(request, BodyHandlers.ofString());
  }

  private URI restUriFor(final String physicalTenantId, final String path) {
    final String base = broker.restAddress().toString();
    final String prefix =
        DEFAULT_TENANT.equals(physicalTenantId) ? "" : "physical-tenants/" + physicalTenantId + "/";
    return URI.create(base.endsWith("/") ? base + prefix + path : base + "/" + prefix + path);
  }

  private double readinessGaugeFor(final String physicalTenantId) {
    final var matcher =
        Pattern.compile(
                READINESS_GAUGE
                    + "\\{[^}]*physicalTenant=\""
                    + physicalTenantId
                    + "\"[^}]*}\\s+(\\S+)")
            .matcher(PrometheusActuator.of(broker).metrics());
    assertThat(matcher.find())
        .as("a readiness gauge is exported for physical tenant '%s'", physicalTenantId)
        .isTrue();
    return Double.parseDouble(matcher.group(1));
  }

  /**
   * Creates tenant A's role index with {@code dynamic: strict} and a {@code roleId} type that
   * conflicts with the descriptor, so applying the schema fails on a mapping update rather than on
   * anything cluster-wide — every other tenant sees a perfectly healthy Elasticsearch.
   */
  private static void createConflictingRoleIndex() {
    sendToElasticsearch(
        "PUT",
        conflictingRoleIndexName(),
        BodyPublishers.ofString(
            "{\"mappings\":{\"dynamic\":\"strict\",\"properties\":{\"roleId\":{\"type\":\"long\"}}}}"),
        200);
  }

  private static void deleteConflictingRoleIndex() {
    sendToElasticsearch("DELETE", conflictingRoleIndexName(), BodyPublishers.noBody(), 200);
  }

  private static String conflictingRoleIndexName() {
    return new RoleIndex(TENANT_A_PREFIX, true).getFullQualifiedName();
  }

  private static void sendToElasticsearch(
      final String method,
      final String index,
      final HttpRequest.BodyPublisher body,
      final int expectedStatus) {
    try {
      final var response =
          HTTP.send(
              HttpRequest.newBuilder(URI.create(ES_URL + "/" + index))
                  .header("Content-Type", "application/json")
                  .method(method, body)
                  .build(),
              BodyHandlers.ofString());
      assertThat(response.statusCode())
          .as("%s %s: %s", method, index, response.body())
          .isEqualTo(expectedStatus);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while talking to Elasticsearch", e);
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to talk to Elasticsearch", e);
    }
  }
}
