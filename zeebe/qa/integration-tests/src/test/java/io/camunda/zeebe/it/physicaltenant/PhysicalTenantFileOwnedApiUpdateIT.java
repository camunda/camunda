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
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class PhysicalTenantFileOwnedApiUpdateIT {

  private static final String BOOT_TENANT = "boottenant";
  private static final Duration TIMEOUT = Duration.ofMinutes(1);
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(BOOT_TENANT, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              .withProperty("physical-tenant-rollout.api-updates-file-owned-tenants", true));

  private final HttpClient http = HttpClient.newHttpClient();

  @AfterEach
  void tearDown() {
    http.close();
  }

  @Test
  void shouldRollOutAnApiChangeOfAFileOwnedTenantWhenAllowed() throws Exception {
    // given
    Awaitility.await("the boot tenant is registered from the file")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              assertThat(status().get("owner").asText()).isEqualTo("FILE");
              assertThat(status().get("rolledOut").asBoolean()).isTrue();
            });
    final var version = status().get("version").asLong();
    final var moduleBefore = moduleContext();

    // when
    final var response =
        send(
            "PUT",
            Map.of(
                "name",
                "Changed through the API",
                "data.secondary-storage.type",
                "none",
                "data.snapshot-period",
                "7m",
                "security.initialization.default-roles.admin.users[0]",
                TENANTS.adminUsername(BOOT_TENANT)));

    // then
    assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
    Awaitility.await("the change rolls out")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var status = status();
              assertThat(status.get("version").asLong()).isEqualTo(version + 1);
              assertThat(status.get("rolledOut").asBoolean()).isTrue();
              assertThat(status.get("owner").asText()).isEqualTo("FILE");
            });
    assertThat(moduleContext()).isNotSameAs(moduleBefore);
  }

  private Object moduleContext() {
    return broker
        .bean(Broker.class)
        .getBrokerContext()
        .getPhysicalTenantModules()
        .context(BOOT_TENANT)
        .orElseThrow();
  }

  private JsonNode status() throws Exception {
    return JSON.readTree(send("GET", null).body());
  }

  private HttpResponse<String> send(final String method, final Map<String, String> body)
      throws Exception {
    return http.send(
        HttpRequest.newBuilder(broker.actuatorUri("physicalTenants", BOOT_TENANT))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? BodyPublishers.noBody()
                    : BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .build(),
        BodyHandlers.ofString());
  }
}
