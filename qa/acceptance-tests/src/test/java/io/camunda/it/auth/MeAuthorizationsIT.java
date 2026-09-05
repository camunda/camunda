/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_DECISION_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.DECISION_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.client.api.search.enums.ResourceType.USER_TASK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Verifies {@code POST /v2/authentication/me/authorizations/search}
 * (https://github.com/camunda/camunda/issues/55892): the endpoint returns authorizations applicable
 * to the authenticated principal — directly or via group/role membership.
 *
 * <p>Note: MAPPING_RULE and CLIENT owner-type tests require OIDC (keycloak) infrastructure and are
 * not covered by this basic-auth test class — see {@link MeAuthorizationsOidcIT} for those.
 * Authorizations cannot be granted to a tenant as owner — {@code EntityType} has no {@code TENANT}
 * value.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class MeAuthorizationsIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String PASSWORD = "password";
  private static final String ME = "meAuthorizationsUser";
  private static final String STRANGER = "meAuthorizationsStranger";
  private static final String DIRECT_PROCESS_ID = "meAuthorizationsDirectProcess";
  private static final String STRANGER_DECISION_ID = "meAuthorizationsStrangerDecision";
  private static final ObjectMapper JSON = new ObjectMapper();

  @UserDefinition
  private static final TestUser ME_USER =
      new TestUser(
          ME,
          PASSWORD,
          List.of(
              new Permissions(
                  PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of(DIRECT_PROCESS_ID))));

  @UserDefinition
  private static final TestUser STRANGER_USER =
      new TestUser(
          STRANGER,
          PASSWORD,
          List.of(
              new Permissions(
                  DECISION_DEFINITION, READ_DECISION_DEFINITION, List.of(STRANGER_DECISION_ID))));

  @GroupDefinition
  private static final TestGroup ME_GROUP =
      new TestGroup(
          "meAuthorizationsGroup",
          "meAuthorizationsGroup",
          List.of(Permissions.withWildcard(RESOURCE, CREATE)),
          List.of(new Membership(ME, EntityType.USER)));

  @RoleDefinition
  private static final TestRole ME_ROLE =
      new TestRole(
          "meAuthorizationsRole",
          "meAuthorizationsRole",
          List.of(Permissions.withWildcard(USER_TASK, READ)),
          List.of(new Membership(ME, EntityType.USER)));

  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldReturnAuthorizationsGrantedDirectlyViaGroupAndViaRole(
      @Authenticated(ME) final CamundaClient meClient) throws Exception {
    Awaitility.await()
        .untilAsserted(
            () -> {
              final SearchResponse<Authorization> response =
                  meClient.newOwnAuthorizationSearchRequest().send().join();
              final var items = response.items();

              assertThat(items)
                  .anySatisfy(
                      item -> {
                        assertThat(item.getOwnerId()).isEqualTo(ME);
                        assertThat(item.getOwnerType().name()).isEqualTo("USER");
                        assertThat(item.getResourceType().name()).isEqualTo("PROCESS_DEFINITION");
                        assertThat(item.getResourceId()).isEqualTo(DIRECT_PROCESS_ID);
                      });
              assertThat(items)
                  .anySatisfy(
                      item -> {
                        assertThat(item.getOwnerId()).isEqualTo(ME_GROUP.id());
                        assertThat(item.getOwnerType().name()).isEqualTo("GROUP");
                        assertThat(item.getResourceType().name()).isEqualTo("RESOURCE");
                        assertThat(item.getResourceId()).isEqualTo("*");
                      });
              assertThat(items)
                  .anySatisfy(
                      item -> {
                        assertThat(item.getOwnerId()).isEqualTo(ME_ROLE.id());
                        assertThat(item.getOwnerType().name()).isEqualTo("ROLE");
                        assertThat(item.getResourceType().name()).isEqualTo("USER_TASK");
                        assertThat(item.getResourceId()).isEqualTo("*");
                      });

              // an authorization granted to an unrelated user must never show up
              assertThat(items)
                  .noneSatisfy(item -> assertThat(item.getOwnerId()).isEqualTo(STRANGER));
              assertThat(items)
                  .extracting(item -> item.getResourceType().name())
                  .doesNotContain("DECISION_DEFINITION");
            });
    assertAuthorizationsAreEnabled(meClient);
  }

  @Test
  void shouldFilterOwnAuthorizationsByResourceType(@Authenticated(ME) final CamundaClient meClient)
      throws Exception {
    Awaitility.await()
        .untilAsserted(
            () -> {
              final SearchResponse<Authorization> response =
                  meClient
                      .newOwnAuthorizationSearchRequest()
                      .filter(f -> f.resourceType(PROCESS_DEFINITION))
                      .send()
                      .join();
              final var items = response.items();

              assertThat(items)
                  .hasSize(1)
                  .first()
                  .satisfies(
                      item -> {
                        assertThat(item.getOwnerId()).isEqualTo(ME);
                        assertThat(item.getResourceType().name()).isEqualTo("PROCESS_DEFINITION");
                        assertThat(item.getResourceId()).isEqualTo(DIRECT_PROCESS_ID);
                      });
            });
    assertAuthorizationsAreEnabled(meClient);
  }

  private void assertAuthorizationsAreEnabled(final CamundaClient client) throws Exception {
    // when
    final var request =
        HttpRequest.newBuilder()
            .uri(createUri(client, "v2/authentication/me/authorizations/search"))
            .header("Authorization", basicAuthentication())
            .POST(BodyPublishers.noBody())
            .build();
    final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
    final var responseBody = JSON.readTree(response.body());

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(responseBody.path("authorizationsEnabled").asBoolean()).isTrue();
  }

  @Test
  void shouldReturnUnauthorizedWithoutCredentials(@Authenticated(ME) final CamundaClient meClient)
      throws Exception {
    // when — no Authorization header
    final var request =
        HttpRequest.newBuilder()
            .uri(createUri(meClient, "v2/authentication/me/authorizations/search"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.noBody())
            .build();
    final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  private static String basicAuthentication() {
    return "Basic " + Base64.getEncoder().encodeToString((ME + ":" + PASSWORD).getBytes());
  }

  private static URI createUri(final CamundaClient client, final String path)
      throws URISyntaxException {
    final String base = client.getConfiguration().getRestAddress().toString();
    final String separator = base.endsWith("/") ? "" : "/";
    return new URI(base + separator + path);
  }
}
