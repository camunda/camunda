/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CreateAuthorizationResponse;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.protocol.rest.AuthorizationResult;
import io.camunda.client.protocol.rest.OwnerTypeEnum;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class AuthorizationIntegrationTest {

  private static CamundaClient camundaClient;

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Test
  void shouldCreateAndGetAuthorizationByAuthorizationKey() {
    // given
    final var ownerId = Strings.newRandomValidIdentityId();
    final var resourceId = Strings.newRandomValidIdentityId();

    // when
    final CreateAuthorizationResponse authorization =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(ownerId)
            .ownerType(OwnerType.USER)
            .resourceId(resourceId)
            .resourceType(ResourceType.RESOURCE)
            .permissionTypes(PermissionType.CREATE)
            .send()
            .join();
    final long authorizationKey = authorization.getAuthorizationKey();
    assertThat(authorizationKey).isGreaterThan(0);

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(
                        getAuthorization(
                            camundaClient.getConfiguration().getRestAddress().toString(),
                            authorizationKey))
                    .matches(
                        r ->
                            Objects.equals(
                                r.getAuthorizationKey(), String.valueOf(authorizationKey)))
                    .matches(r -> Objects.equals(r.getOwnerId(), ownerId))
                    .matches(r -> Objects.equals(r.getOwnerType(), OwnerTypeEnum.USER))
                    .matches(r -> Objects.equals(r.getResourceId(), resourceId))
                    .matches(r -> Objects.equals(r.getResourceType(), ResourceTypeEnum.RESOURCE))
                    .matches(
                        r ->
                            Objects.equals(
                                r.getPermissionTypes(), List.of(PermissionTypeEnum.CREATE))));
  }

  // TODO search authorizations command is unimplemented, remove the code below after implementation
  private static AuthorizationResult getAuthorization(
      final String restAddress, final long authorizationKey)
      throws URISyntaxException, IOException, InterruptedException {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI("%s%s%d".formatted(restAddress, "v2/authorizations/", authorizationKey)))
            .GET()
            .build();

    final HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), AuthorizationResult.class);
  }
}
