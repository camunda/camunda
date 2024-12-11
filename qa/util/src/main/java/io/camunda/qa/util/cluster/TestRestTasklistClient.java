/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TestRestTasklistClient implements AutoCloseable {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final URI endpoint;
  private final String elasticsearchURL;
  private final HttpClient httpClient;
  private final BasicAuthentication authentication;

  public TestRestTasklistClient(final URI endpoint, final String elasticsearchURL) {
    this(endpoint, elasticsearchURL, HttpClient.newHttpClient(), null);
  }

  TestRestTasklistClient(
      final URI endpoint,
      final String elasticsearchURL,
      final HttpClient httpClient,
      final BasicAuthentication authentication) {
    this.endpoint = endpoint;
    this.elasticsearchURL = elasticsearchURL;
    this.httpClient = httpClient;
    this.authentication = authentication;
  }

  public HttpResponse<String> createProcessInstanceViaPublicForm(final String processDefinitionId) {
    return createProcessInstanceViaPublicForm(processDefinitionId, null);
  }

  public HttpResponse<String> createProcessInstanceViaPublicForm(
      final String processDefinitionId, final List<CreateProcessInstanceVariable> variables) {
    final var path = String.format("%sv1/external/process/%s/start", endpoint, processDefinitionId);
    return sendRequest("PATCH", path, null);
  }

  public HttpResponse<String> createProcessInstance(final String processDefinitionId) {
    return createProcessInstance(processDefinitionId, null);
  }

  public HttpResponse<String> createProcessInstance(
      final String processDefinitionId, final List<CreateProcessInstanceVariable> variables) {
    final var path =
        String.format("%sv1/internal/processes/%s/start", endpoint, processDefinitionId);
    return sendRequest(
        "PATCH",
        path,
        Optional.ofNullable(variables).map(v -> mapToRequestBody("variables", v)).orElse(null));
  }

  public HttpResponse<String> assignUserTask(final long userTaskKey, final String assignee) {
    final var path = String.format("%sv1/tasks/%d/assign", endpoint, userTaskKey);
    return sendRequest(
        "PATCH",
        path,
        Optional.ofNullable(assignee).map(a -> mapToRequestBody("assignee", a)).orElse(null));
  }

  private String mapToRequestBody(final String key, final Object value) {
    try {
      return OBJECT_MAPPER.writeValueAsString(Map.of(key, value));
    } catch (final Exception e) {
      throw new RuntimeException("Failed to map variables to request body", e);
    }
  }

  private HttpResponse<String> sendRequest(
      final String method, final String path, final String body) {
    try {
      final var requestBody = Optional.ofNullable(body).orElse("{}");
      final var requestBuilder =
          HttpRequest.newBuilder()
              .uri(new URI(path))
              .header("content-type", "application/json")
              .method(method, HttpRequest.BodyPublishers.ofString(requestBody));

      if (authentication != null) {
        final var basicAuth = getBasicAuthenticationHeader(authentication);
        requestBuilder.header("Authorization", "Basic %s".formatted(basicAuth));
      }

      final var request = requestBuilder.build();

      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final Exception e) {
      throw new RuntimeException("Failed to send request", e);
    }
  }

  private static String getBasicAuthenticationHeader(final BasicAuthentication authentication) {
    final var basicAuth = "%s:%s".formatted(authentication.username(), authentication.password());
    return Base64.getEncoder().encodeToString(basicAuth.getBytes());
  }

  @Override
  public void close() {
    httpClient.close();
  }

  public TestRestTasklistClient withAuthentication(final String username, final String password) {
    return new TestRestTasklistClient(
        endpoint, elasticsearchURL, httpClient, new BasicAuthentication(username, password));
  }

  public record CreateProcessInstanceVariable(String name, Object value) {}

  record BasicAuthentication(String username, String password) {}
}
