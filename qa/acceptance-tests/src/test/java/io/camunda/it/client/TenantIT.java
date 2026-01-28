/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.search.exception.ErrorMessages.ERROR_ENTITY_BY_ID_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class TenantIT {
  private static CamundaClient camundaClient;

  @Test
  void shouldCreateAndGetTenantById() {
    // given
    final var tenantId = Strings.newRandomValidTenantId();
    final var name = UUID.randomUUID().toString();
    final var description = UUID.randomUUID().toString();

    // when
    camundaClient
        .newCreateTenantCommand()
        .tenantId(tenantId)
        .name(name)
        .description(description)
        .send()
        .join();

    // then
    Awaitility.await("Tenant is created and exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var tenant = camundaClient.newTenantGetRequest(tenantId).send().join();
              assertThat(tenant).isNotNull();
              assertThat(tenant.getTenantId()).isEqualTo(tenantId);
              assertThat(tenant.getName()).isEqualTo(name);
              assertThat(tenant.getDescription()).isEqualTo(description);
            });
  }

  @Test
  void shouldReturnNotFoundWhenGettingNonExistingTenant() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newTenantGetRequest("someTenantId").send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            ERROR_ENTITY_BY_ID_NOT_FOUND.formatted("Tenant", "id", "someTenantId"));
  }

  @Test
  void shouldRejectGetTenantIfEmptyTenantId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newTenantGetRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  @Test
  void shouldRejectGetTenantIfNullTenantId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newTenantGetRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }
}
