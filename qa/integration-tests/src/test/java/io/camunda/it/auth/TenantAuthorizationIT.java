/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.protocol.rest.PermissionTypeEnum.CREATE;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.READ;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.TENANT;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.collection.Tuple;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;

@Tag("multi-db-test")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class TenantAuthorizationIT {

  public static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  public static final String PASSWORD = "password";
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  @RegisterExtension
  static final CamundaMultiDBExtension EXTENSION = new CamundaMultiDBExtension(BROKER);

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String UNAUTHORIZED = "unauthorizedUser";

  @UserDefinition
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          PASSWORD,
          List.of(
              new Permissions(TENANT, CREATE, List.of("*")),
              new Permissions(TENANT, READ, List.of("*"))));

  @UserDefinition
  private static final User RESTRICTED_USER =
      new User(
          RESTRICTED,
          PASSWORD,
          List.of(new Permissions(TENANT, READ, List.of("tenant1", "tenant2"))));

  @UserDefinition
  private static final User UNAUTHORIZED_USER = new User(UNAUTHORIZED, PASSWORD, List.of());

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private boolean initialized;

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createTenant(adminClient, "tenant1");
    createTenant(adminClient, "tenant2");
    // Expected count is 3 because a default tenant gets created
    waitForTenantsToBeCreated(adminClient.getConfiguration().getRestAddress().toString(), ADMIN, 3);
  }

  @Test
  void searchShouldReturnAuthorizedTenants(
      @Authenticated(RESTRICTED) final CamundaClient userClient) throws Exception {
    // when
    final var tenantSearchResponse =
        searchTenants(userClient.getConfiguration().getRestAddress().toString(), RESTRICTED);

    // then
    assertThat(tenantSearchResponse.items())
        .hasSize(2)
        .map(TenantResponse::tenantId)
        .containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  void searchShouldReturnEmptyListWhenUnauthorized(
      @Authenticated(UNAUTHORIZED) final CamundaClient userClient) throws Exception {
    // when
    final var tenantSearchResponse =
        searchTenants(userClient.getConfiguration().getRestAddress().toString(), UNAUTHORIZED);

    // then
    assertThat(tenantSearchResponse.items()).isEmpty();
  }

  @Test
  void getByIdShouldReturnAuthorizedTenant(
      @Authenticated(RESTRICTED) final CamundaClient userClient) throws Exception {
    // when
    final var tenant =
        getTenantById(
            userClient.getConfiguration().getRestAddress().toString(), RESTRICTED, "tenant1");

    // then
    assertThat(tenant.isRight()).isTrue();
    assertThat(tenant.get().tenantId()).isEqualTo("tenant1");
  }

  @Test
  void getByIdShouldReturnForbiddenForUnauthorizedTenantId(
      @Authenticated(UNAUTHORIZED) final CamundaClient userClient) throws Exception {
    // when
    final var tenant =
        getTenantById(
            userClient.getConfiguration().getRestAddress().toString(), UNAUTHORIZED, "tenant1");

    // then
    assertThat(tenant.isLeft()).isTrue();
    final var statusCode = tenant.getLeft().getLeft();
    final var responseBody = tenant.getLeft().getRight();
    assertThat(statusCode).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(responseBody)
        .contains("Unauthorized to perform operation 'READ' on resource 'TENANT'");
  }

  private static void createTenant(final CamundaClient adminClient, final String tenantId) {
    adminClient
        .newCreateTenantCommand()
        .tenantId(tenantId)
        .name(UUID.randomUUID().toString())
        .send()
        .join();
  }

  private static void waitForTenantsToBeCreated(
      final String restAddress, final String username, final int expectedCount) {
    Awaitility.await("should create tenants and import in ES")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var tenantSearchResponse = searchTenants(restAddress, username);

              // Validate the response
              assert tenantSearchResponse.items().size() == expectedCount;
            });
  }

  // TODO once available, this test should use the client to make the request
  private static TenantSearchResponse searchTenants(final String restAddress, final String username)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted(username, PASSWORD).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, "v2/tenants/search")))
            .POST(BodyPublishers.ofString(""))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    // Send the request and get the response
    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    return OBJECT_MAPPER.readValue(response.body(), TenantSearchResponse.class);
  }

  // TODO once available, this test should use the client to make the request
  private static Either<Tuple<HttpStatus, String>, TenantResponse> getTenantById(
      final String restAddress, final String username, final String tenantId)
      throws URISyntaxException, IOException, InterruptedException {
    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted(username, PASSWORD).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s".formatted(restAddress, "v2/tenants/%s".formatted(tenantId))))
            .GET()
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    // Send the request and get the response
    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == HttpStatus.OK.value()) {
      return Either.right(OBJECT_MAPPER.readValue(response.body(), TenantResponse.class));
    }

    return Either.left(Tuple.of(HttpStatus.resolve(response.statusCode()), response.body()));
  }

  private record TenantSearchResponse(List<TenantResponse> items) {}

  private record TenantResponse(String tenantId) {}
}
