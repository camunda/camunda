/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexState;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ElasticsearchHelper implements NoSqlHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchHelper.class);

  private static final Integer QUERY_SIZE = 100;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private SnapshotTaskVariableTemplate taskVariableTemplate;

  @Autowired private VariableTemplate variableIndex;

  @Autowired
  @Qualifier("tasklistEs8Client")
  private ElasticsearchClient esClient;

  @Autowired private ObjectMapper objectMapper;

  @Override
  public TaskEntity getTask(final String taskId) {
    try {
      final Query query = ElasticsearchUtil.termsQuery(TaskTemplate.KEY, taskId);
      final SearchRequest searchRequest =
          SearchRequest.of(s -> s.index(taskTemplate.getAlias()).query(query));
      final SearchResponse<TaskEntity> response = esClient.search(searchRequest, TaskEntity.class);
      if (response.hits().hits().size() == 1) {
        return response.hits().hits().get(0).source();
      } else {
        throw new NotFoundApiException(
            String.format("Could not find  task for taskId [%s].", taskId));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the task: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<TaskEntity> getTask(final String processInstanceId, final String flowNodeBpmnId) {
    Query piIdQuery = null;
    if (processInstanceId != null) {
      piIdQuery = ElasticsearchUtil.termsQuery(TaskTemplate.PROCESS_INSTANCE_ID, processInstanceId);
    }
    final Query flowNodeQuery =
        ElasticsearchUtil.termsQuery(TaskTemplate.FLOW_NODE_BPMN_ID, flowNodeBpmnId);
    final Query combinedQuery = ElasticsearchUtil.joinWithAnd(piIdQuery, flowNodeQuery);

    final SearchRequest searchRequest =
        SearchRequest.of(
            s ->
                s.index(taskTemplate.getAlias())
                    .query(combinedQuery)
                    .sort(ElasticsearchUtil.sortOrder(TaskTemplate.CREATION_TIME, SortOrder.Desc)));

    try {
      final SearchResponse<TaskEntity> response = esClient.search(searchRequest, TaskEntity.class);
      if (response.hits().total() != null && response.hits().total().value() >= 1) {
        return response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
      } else {
        throw new NotFoundApiException(
            String.format(
                "Could not find task for processInstanceId [%s] with flowNodeBpmnId [%s].",
                processInstanceId, flowNodeBpmnId));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public boolean checkTaskVariableExists(final String taskId, final String varName) {
    final Query taskIdQuery =
        ElasticsearchUtil.termsQuery(SnapshotTaskVariableTemplate.TASK_ID, taskId);
    final Query varNameQuery =
        ElasticsearchUtil.termsQuery(SnapshotTaskVariableTemplate.NAME, varName);
    final Query combinedQuery = ElasticsearchUtil.joinWithAnd(taskIdQuery, varNameQuery);

    try {
      final var countResponse =
          esClient.count(c -> c.index(taskVariableTemplate.getAlias()).query(combinedQuery));
      return countResponse.count() > 0;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public boolean checkVariablesExist(final String processInstanceId, final String[] varNames) {
    final Query scopeQuery =
        ElasticsearchUtil.termsQuery(VariableTemplate.SCOPE_KEY, processInstanceId);
    final Query varNamesQuery =
        ElasticsearchUtil.termsQuery(VariableTemplate.NAME, Arrays.asList(varNames));
    final Query combinedQuery = ElasticsearchUtil.joinWithAnd(scopeQuery, varNamesQuery);

    try {
      final var countResponse =
          esClient.count(c -> c.index(variableIndex.getFullQualifiedName()).query(combinedQuery));
      return countResponse.count() == varNames.length;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<TaskEntity> getTasksFromIdAndIndex(final String index, final List<String> ids) {
    final List<String> safeIds =
        Arrays.asList(CollectionUtil.toSafeArrayOfStrings(ids)).stream().toList();
    final Query query = ElasticsearchUtil.termsQuery(TaskTemplate.KEY, safeIds);

    final SearchRequest searchRequest =
        SearchRequest.of(s -> s.index(index).query(query).size(QUERY_SIZE));

    try {
      final SearchResponse<TaskEntity> response = esClient.search(searchRequest, TaskEntity.class);
      return response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<TaskEntity> getAllTasks(final String index) {
    try {
      final Query query =
          ElasticsearchUtil.constantScoreQuery(ElasticsearchUtil.matchAllQueryEs8());

      final SearchRequest searchRequest =
          SearchRequest.of(s -> s.index(index).query(query).size(100));

      final SearchResponse<TaskEntity> response = esClient.search(searchRequest, TaskEntity.class);
      return response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Long countIndexResult(final String index) {
    try {
      final var countResponse = esClient.count(c -> c.index(index));
      return countResponse.count();
    } catch (final IOException e) {
      return -1L;
    }
  }

  @Override
  public Boolean isIndexDynamicMapping(final IndexDescriptor indexDescriptor, final String dynamic)
      throws IOException {
    final GetIndexRequest request =
        GetIndexRequest.of(r -> r.index(indexDescriptor.getFullQualifiedName()));
    final GetIndexResponse response = esClient.indices().get(request);

    final IndexState indexState = response.get(indexDescriptor.getFullQualifiedName());
    if (indexState != null && indexState.mappings() != null) {
      final DynamicMapping dynamicValue = indexState.mappings().dynamic();
      return dynamicValue != null && dynamicValue.jsonValue().equals(dynamic);
    }
    return false;
  }

  @Override
  public Map<String, Object> getFieldDescription(final IndexDescriptor indexDescriptor)
      throws IOException {
    final GetIndexRequest request =
        GetIndexRequest.of(r -> r.index(indexDescriptor.getFullQualifiedName()));
    final GetIndexResponse response = esClient.indices().get(request);

    final IndexState indexState = response.get(indexDescriptor.getFullQualifiedName());
    if (indexState != null && indexState.mappings() != null) {
      final var properties = indexState.mappings().properties();
      // Convert to Map<String, Object> for compatibility
      final Map<String, Object> result = new HashMap<>();
      properties.forEach((key, value) -> result.put(key, value));
      return result;
    }
    return Map.of();
  }

  @Override
  public Boolean indexHasAlias(final String index, final String alias) throws IOException {
    final GetIndexRequest request = GetIndexRequest.of(r -> r.index(index));
    final GetIndexResponse response = esClient.indices().get(request);

    final IndexState indexState = response.get(index);
    if (indexState != null && indexState.aliases() != null) {
      return indexState.aliases().size() == 1 && indexState.aliases().containsKey(alias);
    }
    return false;
  }

  @Override
  public void delete(final String index, final String id) throws IOException {
    final DeleteRequest request = DeleteRequest.of(r -> r.index(index).id(id));
    esClient.delete(request);
  }

  @Override
  public void update(final String index, final String id, final Map<String, Object> jsonMap)
      throws IOException {
    final UpdateRequest<Map<String, Object>, Map<String, Object>> request =
        UpdateRequest.of(r -> r.index(index).id(id).doc(jsonMap));
    esClient.update(request, Map.class);
  }
}
