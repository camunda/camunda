/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.Tenant;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class TenantsSearchIntegrationTest {

  private static CamundaClient camundaClient;

  private static final String TENANT_ID_1 = Strings.newRandomValidTenantId();
  private static final String TENANT_NAME_1 = "ATenantName";
  private static final String TENANT_ID_2 = Strings.newRandomValidTenantId();
  private static final String TENANT_NAME_2 = "BTenantName";

  @BeforeAll
  static void setup() {
    createTenant(TENANT_ID_1, TENANT_NAME_1, "description");
    assertTenantCreated(TENANT_ID_1, TENANT_NAME_1, "description");

    createTenant(TENANT_ID_2, TENANT_NAME_2, "description");
    assertTenantCreated(TENANT_ID_2, TENANT_NAME_2, "description");
  }

  @Test
  void searchShouldReturnTenantFilteredByTenantName() {
    final var tenantSearchResponse =
        camundaClient.newTenantsSearchRequest().filter(fn -> fn.name(TENANT_NAME_1)).send().join();

    assertThat(tenantSearchResponse.items())
        .hasSize(1)
        .map(Tenant::getName)
        .containsExactly(TENANT_NAME_1);
  }

  @Test
  void searchShouldReturnTenantsFilteredById() {
    final var tenantSearchResponse =
        camundaClient
            .newTenantsSearchRequest()
            .filter(fn -> fn.tenantId(TENANT_ID_1))
            .send()
            .join();

    assertThat(tenantSearchResponse.items())
        .hasSize(1)
        .map(Tenant::getTenantId)
        .containsExactly(TENANT_ID_1);
  }

  @Test
  void searchShouldReturnEmptyListWhenSearchingForNonExistingTenantId() {
    final var tenantSearchResponse =
        camundaClient
            .newTenantsSearchRequest()
            .filter(fn -> fn.tenantId("someTenantId"))
            .send()
            .join();
    assertThat(tenantSearchResponse.items()).isEmpty();
  }

  @Test
  void searchShouldReturnEmptyListWhenSearchingForNonExistingTenantName() {
    final var tenantSearchResponse =
        camundaClient
            .newTenantsSearchRequest()
            .filter(fn -> fn.name("someTenantName"))
            .send()
            .join();
    assertThat(tenantSearchResponse.items()).isEmpty();
  }

  @Test
  void searchShouldReturnTenantsSortedByName() {
    final var tenantSearchResponse =
        camundaClient.newTenantsSearchRequest().sort(s -> s.name().desc()).send().join();

    assertThat(tenantSearchResponse.items())
        .hasSizeGreaterThanOrEqualTo(2)
        .map(Tenant::getName)
        // filtering here as there is also "Default" tenant
        .filteredOn(r -> r.equals(TENANT_NAME_1) || r.equals(TENANT_NAME_2))
        .containsExactly(TENANT_NAME_2, TENANT_NAME_1);
  }

  private static void assertTenantCreated(
      final String tenantId, final String tenantName, final String description) {
    Awaitility.await("Tenant is created and exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var tenant = camundaClient.newTenantGetRequest(tenantId).send().join();
              assertThat(tenant).isNotNull();
              assertThat(tenant.getTenantId()).isEqualTo(tenantId);
              assertThat(tenant.getName()).isEqualTo(tenantName);
              assertThat(tenant.getDescription()).isEqualTo(description);
            });
  }

  private static void createTenant(
      final String tenantId, final String tenantName, final String description) {
    camundaClient
        .newCreateTenantCommand()
        .tenantId(tenantId)
        .name(tenantName)
        .description(description)
        .send()
        .join();
  }
}
