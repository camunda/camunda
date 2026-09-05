/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.qa.util.actuator.PrometheusActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.testcontainers.ProxyRegistry;
import io.camunda.zeebe.qa.util.testcontainers.ProxyRegistry.ContainerProxy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;

/**
 * Verifies the per-physical-tenant isolation of RDBMS schema initialization against a live
 * PostgreSQL: a tenant whose database cannot be reached is degraded on its own, the node still
 * serves the healthy tenant, and the degraded tenant recovers in the background once its database
 * comes back — with no restart and no operator action. See {@code
 * docs/adr/management/005-per-physical-tenant-schema-initialization.md}.
 *
 * <p>An unreachable database rather than a failing migration, deliberately. It is the failure
 * #54299 names first, and the one this work newly isolates: the tenant's whole object graph —
 * including the database vendor its mapper statements and vendor properties are selected by — is
 * built before initialization starts, so until that graph could be built without connecting, one
 * tenant being down failed the context refresh for every tenant on the node. A tenant reachable but
 * unmigratable exercises none of that, and is covered where it costs no container, in {@code
 * RdbmsSchemaInitializerH2IT}.
 *
 * <p>Tenant A therefore reaches the shared PostgreSQL through a Toxiproxy proxy that starts
 * disabled: its port refuses connections, so Liquibase fails to connect and the tenant degrades,
 * while the default tenant connects directly and migrates. Recovery is {@code proxy.enable()},
 * which restores reachability rather than removing an obstacle from an already-open connection.
 *
 * <p>One vendor rather than the whole matrix, deliberately: what this proves — the 503 with a
 * {@code Retry-After}, the readiness gauge at 0, and background recovery — has nothing
 * vendor-specific about it, and the matrix is expensive. The counterpart on Elasticsearch is {@link
 * PhysicalTenantSchemaInitializationIsolationIT}.
 *
 * <p>The degraded tenant is deliberately <em>not</em> the default one: until the module-specific
 * readiness indicators are replaced ({@code #51861}), a degraded default tenant still pulls down
 * the readiness group, so isolation is only observable for a non-default tenant.
 */
@Tag("rdbms")
@Timeout(300)
@ZeebeIntegration
final class PhysicalTenantRdbmsSchemaInitializationIsolationIT {

  private static final String TENANT_A = "tenanta";
  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;

  private static final String DEFAULT_TENANT_SCHEMA = "defaulttenant";
  private static final String TENANT_A_SCHEMA = "tenanta";

  private static final String READINESS_GAUGE = "camunda_physical_tenant_secondary_storage_ready";

  private static final String DATABASE_NAME = "camunda";
  private static final String DATABASE_USER = "camunda";
  private static final String DATABASE_PASSWORD = "camunda";

  /**
   * Short enough that a refused connection surfaces as a failed attempt in seconds rather than
   * spending HikariCP's 30-second default retrying inside a single one. It shortens how long the
   * node holds at the gate waiting for tenant A to settle; it does not change what happens.
   */
  private static final Duration DEGRADED_TENANT_CONNECTION_TIMEOUT = Duration.ofSeconds(2);

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName(DATABASE_NAME)
          .withUsername(DATABASE_USER)
          .withPassword(DATABASE_PASSWORD)
          .withStartupTimeout(Duration.ofMinutes(5));

  @SuppressWarnings("resource")
  private static final ToxiproxyContainer TOXIPROXY =
      ProxyRegistry.addExposedPorts(new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0"));

  private static final ContainerProxy TENANT_A_PROXY;
  private static final PhysicalTenantsITHelper TENANTS;

  /**
   * Both schemas exist before the node starts, so that being unreachable is the only thing wrong
   * with tenant A and the recovery assertion cannot pass for another reason.
   */
  static {
    POSTGRES.start();
    try {
      TOXIPROXY.start();
      TENANT_A_PROXY =
          new ProxyRegistry(TOXIPROXY)
              .getOrCreateHostProxy(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
      disableTenantAProxy();
      executeOnPostgres("CREATE SCHEMA " + DEFAULT_TENANT_SCHEMA);
      executeOnPostgres("CREATE SCHEMA " + TENANT_A_SCHEMA);
    } catch (final RuntimeException e) {
      // class initialization is about to fail, and @AfterAll will not run to release the containers
      stopContainers();
      throw e;
    }
    TENANTS =
        PhysicalTenantsITHelper.builder()
            .withTenant(DEFAULT_TENANT, directlyIn(DEFAULT_TENANT_SCHEMA))
            .withTenant(TENANT_A, throughTheProxyIn(TENANT_A_SCHEMA))
            .build();
  }

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS
          .configure(new TestStandaloneBroker().withUnauthenticatedAccess())
          .withPtConfig(
              TENANT_A,
              camunda ->
                  camunda
                      .getData()
                      .getSecondaryStorage()
                      .getRdbms()
                      .getConnectionPool()
                      .setConnectionTimeout(DEGRADED_TENANT_CONNECTION_TIMEOUT));

  @AfterAll
  static void stopContainers() {
    TOXIPROXY.stop();
    POSTGRES.stop();
  }

  @Test
  void shouldDegradeOnlyTheTenantWhoseDatabaseIsUnreachableAndRecoverItInTheBackground()
      throws Exception {
    // given - the node started and was admitted even though tenant A's database was never
    // reachable, which is the behaviour that used to require the whole context to come down
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

    // when - its database becomes reachable again, without restarting the node
    TENANT_A_PROXY.proxy().enable();

    // then - the background retry loop migrates tenant A on its own
    Awaitility.await("tenant A recovers without a restart")
        .atMost(Duration.ofSeconds(120))
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
   * A tenant on its own PostgreSQL schema inside the shared database, selected through {@code
   * currentSchema} — the same per-tenant isolation the multi-database test matrix provisions.
   */
  private static Storage directlyIn(final String schema) {
    return rdbmsAt(POSTGRES.getJdbcUrl(), schema);
  }

  /**
   * The same database reached through Toxiproxy, so that the test can take it away and give it back
   * while the node runs. The vendor still resolves from the {@code jdbc:postgresql:} prefix, which
   * is what lets this tenant's beans be built while its database refuses connections.
   */
  private static Storage throughTheProxyIn(final String schema) {
    final var proxiedUrl =
        "jdbc:postgresql://%s:%d/%s"
            .formatted(
                TOXIPROXY.getHost(),
                TOXIPROXY.getMappedPort(TENANT_A_PROXY.internalPort()),
                DATABASE_NAME);
    return rdbmsAt(proxiedUrl, schema);
  }

  private static Storage rdbmsAt(final String baseUrl, final String schema) {
    final String url = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    return Storage.rdbms(url, DATABASE_USER, DATABASE_PASSWORD);
  }

  private static void disableTenantAProxy() {
    try {
      TENANT_A_PROXY.proxy().disable();
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to disable tenant A's Toxiproxy proxy", e);
    }
  }

  private static void executeOnPostgres(final String sql) {
    try (final var connection =
            DriverManager.getConnection(POSTGRES.getJdbcUrl(), DATABASE_USER, DATABASE_PASSWORD);
        final var statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to run '" + sql + "' on PostgreSQL", e);
    }
  }
}
