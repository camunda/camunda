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
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class UnassignClientFromTenantIntegrationTest {

  private static CamundaClient camundaClient;
  private static final String TENANT_ID = Strings.newRandomValidTenantId();

  @BeforeAll
  static void setup() {
    camundaClient.newCreateTenantCommand().tenantId(TENANT_ID).name("tenantName").send().join();
  }

  @Test
  void shouldUnassignClientFromTenant() {
    // given
    final var clientId = Strings.newRandomValidIdentityId();

    // first assign the client to the tenant
    camundaClient
        .newAssignClientToTenantCommand()
        .clientId(clientId)
        .tenantId(TENANT_ID)
        .send()
        .join();

    // verify client is assigned
    Awaitility.await("Client is assigned to the tenant")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> result =
                  camundaClient.newClientsByTenantSearchRequest(TENANT_ID).send().join();
              assertThat(result.items()).map(Client::getClientId).contains(clientId);
            });

    // when - unassign the client from the tenant
    camundaClient
        .newUnassignClientFromTenantCommand()
        .clientId(clientId)
        .tenantId(TENANT_ID)
        .send()
        .join();

    // then - verify client is no longer assigned
    Awaitility.await("Client is unassigned from the tenant")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final SearchResponse<Client> result =
                  camundaClient.newClientsByTenantSearchRequest(TENANT_ID).send().join();
              assertThat(result.items()).map(Client::getClientId).doesNotContain(clientId);
            });
  }

  @Test
  void shouldReturnNotFoundOnUnassigningClientFromTenantIfTenantDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignClientFromTenantCommand()
                    .clientId(Strings.newRandomValidIdentityId())
                    .tenantId(Strings.newRandomValidTenantId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("no tenant with this ID exists.");
  }

  @Test
  void shouldReturnNotFoundOnUnassigningClientFromTenantIfClientIsNotAssigned() {
    // given
    final var clientId = Strings.newRandomValidIdentityId();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignClientFromTenantCommand()
                    .clientId(clientId)
                    .tenantId(TENANT_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}
