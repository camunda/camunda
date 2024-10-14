/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequest.ResourceTypeEnum;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequestPermissionsInner.PermissionTypeEnum;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.awaitility.Awaitility;

public class AuthorizationsUtil {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  public static ZeebeClient createUserWithPermissions(
      final TestStandaloneBroker broker,
      final ZeebeClient client,
      final String elasticsearchUrl,
      final String username,
      final String password,
      final Permissions... permissions)
      throws Exception {
    final var userCreateResponse =
        client
            .newUserCreateCommand()
            .username(username)
            .password(password)
            .name("name")
            .email("foo@bar.com")
            .send()
            .join();

    for (final Permissions permission : permissions) {
      client
          .newAddPermissionsCommand(userCreateResponse.getUserKey())
          .resourceType(permission.resourceType())
          .permission(permission.permissionType())
          .resourceIds(permission.resourceIds())
          .send()
          .join();
    }

    awaitUserExistsInElasticsearch(elasticsearchUrl, username);
    return createClientWithAuthorization(broker, username, password);
  }

  public static ZeebeClient createClientWithAuthorization(
      final TestStandaloneBroker broker, final String username, final String password) {
    return broker
        .newClientBuilder()
        .preferRestOverGrpc(true)
        .defaultRequestTimeout(Duration.ofSeconds(15))
        .credentialsProvider(
            new CredentialsProvider() {
              @Override
              public void applyCredentials(final CredentialsApplier applier) {
                applier.put(
                    "Authorization",
                    "Basic %s"
                        .formatted(
                            Base64.getEncoder()
                                .encodeToString("%s:%s".formatted(username, password).getBytes())));
              }

              @Override
              public boolean shouldRetryRequest(final StatusCode statusCode) {
                return false;
              }
            })
        .build();
  }

  public static void awaitUserExistsInElasticsearch(
      final String elasticsearchUrl, final String username) throws Exception {
    final var request =
        HttpRequest.newBuilder()
            .POST(
                BodyPublishers.ofString(
                    """
                    {
                      "query": {
                        "match": {
                          "username": "%s"
                        }
                      }
                    }"""
                        .formatted(username)))
            .uri(new URI("http://%s/users/_count/".formatted(elasticsearchUrl)))
            .header("Content-Type", "application/json")
            .build();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () -> {
              final var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
              final var userExistsResponse =
                  OBJECT_MAPPER.readValue(response.body(), UserExistsResponse.class);
              return userExistsResponse.count > 0;
            });
  }

  public record Permissions(
      ResourceTypeEnum resourceType, PermissionTypeEnum permissionType, List<String> resourceIds) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record UserExistsResponse(int count) {}
}
