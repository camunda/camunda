/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.it.util.SearchClientsUtil;
import io.camunda.zeebe.util.CloseableSilently;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TestRestTasklistClient implements CloseableSilently {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TaskTemplate TASK_INDEX = new TaskTemplate("", true);

  private final URI endpoint;
  private final HttpClient httpClient;
  private final BasicAuthentication authentication;
  private final DocumentBasedSearchClient searchClient;

  public TestRestTasklistClient(final URI endpoint) {
    this(
        endpoint,
        HttpClient.newHttpClient(),
        null, // intermediate solution - search client should be removed after migration all usage
        null);
  }

  public TestRestTasklistClient(final URI endpoint, final String elasticsearchUrl) {
    this(
        endpoint,
        HttpClient.newHttpClient(),
        SearchClientsUtil.createLowLevelSearchClient(elasticsearchUrl),
        null);
  }

  TestRestTasklistClient(
      final URI endpoint,
      final HttpClient httpClient,
      final DocumentBasedSearchClient searchClient,
      final BasicAuthentication authentication) {
    this.endpoint = endpoint;
    this.httpClient = httpClient;
    this.searchClient = searchClient;
    this.authentication = authentication;
  }

  public SearchQueryResponse<TaskEntity> searchJobBasedUserTasks(final SearchQuery query) {
    final var searchRequest =
        SearchQueryRequest.of(s -> s.query(query).index(TASK_INDEX.getAlias()));
    return searchClient.search(searchRequest, TaskEntity.class);
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

  private String getBasicAuthenticationHeader(final BasicAuthentication authentication) {
    final var basicAuth = "%s:%s".formatted(authentication.username(), authentication.password());
    return Base64.getEncoder().encodeToString(basicAuth.getBytes());
  }

  @Override
  public void close() {
    httpClient.close();
    if (searchClient != null) {
      searchClient.close();
    }
  }

  public TestRestTasklistClient withAuthentication(final String username, final String password) {
    return new TestRestTasklistClient(
        endpoint, httpClient, searchClient, new BasicAuthentication(username, password));
  }

  public record CreateProcessInstanceVariable(String name, Object value) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProcessDefinitionResponse(String bpmnProcessId) {}

  record BasicAuthentication(String username, String password) {}
}
