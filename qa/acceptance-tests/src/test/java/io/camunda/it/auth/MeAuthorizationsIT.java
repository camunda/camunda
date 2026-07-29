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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.StreamSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Verifies {@code POST /v2/authentication/me/authorizations}
 * (https://github.com/camunda/camunda/issues/55892): the endpoint has no fluent Java client method
 * yet, so requests are issued as raw HTTP calls.
 *
 * <p>Note: authorizations cannot be granted to a tenant as owner — {@code EntityType} has no {@code
 * TENANT} value, only {@code USER}, {@code CLIENT}, {@code GROUP}, {@code ROLE}, and {@code
 * MAPPING_RULE} are valid authorization owners. This suite therefore covers direct/group/role
 * grants, not a tenant-owned grant.
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

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
          List.of(new Permissions(RESOURCE, CREATE, List.of("*"))),
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
      @Authenticated(ME) final CamundaClient meClient) {
    Awaitility.await()
        .untilAsserted(
            () -> {
              final JsonNode response = searchOwnAuthorizations(meClient, ME, null);
              final var items = itemsOf(response);

              assertThat(items)
                  .anySatisfy(
                      item -> {
                        assertThat(item.get("ownerId").asText()).isEqualTo(ME);
                        assertThat(item.get("ownerType").asText()).isEqualTo("USER");
                        assertThat(item.get("resourceType").asText())
                            .isEqualTo("PROCESS_DEFINITION");
                        assertThat(item.get("resourceId").asText()).isEqualTo(DIRECT_PROCESS_ID);
                      });
              assertThat(items)
                  .anySatisfy(
                      item -> {
                        assertThat(item.get("ownerId").asText()).isEqualTo(ME_GROUP.id());
                        assertThat(item.get("ownerType").asText()).isEqualTo("GROUP");
                        assertThat(item.get("resourceType").asText()).isEqualTo("RESOURCE");
                      });
              assertThat(items)
                  .anySatisfy(
                      item -> {
                        assertThat(item.get("ownerId").asText()).isEqualTo(ME_ROLE.id());
                        assertThat(item.get("ownerType").asText()).isEqualTo("ROLE");
                        assertThat(item.get("resourceType").asText()).isEqualTo("USER_TASK");
                      });

              // an authorization granted to an unrelated user must never show up
              assertThat(items)
                  .noneSatisfy(
                      item -> assertThat(item.get("ownerId").asText()).isEqualTo(STRANGER));
              assertThat(items)
                  .extracting(item -> item.get("resourceType").asText())
                  .doesNotContain("DECISION_DEFINITION");
            });
  }

  @Test
  void shouldFilterOwnAuthorizationsByResourceType(
      @Authenticated(ME) final CamundaClient meClient) {
    final var filterByProcessDefinition = "{\"filter\":{\"resourceType\":\"PROCESS_DEFINITION\"}}";
    Awaitility.await()
        .untilAsserted(
            () -> {
              final JsonNode response =
                  searchOwnAuthorizations(meClient, ME, filterByProcessDefinition);
              final var items = itemsOf(response);

              assertThat(items)
                  .hasSize(1)
                  .first()
                  .satisfies(
                      item -> {
                        assertThat(item.get("ownerId").asText()).isEqualTo(ME);
                        assertThat(item.get("resourceType").asText())
                            .isEqualTo("PROCESS_DEFINITION");
                        assertThat(item.get("resourceId").asText()).isEqualTo(DIRECT_PROCESS_ID);
                      });
            });
  }

  @Test
  void shouldReturnUnauthorizedWithoutCredentials(@Authenticated(ME) final CamundaClient meClient)
      throws Exception {
    // when
    final var request =
        HttpRequest.newBuilder()
            .uri(createUri(meClient, "v2/authentication/me/authorizations"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.noBody())
            .build();
    final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  private JsonNode searchOwnAuthorizations(
      final CamundaClient client, final String username, final String body) throws Exception {
    final var requestBuilder =
        HttpRequest.newBuilder()
            .uri(createUri(client, "v2/authentication/me/authorizations"))
            .header("Content-Type", "application/json")
            .header("Authorization", basicAuthentication(username));
    final var request =
        (body == null
                ? requestBuilder.POST(BodyPublishers.noBody())
                : requestBuilder.POST(BodyPublishers.ofString(body)))
            .build();
    final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    return OBJECT_MAPPER.readTree(response.body());
  }

  private static List<JsonNode> itemsOf(final JsonNode response) {
    return StreamSupport.stream(response.get("items").spliterator(), false).toList();
  }

  private static String basicAuthentication(final String username) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((username + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
  }

  private static URI createUri(final CamundaClient client, final String path)
      throws URISyntaxException {
    final String base = client.getConfiguration().getRestAddress().toString();
    final String separator = base.endsWith("/") ? "" : "/";
    return new URI(base + separator + path);
  }
}
