/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.Client;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@CompatibilityTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class ClientsByTenantIntegrationTest {

  private static CamundaClient camundaClient;
  private static final String TENANT_ID = Strings.newRandomValidTenantId();

  @BeforeAll
  static void setup() {
    camundaClient.newCreateTenantCommand().tenantId(TENANT_ID).name("tenantName").send().join();
  }

  @Test
  void shouldSearchAssignedClientsByTenantAndSort() {
    // given
    final var firstClientId = "aClientId";
    final var secondClientId = "bClientId";

    // when
    camundaClient
        .newAssignClientToTenantCommand()
        .clientId(firstClientId)
        .tenantId(TENANT_ID)
        .send()
        .join();
    camundaClient
        .newAssignClientToTenantCommand()
        .clientId(secondClientId)
        .tenantId(TENANT_ID)
        .send()
        .join();

    // then
    Awaitility.await("Clients are assigned to the tenant and can be searched")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> result =
                  camundaClient
                      .newClientsByTenantSearchRequest(TENANT_ID)
                      .sort(s -> s.clientId().desc())
                      .send()
                      .join();
              assertThat(result.items())
                  .map(Client::getClientId)
                  .containsExactly(secondClientId, firstClientId);
            });
  }

  @Test
  void shouldReturnNotFoundOnAssigningClientToTenantIfTenantDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignClientToTenantCommand()
                    .clientId(Strings.newRandomValidIdentityId())
                    .tenantId(Strings.newRandomValidTenantId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("no tenant with this ID exists.");
  }

  @Test
  void searchClientsShouldReturnEmptyListWhenSearchingForNonExistingTenantId() {
    final var clientsSearchResponse =
        camundaClient.newClientsByTenantSearchRequest("someTenantId").send().join();
    assertThat(clientsSearchResponse.items()).isEmpty();
  }

  @Test
  void shouldRejectClientsByTenantSearchIfMissingTenantId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newClientsByTenantSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRejectClientsByTenantSearchIfEmptyTenantId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newClientsByTenantSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }
}
