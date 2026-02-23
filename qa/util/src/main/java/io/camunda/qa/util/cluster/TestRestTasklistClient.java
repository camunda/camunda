/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.client.CredentialsProvider;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.zeebe.util.CloseableSilently;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class TestRestTasklistClient implements CloseableSilently {

  public static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private final URI endpoint;
  private final HttpClient httpClient;
  private Consumer<Builder> authenticationApplier;

  public TestRestTasklistClient(final URI endpoint) {
    this.endpoint = endpoint;
    httpClient = HttpClient.newHttpClient();
  }

  public TestRestTasklistClient(final URI endpoint, final CredentialsProvider credentialsProvider) {
    this(endpoint);
    authenticationApplier = TestRestOperateClient.applyCredentialsProvider(credentialsProvider);
  }

  TestRestTasklistClient(
      final URI endpoint,
      final HttpClient httpClient,
      final String username,
      final String password) {
    this(endpoint);
    authenticationApplier = applyBasicAuthenticationHeader(username, password);
  }

  public HttpResponse<String> searchRequest(final String path, final String body) {
    final var formattedPath = String.format("%s%s", endpoint, path);
    return sendRequest("POST", formattedPath, body);
  }

  public HttpResponse<String> getRequest(final String path) {
    final var formattedPath = String.format("%s%s", endpoint, path);
    return sendRequest("GET", formattedPath, null);
  }

  public HttpResponse<String> searchTasks(final Long processInstanceKey) {
    final var path = String.format("%sv1/tasks/search", endpoint);
    return sendRequest(
        "POST",
        path,
        Optional.ofNullable(processInstanceKey)
            .map(a -> mapToRequestBody("processInstanceKey", a))
            .orElse(null));
  }

  public TaskSearchResponse[] searchAndParseTasks(final Long processInstanceKey)
      throws JsonProcessingException {
    return OBJECT_MAPPER.readValue(
        searchTasks(processInstanceKey).body(), TaskSearchResponse[].class);
  }

  public HttpResponse<String> saveDraftVariables(
      final Long taskKey, final String name, final String value) {
    final var path = String.format("%sv1/tasks/%d/variables", endpoint, taskKey);

    final var requestDtoVariableValue = new HashMap<String, Object>();
    requestDtoVariableValue.put("name", name);
    requestDtoVariableValue.put("value", value);

    return sendRequest(
        "POST", path, mapToRequestBody("variables", Arrays.asList(requestDtoVariableValue)));
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

  public HttpResponse<String> unassignUserTask(final long userTaskKey) {
    final var path = String.format("%sv1/tasks/%d/unassign", endpoint, userTaskKey);
    return sendRequest("PATCH", path, null);
  }

  public HttpResponse<String> completeUserTask(final long userTaskKey) {
    final var path = String.format("%sv1/tasks/%d/complete", endpoint, userTaskKey);
    return sendRequest("PATCH", path, null);
  }

  public HttpResponse<String> getProcessDefinition(final long processDefinitionKey) {
    final var path = String.format("%sv1/internal/processes/%d", endpoint, processDefinitionKey);
    return sendRequest("GET", path, null);
  }

  public List<ProcessDefinitionResponse> searchProcessDefinitions() {
    try {
      final var path = String.format("%sv1/internal/processes", endpoint);
      final var response = sendRequest("GET", path, null);
      final var result =
          OBJECT_MAPPER.readValue(response.body(), ProcessDefinitionResponse[].class);
      return Optional.ofNullable(result).map(Arrays::asList).orElseGet(Collections::emptyList);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
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

      if (authenticationApplier != null) {
        authenticationApplier.accept(requestBuilder);
      }

      final var request = requestBuilder.build();

      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final Exception e) {
      throw new RuntimeException("Failed to send request", e);
    }
  }

  private Consumer<Builder> applyBasicAuthenticationHeader(
      final String username, final String password) {
    final var basicAuth = "%s:%s".formatted(username, password);
    final var encodedBasicAuth = Base64.getEncoder().encodeToString(basicAuth.getBytes());
    return builder -> builder.header("Authorization", "Basic %s".formatted(encodedBasicAuth));
  }

  public URI getEndpoint() {
    return endpoint;
  }

  @Override
  public void close() {
    httpClient.close();
  }

  public TestRestTasklistClient withAuthentication(final String username, final String password) {
    return new TestRestTasklistClient(endpoint, httpClient, username, password);
  }

  public record CreateProcessInstanceVariable(String name, Object value) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProcessDefinitionResponse(String bpmnProcessId) {}
}
