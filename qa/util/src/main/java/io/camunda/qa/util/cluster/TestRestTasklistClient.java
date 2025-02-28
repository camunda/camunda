/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.zeebe.util.CloseableSilently;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.commons.lang3.reflect.FieldUtils;

public class TestRestTasklistClient extends AbstractTestWebappClient<TestRestTasklistClient>
    implements CloseableSilently {

  private final TaskTemplate taskTemplate;

  private final ProcessIndex processIndex;

  public TestRestTasklistClient(final URI endpoint, final TasklistProperties tasklistProperties) {
    this(
        endpoint,
        HttpClient.newHttpClient(),
        createElasticsearchClient(
            tasklistProperties.getElasticsearch().getUrl(),
            tasklistProperties.getElasticsearch().getUsername(),
            tasklistProperties.getElasticsearch().getPassword()),
        initDescriptor(TaskTemplate::new, tasklistProperties),
        initDescriptor(ProcessIndex::new, tasklistProperties));
  }

  private TestRestTasklistClient(
      final URI endpoint,
      final HttpClient httpClient,
      final ElasticsearchClient elasticsearchClient,
      final TaskTemplate taskTemplate,
      final ProcessIndex processIndex) {
    super(endpoint, httpClient, elasticsearchClient);
    this.taskTemplate = taskTemplate;
    this.processIndex = processIndex;
  }

  private static <T> T initDescriptor(
      final Supplier<T> constructor, final TasklistProperties tasklistProperties) {
    final T descriptor = constructor.get();
    try {
      FieldUtils.writeField(descriptor, "tasklistProperties", tasklistProperties, true);
    } catch (final IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return descriptor;
  }

  public SearchQueryResponse<TaskEntity> searchUserTasks(final SearchQuery query) {
    final var searchRequest =
        SearchQueryRequest.of(s -> s.query(query).index(taskTemplate.getAlias()));
    return searchClient.search(searchRequest, TaskEntity.class).get();
  }

  public SearchQueryResponse<ProcessEntity> searchProcesses(final String processDefinitionId) {
    final var searchRequest =
        SearchQueryRequest.of(
            s ->
                s.query(
                        SearchQueryBuilders.term(
                            ProcessIndex.PROCESS_DEFINITION_ID, processDefinitionId))
                    .index(processIndex.getAlias()));
    return searchClient.search(searchRequest, ProcessEntity.class).get();
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

  private String mapToRequestBody(final String key, final Object value) {
    try {
      return OBJECT_MAPPER.writeValueAsString(Map.of(key, value));
    } catch (final Exception e) {
      throw new RuntimeException("Failed to map variables to request body", e);
    }
  }

  @Override
  protected TestRestTasklistClient create(final HttpClient httpClient) {
    return new TestRestTasklistClient(
        endpoint, httpClient, elasticsearchClient, taskTemplate, processIndex);
  }

  public record CreateProcessInstanceVariable(String name, Object value) {}
}
