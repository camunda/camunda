/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.util.CollectionUtil.isNotEmpty;
import static io.camunda.tasklist.util.OpenSearchUtil.SCROLL_KEEP_ALIVE_MS;
import static io.camunda.tasklist.util.OpenSearchUtil.createSearchRequest;
import static io.camunda.tasklist.v86.schema.indices.TasklistVariableIndex.ID;
import static io.camunda.tasklist.v86.schema.indices.TasklistVariableIndex.NAME;
import static io.camunda.tasklist.v86.schema.indices.TasklistVariableIndex.SCOPE_FLOW_NODE_ID;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.v86.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.v86.entities.TaskVariableEntity;
import io.camunda.tasklist.v86.entities.VariableEntity;
import io.camunda.tasklist.v86.schema.indices.TasklistFlowNodeInstanceIndex;
import io.camunda.tasklist.v86.schema.indices.TasklistVariableIndex;
import io.camunda.tasklist.v86.schema.templates.TasklistTaskVariableTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.ConstantScoreQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class VariableStoreOpenSearch implements VariableStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(VariableStoreOpenSearch.class);

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient osClient;

  @Autowired private TenantAwareOpenSearchClient tenantAwareClient;
  @Autowired private TasklistFlowNodeInstanceIndex flowNodeInstanceIndex;
  @Autowired private TasklistVariableIndex variableIndex;
  @Autowired private TasklistTaskVariableTemplate taskVariableTemplate;
  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      final List<String> flowNodeInstanceIds,
      final List<String> varNames,
      final Set<String> fieldNames) {

    final Query.Builder flowNodeInstanceKeyQ = new Query.Builder();
    flowNodeInstanceKeyQ.terms(
        terms ->
            terms
                .field(SCOPE_FLOW_NODE_ID)
                .terms(
                    t ->
                        t.value(
                            flowNodeInstanceIds.stream()
                                .map(m -> FieldValue.of(m))
                                .collect(toList()))));

    Query.Builder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = new Query.Builder();
      varNamesQ.terms(
          terms ->
              terms
                  .field(TasklistVariableIndex.NAME)
                  .terms(
                      t ->
                          t.value(varNames.stream().map(m -> FieldValue.of(m)).collect(toList()))));
    }
    final Query.Builder query = new Query.Builder();
    query.constantScore(
        new ConstantScoreQuery.Builder()
            .filter(OpenSearchUtil.joinWithAnd(flowNodeInstanceKeyQ, varNamesQ))
            .build());
    final SearchRequest.Builder searchRequest = new SearchRequest.Builder();
    searchRequest.index(variableIndex.getAlias()).query(query.build());
    applyFetchSourceForVariableIndex(searchRequest, fieldNames);

    try {
      return OpenSearchUtil.scroll(searchRequest, VariableEntity.class, osClient);
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public Map<String, List<TaskVariableEntity>> getTaskVariablesPerTaskId(
      final List<GetVariablesRequest> requests) {

    if (requests == null || requests.size() == 0) {
      return new HashMap<>();
    }
    final Query.Builder taskIdsQ = new Query.Builder();
    final List<String> ids =
        requests.stream().map(GetVariablesRequest::getTaskId).collect(toList());
    taskIdsQ.terms(
        terms ->
            terms
                .field(TasklistTaskVariableTemplate.TASK_ID)
                .terms(t -> t.value(ids.stream().map(m -> FieldValue.of(m)).collect(toList()))));

    final List<String> varNames =
        requests.stream()
            .map(GetVariablesRequest::getVarNames)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .distinct()
            .collect(toList());
    Query.Builder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = new Query.Builder();
      varNamesQ.terms(
          terms ->
              terms
                  .field(NAME)
                  .terms(
                      t ->
                          t.value(varNames.stream().map(m -> FieldValue.of(m)).collect(toList()))));
    }

    final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();

    final Query joins = OpenSearchUtil.joinWithAnd(taskIdsQ, varNamesQ);
    searchRequestBuilder.query(q -> q.constantScore(cs -> cs.filter(joins)));
    searchRequestBuilder.index(taskVariableTemplate.getAlias());
    applyFetchSourceForTaskVariableTemplate(
        searchRequestBuilder,
        requests
            .get(0)
            .getFieldNames()); // we assume here that all requests has the same list of fields

    try {
      final List<TaskVariableEntity> entities =
          OpenSearchUtil.scroll(searchRequestBuilder, TaskVariableEntity.class, osClient);
      return entities.stream()
          .collect(
              groupingBy(TaskVariableEntity::getTaskId, mapping(Function.identity(), toList())));
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public Map<String, String> getTaskVariablesIdsWithIndexByTaskIds(final List<String> taskIds) {
    final SearchRequest.Builder searchRequest =
        OpenSearchUtil.createSearchRequest(taskVariableTemplate)
            .query(
                q ->
                    q.terms(
                        terms ->
                            terms
                                .field(TasklistTaskVariableTemplate.TASK_ID)
                                .terms(
                                    t ->
                                        t.value(
                                            taskIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList())))))
            .fields(f -> f.field(TasklistTaskVariableTemplate.ID));

    try {
      return OpenSearchUtil.scrollIdsWithIndexToMap(searchRequest, osClient);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public void persistTaskVariables(final Collection<TaskVariableEntity> finalVariables) {
    final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
    final List<BulkOperation> operations = new ArrayList<BulkOperation>();
    for (final TaskVariableEntity variableEntity : finalVariables) {
      operations.add(createUpsertRequest(variableEntity));
    }
    bulkRequest.operations(operations);
    bulkRequest.refresh(Refresh.WaitFor);
    try {
      OpenSearchUtil.processBulkRequest(osClient, bulkRequest.build());
    } catch (final PersistenceException ex) {
      throw new TasklistRuntimeException(ex);
    }
  }

  @Override
  public List<FlowNodeInstanceEntity> getFlowNodeInstances(final List<String> processInstanceIds) {

    final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();
    searchRequestBuilder
        .index(flowNodeInstanceIndex.getAlias())
        .query(
            q ->
                q.constantScore(
                    cs ->
                        cs.filter(
                            f ->
                                f.terms(
                                    terms ->
                                        terms
                                            .field(
                                                TasklistFlowNodeInstanceIndex.PROCESS_INSTANCE_ID)
                                            .terms(
                                                t ->
                                                    t.value(
                                                        processInstanceIds.stream()
                                                            .map(m -> FieldValue.of(m))
                                                            .collect(toList())))))))
        .sort(
            sort ->
                sort.field(
                    f -> f.field(TasklistFlowNodeInstanceIndex.POSITION).order(SortOrder.Asc)))
        .size(tasklistProperties.getOpenSearch().getBatchSize());

    try {
      return OpenSearchUtil.scroll(searchRequestBuilder, FlowNodeInstanceEntity.class, osClient);
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all flow nodes: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public VariableEntity getRuntimeVariable(final String variableId, final Set<String> fieldNames) {

    final SearchRequest.Builder request = new SearchRequest.Builder();
    request.index(variableIndex.getAlias()).query(q -> q.ids(ids -> ids.values(variableId)));
    applyFetchSourceForVariableIndex(request, fieldNames);

    try {
      final SearchResponse<VariableEntity> response =
          tenantAwareClient.search(request, VariableEntity.class);
      if (response.hits().total().value() == 1L) {
        return response.hits().hits().get(0).source();
      } else if (response.hits().total().value() > 1L) {
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
  public TaskVariableEntity getTaskVariable(final String variableId, final Set<String> fieldNames) {

    final SearchRequest.Builder request = createSearchRequest(taskVariableTemplate);
    request.query(q -> q.ids(ids -> ids.values(variableId)));
    applyFetchSourceForTaskVariableTemplate(request, fieldNames);
    try {
      final SearchResponse<TaskVariableEntity> response =
          tenantAwareClient.search(request, TaskVariableEntity.class);
      if (response.hits().total().value() == 1L) {
        return response.hits().hits().get(0).source();
      } else if (response.hits().total().value() > 1L) {
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
  public List<String> getProcessInstanceIdsWithMatchingVars(
      final List<String> varNames, final List<String> varValues) {
    final List<Set<String>> listProcessIdsMatchingVars = new ArrayList<>();

    for (int i = 0; i < varNames.size(); i++) {
      final Query.Builder nameQ = new Query.Builder();
      final int finalI = i;
      nameQ.terms(
          terms ->
              terms
                  .field(TasklistVariableIndex.NAME)
                  .terms(
                      t ->
                          t.value(Collections.singletonList(FieldValue.of(varNames.get(finalI))))));

      final Query.Builder valueQ = new Query.Builder();
      valueQ.terms(
          terms ->
              terms
                  .field(TasklistVariableIndex.VALUE)
                  .terms(
                      t ->
                          t.value(
                              Collections.singletonList(FieldValue.of(varValues.get(finalI))))));
      final Query boolQuery = OpenSearchUtil.joinWithAnd(nameQ, valueQ);
      final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();
      searchRequestBuilder
          .index(variableIndex.getAlias())
          .query(q -> q.constantScore(cs -> cs.filter(boolQuery)))
          .scroll(timeBuilder -> timeBuilder.time(SCROLL_KEEP_ALIVE_MS));

      final Set<String> processInstanceIds = new HashSet<>();

      try {
        SearchResponse<VariableEntity> response =
            osClient.search(searchRequestBuilder.build(), VariableEntity.class);

        List<String> scrollProcessIds =
            response.hits().hits().stream()
                .map(hit -> hit.source().getProcessInstanceId())
                .collect(Collectors.toList());

        processInstanceIds.addAll(scrollProcessIds);

        final String scrollId = response.scrollId();

        while (!scrollProcessIds.isEmpty()) {
          final ScrollRequest scrollRequest =
              ScrollRequest.of(
                  builder ->
                      builder
                          .scrollId(scrollId)
                          .scroll(new Time.Builder().time(SCROLL_KEEP_ALIVE_MS).build()));

          response = osClient.scroll(scrollRequest, VariableEntity.class);
          scrollProcessIds =
              response.hits().hits().stream()
                  .map(hit -> hit.source().getProcessInstanceId())
                  .collect(Collectors.toList());

          processInstanceIds.addAll(scrollProcessIds);
        }

        OpenSearchUtil.clearScroll(scrollId, osClient);

        listProcessIdsMatchingVars.add(processInstanceIds);

      } catch (final IOException e) {
        final String message =
            String.format("Exception occurred while obtaining flowInstanceIds: %s", e.getMessage());
        throw new TasklistRuntimeException(message, e);
      }
    }

    // now find the intersection of all sets
    return new ArrayList<>(
        listProcessIdsMatchingVars.stream()
            .reduce(
                (set1, set2) -> {
                  set1.retainAll(set2);
                  return set1;
                })
            .orElse(Collections.emptySet()));
  }

  private BulkOperation createUpsertRequest(final TaskVariableEntity variableEntity) {
    return new BulkOperation.Builder()
        .update(
            UpdateOperation.of(
                i ->
                    i.index(taskVariableTemplate.getFullQualifiedName())
                        .id(variableEntity.getId())
                        .docAsUpsert(true)
                        .document(CommonUtils.getJsonObjectFromEntity(variableEntity))
                        .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT)))
        .build();
  }

  private void applyFetchSourceForVariableIndex(
      final SearchRequest.Builder searchSourceBuilder, final Set<String> fieldNames) {
    final String[] includesFields;
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames =
          TasklistVariableIndex.getElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(ID);
      elsFieldNames.add(NAME);
      elsFieldNames.add(SCOPE_FLOW_NODE_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchSourceBuilder.source(s -> s.filter(f -> f.includes(Arrays.asList(includesFields))));
    }
  }

  private void applyFetchSourceForTaskVariableTemplate(
      final SearchRequest.Builder searchRequestBuilder, final Set<String> fieldNames) {
    final String[] includesFields;
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames =
          TasklistTaskVariableTemplate.getElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(TasklistTaskVariableTemplate.ID);
      elsFieldNames.add(TasklistTaskVariableTemplate.NAME);
      elsFieldNames.add(TasklistTaskVariableTemplate.TASK_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchRequestBuilder.source(s -> s.filter(f -> f.includes(Arrays.asList(includesFields))));
    }
  }
}
