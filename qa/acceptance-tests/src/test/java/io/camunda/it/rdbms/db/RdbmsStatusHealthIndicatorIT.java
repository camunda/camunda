/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@code rdbmsStatus} entry of {@code /actuator/health}: a single physical tenant
 * keeps the original flat {@link org.springframework.boot.jdbc.health.DataSourceHealthIndicator}
 * shape, while more than one is reported as a composite, keyed by physical tenant id, with one
 * indicator per pool.
 */
@Tag("rdbms")
class RdbmsStatusHealthIndicatorIT {

  private static final String TENANT_B = "tenantb";
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private CamundaRdbmsTestApplication app;

  @AfterEach
  void tearDown() {
    if (app != null) {
      app.close();
    }
  }

  @Test
  void shouldExposePlainDataSourceHealthForSingleTenant() throws Exception {
    // given - a single, default physical tenant, exactly as before physical tenants existed
    app =
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withUnauthenticatedAccess()
            .withProperty(
                "camunda.data.secondary-storage.rdbms.url",
                "jdbc:h2:mem:rdbms-health-single;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            .withProperty("camunda.data.secondary-storage.rdbms.username", "sa")
            .withProperty("camunda.data.secondary-storage.rdbms.password", "")
            .withProperty("management.endpoint.health.show-details", "always");
    app.start();

    // when
    final JsonNode rdbmsStatus = fetchRdbmsStatus(app);

    // then - the original flat shape is preserved: a plain DataSourceHealthIndicator, not a
    // composite
    assertThat(rdbmsStatus.path("status").asText()).isEqualTo("UP");
    assertThat(rdbmsStatus.path("details").path("database").asText()).isEqualTo("H2");
    assertThat(rdbmsStatus.has("components")).isFalse();
  }

  @Test
  void shouldExposeCompositeHealthPerPhysicalTenantForMultipleTenants() throws Exception {
    // given - the default tenant plus one more, each on its own H2 database
    app =
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withUnauthenticatedAccess()
            .withProperty(
                "camunda.data.secondary-storage.rdbms.url",
                "jdbc:h2:mem:rdbms-health-multi-default;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            .withProperty("camunda.data.secondary-storage.rdbms.username", "sa")
            .withProperty("camunda.data.secondary-storage.rdbms.password", "")
            .withProperty(
                "camunda.physical-tenants." + TENANT_B + ".data.secondary-storage.rdbms.url",
                "jdbc:h2:mem:rdbms-health-multi-tenantb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            .withProperty(
                "camunda.physical-tenants." + TENANT_B + ".data.secondary-storage.rdbms.username",
                "sa")
            .withProperty(
                "camunda.physical-tenants." + TENANT_B + ".data.secondary-storage.rdbms.password",
                "")
            // explicitly-configured tenants must provide their own initialization block
            .withProperty(
                "camunda.physical-tenants."
                    + TENANT_B
                    + ".security.initialization.default-roles.admin.users[0]",
                TENANT_B + "-admin")
            .withProperty("management.endpoint.health.show-details", "always");
    app.start();

    // when
    final JsonNode rdbmsStatus = fetchRdbmsStatus(app);

    // then - a composite over both physical tenants, each reported under its own tenant id, and
    // the default tenant is no longer special-cased into the flat shape
    assertThat(rdbmsStatus.path("status").asText()).isEqualTo("UP");
    assertThat(rdbmsStatus.has("details")).isFalse();
    assertThat(
            rdbmsStatus.path("components").path(DEFAULT_PHYSICAL_TENANT_ID).path("status").asText())
        .isEqualTo("UP");
    assertThat(rdbmsStatus.path("components").path(TENANT_B).path("status").asText())
        .isEqualTo("UP");
  }

  private static JsonNode fetchRdbmsStatus(final CamundaRdbmsTestApplication app)
      throws IOException, InterruptedException {
    final var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + app.mappedPort(TestZeebePort.MONITORING)
                        + "/actuator/health"))
            .GET()
            .build();
    final HttpResponse<String> response = HTTP.send(request, BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return OBJECT_MAPPER.readTree(response.body()).path("components").path("rdbmsStatus");
  }
}
