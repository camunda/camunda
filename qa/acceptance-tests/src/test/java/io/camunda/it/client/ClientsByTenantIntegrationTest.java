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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.assertj.core.api.AssertionsForClassTypes;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class ClientsByTenantIntegrationTest {

  private static CamundaClient camundaClient;
  private static final String TENANT_ID = Strings.newRandomValidIdentityId();

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @BeforeAll
  static void setup() {
    camundaClient.newCreateTenantCommand().tenantId(TENANT_ID).name("tenantName").send().join();
  }

  @Test
  void shouldSearchClientsByTenantAndSort()
      throws URISyntaxException, IOException, InterruptedException {
    // given
    final var firstClientId = "aClientId";
    final var secondClientId = "bClientId";

    // when
    assignClientToTenant(
        camundaClient.getConfiguration().getRestAddress().toString(), TENANT_ID, firstClientId);
    assignClientToTenant(
        camundaClient.getConfiguration().getRestAddress().toString(), TENANT_ID, secondClientId);

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

  // TODO once available in #35767, this test should use the client to make the request
  private static void assignClientToTenant(
      final String restAddress, final String tenantId, final String clientId)
      throws URISyntaxException, IOException, InterruptedException {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "%s%s%s%s%s"
                        .formatted(restAddress, "v2/tenants/", tenantId, "/clients/", clientId)))
            .PUT(BodyPublishers.ofString(""))
            .build();

    // Send the request and get the response
    final HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString());

    AssertionsForClassTypes.assertThat(response.statusCode()).isEqualTo(204);
  }
}
