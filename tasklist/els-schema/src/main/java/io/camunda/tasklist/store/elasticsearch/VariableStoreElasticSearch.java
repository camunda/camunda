/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.CollectionUtil.isNotEmpty;
import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.NAME;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.SCOPE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.VALUE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.util.ElasticsearchTenantHelper;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.util.ElasticsearchUtil.QueryType;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class VariableStoreElasticSearch implements VariableStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(VariableStoreElasticSearch.class);

  @Autowired
  @Qualifier("tasklistEsClient")
  private ElasticsearchClient esClient;

  @Autowired private ElasticsearchTenantHelper tenantHelper;

  @Autowired private VariableTemplate variableIndex;

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  @Autowired private SnapshotTaskVariableTemplate taskVariableTemplate;

  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      final List<String> flowNodeInstanceIds,
      final List<String> varNames,
      final Set<String> fieldNames) {
    try {
      return ElasticsearchUtil.scrollInChunks(
          esClient,
          flowNodeInstanceIds,
          tasklistProperties.getElasticsearch().getMaxTermsCount(),
          chunk -> buildSearchVariablesByFlowNodeInstanceIdsRequest(chunk, varNames, fieldNames),
          VariableEntity.class);
    } catch (final Exception e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public Map<String, List<SnapshotTaskVariableEntity>> getTaskVariablesPerTaskId(
      final List<GetVariablesRequest> requests) {

    if (requests == null || requests.isEmpty()) {
      return new HashMap<>();
    }

    final var taskIds = requests.stream().map(GetVariablesRequest::getTaskId).collect(toList());
    final var taskIdsQuery =
        ElasticsearchUtil.termsQuery(SnapshotTaskVariableTemplate.TASK_ID, taskIds);

    final List<String> varNames =
        requests.stream()
            .map(GetVariablesRequest::getVarNames)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .distinct()
            .collect(toList());

    final var query =
        isNotEmpty(varNames)
            ? ElasticsearchUtil.joinWithAnd(
                taskIdsQuery, ElasticsearchUtil.termsQuery(VariableTemplate.NAME, varNames))
            : taskIdsQuery;
    final var constantScoreQuery = ElasticsearchUtil.constantScoreQuery(query);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(taskVariableTemplate.getAlias())
            .query(constantScoreQuery);

    // Apply fetch source using field names from the first request
    applyFetchSourceForTaskVariableTemplate(searchRequestBuilder, requests.get(0).getFieldNames());

    try {
      final List<SnapshotTaskVariableEntity> entities =
          ElasticsearchUtil.scrollAllToList(
              esClient, searchRequestBuilder, SnapshotTaskVariableEntity.class);

      return entities.stream()
          .collect(
              groupingBy(
                  SnapshotTaskVariableEntity::getTaskId, mapping(Function.identity(), toList())));
    } catch (final Exception e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public Map<String, String> getTaskVariablesIdsWithIndexByTaskIds(final List<String> taskIds) {
    final var query = ElasticsearchUtil.termsQuery(SnapshotTaskVariableTemplate.TASK_ID, taskIds);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(ElasticsearchUtil.whereToSearch(taskVariableTemplate, QueryType.ALL))
            .query(query)
            .source(s -> s.filter(f -> f.includes(SnapshotTaskVariableTemplate.ID)));

    try {
      return ElasticsearchUtil.scrollIdsWithIndexToMap(esClient, searchRequestBuilder);
    } catch (final Exception e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public void persistTaskVariables(final Collection<SnapshotTaskVariableEntity> finalVariables) {
    try {
      final var bulkOperations = finalVariables.stream().map(this::createUpsertRequest).toList();
      ElasticsearchUtil.executeBulkRequest(esClient, bulkOperations, Refresh.WaitFor);
    } catch (final IOException e) {
      throw new TasklistRuntimeException("Error persisting task variables", e);
    }
  }

  @Override
  public List<FlowNodeInstanceEntity> getFlowNodeInstances(final List<Long> processInstanceKeys) {
    try {
      return ElasticsearchUtil.scrollInChunks(
          esClient,
          processInstanceKeys,
          tasklistProperties.getElasticsearch().getMaxTermsCount(),
          this::buildSearchFlowNodeInstancesRequest,
          FlowNodeInstanceEntity.class);
    } catch (final Exception e) {
      final String message =
          String.format("Exception occurred, while obtaining all flow nodes: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public VariableEntity getRuntimeVariable(final String variableId, final Set<String> fieldNames) {
    final var query = ElasticsearchUtil.idsQuery(variableId);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var requestBuilder =
        new SearchRequest.Builder()
            .index(variableIndex.getFullQualifiedName())
            .query(tenantAwareQuery);

    applyFetchSourceForVariableIndex(requestBuilder, fieldNames);

    try {
      final var response = esClient.search(requestBuilder.build(), VariableEntity.class);
      if (response.hits().total().value() == 1) {
        return response.hits().hits().get(0).source();
      } else if (response.hits().total().value() > 1) {
        throw new NotFoundException(
            String.format("Unique variable with id %s was not found", variableId));
      } else {
        throw new NotFoundException(String.format("Variable with id %s was not found", variableId));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variable: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public SnapshotTaskVariableEntity getTaskVariable(
      final String variableId, final Set<String> fieldNames) {
    final var query = ElasticsearchUtil.idsQuery(variableId);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var requestBuilder =
        new SearchRequest.Builder().index(taskVariableTemplate.getAlias()).query(tenantAwareQuery);

    applyFetchSourceForTaskVariableTemplate(requestBuilder, fieldNames);

    try {
      final var response =
          esClient.search(requestBuilder.build(), SnapshotTaskVariableEntity.class);
      if (response.hits().total().value() == 1) {
        return response.hits().hits().get(0).source();
      } else if (response.hits().total().value() > 1) {
        throw new NotFoundException(
            String.format("Unique task variable with id %s was not found", variableId));
      } else {
        throw new NotFoundException(
            String.format("Task variable with id %s was not found", variableId));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining task variable: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<Long> getProcessInstanceKeysWithMatchingVars(
      final List<String> varNames, final List<String> varValues) {

    Set<Long> processInstanceKeys = null;

    for (int i = 0; i < varNames.size(); i++) {
      final var nameQuery = ElasticsearchUtil.termsQuery(NAME, varNames.get(i));
      final var valueQuery = ElasticsearchUtil.termsQuery(VALUE, varValues.get(i));
      final var query = ElasticsearchUtil.joinWithAnd(nameQuery, valueQuery);

      final var searchRequestBuilder =
          new SearchRequest.Builder()
              .index(variableIndex.getFullQualifiedName())
              .query(query)
              .source(s -> s.filter(f -> f.includes(PROCESS_INSTANCE_KEY)))
              .size(tasklistProperties.getElasticsearch().getBatchSize());

      final Set<Long> currentKeys;
      try {
        currentKeys =
            ElasticsearchUtil.scrollFieldToLongSet(
                esClient, searchRequestBuilder, PROCESS_INSTANCE_KEY);
      } catch (final Exception e) {
        final String message =
            String.format(
                "Exception occurred while obtaining flowNodeInstanceIds for variable %s: %s",
                varNames.get(i), e.getMessage());
        throw new TasklistRuntimeException(message, e);
      }
      // Early exit if empty result
      if (currentKeys.isEmpty()) {
        return Collections.emptyList();
      }
      if (processInstanceKeys == null) {
        processInstanceKeys = currentKeys;
      } else {
        processInstanceKeys.retainAll(currentKeys);
        if (processInstanceKeys.isEmpty()) {
          // Early exit if intersection is empty
          return Collections.emptyList();
        }
      }
    }
    return processInstanceKeys == null
        ? Collections.emptyList()
        : new ArrayList<>(processInstanceKeys);
  }

  private SearchRequest.Builder buildSearchVariablesByFlowNodeInstanceIdsRequest(
      final List<String> flowNodeInstanceIds,
      final List<String> varNames,
      final Set<String> fieldNames) {
    final var scopeKeyQuery = ElasticsearchUtil.termsQuery(SCOPE_KEY, flowNodeInstanceIds);
    final var query =
        isNotEmpty(varNames)
            ? ElasticsearchUtil.joinWithAnd(
                scopeKeyQuery, ElasticsearchUtil.termsQuery(VariableTemplate.NAME, varNames))
            : scopeKeyQuery;
    final var constantScoreQuery = ElasticsearchUtil.constantScoreQuery(query);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(variableIndex.getFullQualifiedName())
            .query(constantScoreQuery)
            .size(tasklistProperties.getElasticsearch().getBatchSize());

    applyFetchSourceForVariableIndex(searchRequestBuilder, fieldNames);
    return searchRequestBuilder;
  }

  private SearchRequest.Builder buildSearchFlowNodeInstancesRequest(
      final List<Long> processInstanceKeys) {
    final var processInstanceKeyQuery =
        ElasticsearchUtil.termsQuery(
            FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys);
    final var stateQuery =
        ElasticsearchUtil.termsQuery(FlowNodeInstanceTemplate.STATE, FlowNodeState.ACTIVE.name());
    final var typeQuery =
        ElasticsearchUtil.termsQuery(
            FlowNodeInstanceTemplate.TYPE,
            List.of(
                FlowNodeType.AD_HOC_SUB_PROCESS.name(),
                FlowNodeType.AD_HOC_SUB_PROCESS_INNER_INSTANCE.name(),
                FlowNodeType.USER_TASK.name(),
                FlowNodeType.SUB_PROCESS.name(),
                FlowNodeType.EVENT_SUB_PROCESS.name(),
                FlowNodeType.MULTI_INSTANCE_BODY.name(),
                FlowNodeType.PROCESS.name()));

    final var query = ElasticsearchUtil.joinWithAnd(processInstanceKeyQuery, stateQuery, typeQuery);
    final var constantScoreQuery = ElasticsearchUtil.constantScoreQuery(query);

    return new SearchRequest.Builder()
        .index(flowNodeInstanceIndex.getFullQualifiedName())
        .query(constantScoreQuery)
        .sort(ElasticsearchUtil.sortOrder(FlowNodeInstanceTemplate.POSITION, SortOrder.Asc))
        .size(tasklistProperties.getElasticsearch().getBatchSize());
  }

  private void applyFetchSourceForVariableIndex(
      final SearchRequest.Builder requestBuilder, final Set<String> fieldNames) {
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames =
          VariableStore.getVariableTemplateElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(ID);
      elsFieldNames.add(NAME);
      elsFieldNames.add(SCOPE_KEY);
      final var includesFields = elsFieldNames.toArray(new String[0]);
      requestBuilder.source(s -> s.filter(f -> f.includes(List.of(includesFields))));
    }
  }

  private void applyFetchSourceForTaskVariableTemplate(
      final SearchRequest.Builder requestBuilder, final Set<String> fieldNames) {
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames =
          VariableStore.getTaskVariableElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(SnapshotTaskVariableTemplate.ID);
      elsFieldNames.add(SnapshotTaskVariableTemplate.NAME);
      elsFieldNames.add(SnapshotTaskVariableTemplate.TASK_ID);
      final var includesFields = elsFieldNames.toArray(new String[0]);
      requestBuilder.source(s -> s.filter(f -> f.includes(List.of(includesFields))));
    }
  }

  private BulkOperation createUpsertRequest(final SnapshotTaskVariableEntity variableEntity) {
    return BulkOperation.of(
        op ->
            op.update(
                u ->
                    u.index(taskVariableTemplate.getFullQualifiedName())
                        .id(variableEntity.getId())
                        .retryOnConflict(UPDATE_RETRY_COUNT)
                        .action(a -> a.doc(variableEntity).docAsUpsert(true))));
  }
}
