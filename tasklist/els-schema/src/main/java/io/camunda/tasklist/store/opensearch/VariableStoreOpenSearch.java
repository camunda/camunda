/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.schema.indices.ProcessInstanceDependant.PROCESS_INSTANCE_ID;
import static io.camunda.tasklist.schema.indices.VariableIndex.ID;
import static io.camunda.tasklist.schema.indices.VariableIndex.NAME;
import static io.camunda.tasklist.schema.indices.VariableIndex.SCOPE_FLOW_NODE_ID;
import static io.camunda.tasklist.util.CollectionUtil.isNotEmpty;
import static io.camunda.tasklist.util.OpenSearchUtil.createSearchRequest;
import static io.camunda.tasklist.util.OpenSearchUtil.scrollInChunks;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.entities.FlowNodeState;
import io.camunda.tasklist.entities.FlowNodeType;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.util.OpenSearchUtil;
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
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ConstantScoreQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.SearchRequest.Builder;
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
  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;
  @Autowired private VariableIndex variableIndex;
  @Autowired private TaskVariableTemplate taskVariableTemplate;
  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      final List<String> flowNodeInstanceIds,
      final List<String> varNames,
      final Set<String> fieldNames) {
    try {
      return OpenSearchUtil.scrollInChunks(
          flowNodeInstanceIds,
          tasklistProperties.getOpenSearch().getMaxTermsCount(),
          chunk -> buildSearchVariablesByScopeFNIsAndVarNamesRequest(chunk, varNames, fieldNames),
          VariableEntity.class,
          osClient);
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
                .field(TaskVariableTemplate.TASK_ID)
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
                                .field(TaskVariableTemplate.TASK_ID)
                                .terms(
                                    t ->
                                        t.value(
                                            taskIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList())))))
            .fields(f -> f.field(TaskVariableTemplate.ID));

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
    try {

      return scrollInChunks(
          processInstanceIds,
          tasklistProperties.getOpenSearch().getMaxTermsCount(),
          this::buildSearchFNIByProcessInstanceIdsRequest,
          FlowNodeInstanceEntity.class,
          osClient);
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
    Set<String> processInstanceIds = null;
    for (int i = 0; i < varNames.size(); i++) {
      final Query.Builder nameQ = new Query.Builder();
      final int finalI = i;
      nameQ.terms(
          terms ->
              terms
                  .field(VariableIndex.NAME)
                  .terms(
                      t ->
                          t.value(Collections.singletonList(FieldValue.of(varNames.get(finalI))))));

      final Query.Builder valueQ = new Query.Builder();
      valueQ.terms(
          terms ->
              terms
                  .field(VariableIndex.VALUE)
                  .terms(
                      t ->
                          t.value(
                              Collections.singletonList(FieldValue.of(varValues.get(finalI))))));
      final Query boolQuery = OpenSearchUtil.joinWithAnd(nameQ, valueQ);
      final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();
      searchRequestBuilder
          .index(variableIndex.getAlias())
          .query(q -> q.constantScore(cs -> cs.filter(boolQuery)))
          .source(s -> s.filter(f -> f.includes(PROCESS_INSTANCE_ID)))
          .size(tasklistProperties.getOpenSearch().getBatchSize());
      final Set<String> currentIds;
      try {
        currentIds =
            new HashSet<>(
                OpenSearchUtil.scrollFieldToList(
                    searchRequestBuilder, PROCESS_INSTANCE_ID, osClient));
      } catch (final IOException e) {
        final String message =
            String.format(
                "Exception occurred while obtaining flowNodeInstanceIds for variable %s: %s",
                varNames.get(i), e.getMessage());
        throw new TasklistRuntimeException(message, e);
      }
      // Early exit if empty result
      if (currentIds.isEmpty()) {
        return Collections.emptyList();
      }
      if (processInstanceIds == null) {
        processInstanceIds = currentIds;
      } else {
        processInstanceIds.retainAll(currentIds);
        if (processInstanceIds.isEmpty()) {
          // Early exit if intersection is empty
          return Collections.emptyList();
        }
      }
    }
    return processInstanceIds == null
        ? Collections.emptyList()
        : new ArrayList<>(processInstanceIds);
  }

  private Builder buildSearchFNIByProcessInstanceIdsRequest(final List<String> processInstanceIds) {
    final TermsQuery processInstanceKeyQuery =
        TermsQuery.of(
            t ->
                t.field(FlowNodeInstanceIndex.PROCESS_INSTANCE_ID)
                    .terms(
                        terms ->
                            terms.value(processInstanceIds.stream().map(FieldValue::of).toList())));

    final TermQuery stateActiveQuery =
        TermQuery.of(
            t ->
                t.field(FlowNodeInstanceIndex.STATE)
                    .value(FieldValue.of(FlowNodeState.ACTIVE.name())));
    final BoolQuery stateMissingQuery =
        BoolQuery.of(b -> b.mustNot(q -> q.exists(e -> e.field(FlowNodeInstanceIndex.STATE))));
    final BoolQuery stateQuery =
        BoolQuery.of(
            b ->
                b.should(q -> q.term(stateActiveQuery))
                    .should(q -> q.bool(stateMissingQuery))
                    .minimumShouldMatch("1"));

    final TermsQuery typeQuery =
        TermsQuery.of(
            t ->
                t.field(FlowNodeInstanceIndex.TYPE)
                    .terms(
                        terms ->
                            terms.value(
                                Arrays.asList(
                                    FieldValue.of(FlowNodeType.AD_HOC_SUB_PROCESS.toString()),
                                    FieldValue.of(FlowNodeType.USER_TASK.toString()),
                                    FieldValue.of(FlowNodeType.SUB_PROCESS.toString()),
                                    FieldValue.of(FlowNodeType.EVENT_SUB_PROCESS.toString()),
                                    FieldValue.of(FlowNodeType.MULTI_INSTANCE_BODY.toString()),
                                    FieldValue.of(FlowNodeType.PROCESS.toString())))));

    final BoolQuery finalQuery =
        BoolQuery.of(
            b ->
                b.must(q -> q.terms(processInstanceKeyQuery))
                    .must(q -> q.terms(typeQuery))
                    .must(q -> q.bool(stateQuery)));

    final Query.Builder combinedQuery = new Query.Builder();
    combinedQuery.constantScore(cs -> cs.filter(q -> q.bool(finalQuery)));

    return new Builder()
        .index(flowNodeInstanceIndex.getAlias())
        .query(combinedQuery.build())
        .sort(sort -> sort.field(f -> f.field(FlowNodeInstanceIndex.POSITION).order(SortOrder.Asc)))
        .size(tasklistProperties.getOpenSearch().getBatchSize());
  }

  private Builder buildSearchVariablesByScopeFNIsAndVarNamesRequest(
      final List<String> scopeFlowNodeIds,
      final List<String> varNames,
      final Set<String> fieldNames) {
    final var flowNodeInstanceKeyQ = new Query.Builder();
    flowNodeInstanceKeyQ.terms(
        terms ->
            terms
                .field(SCOPE_FLOW_NODE_ID)
                .terms(t -> t.value(scopeFlowNodeIds.stream().map(FieldValue::of).toList())));

    Query.Builder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = new Query.Builder();
      varNamesQ.terms(
          terms ->
              terms
                  .field(VariableIndex.NAME)
                  .terms(t -> t.value(varNames.stream().map(FieldValue::of).toList())));
    }
    final Query.Builder query = new Query.Builder();
    query.constantScore(
        new ConstantScoreQuery.Builder()
            .filter(OpenSearchUtil.joinWithAnd(flowNodeInstanceKeyQ, varNamesQ))
            .build());
    final Builder searchRequest = new Builder();
    searchRequest
        .index(variableIndex.getAlias())
        .query(query.build())
        .size(tasklistProperties.getOpenSearch().getBatchSize());
    applyFetchSourceForVariableIndex(searchRequest, fieldNames);
    return searchRequest;
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
      final Set<String> elsFieldNames = VariableIndex.getElsFieldsByGraphqlFields(fieldNames);
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
          TaskVariableTemplate.getElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(TaskVariableTemplate.ID);
      elsFieldNames.add(TaskVariableTemplate.NAME);
      elsFieldNames.add(TaskVariableTemplate.TASK_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchRequestBuilder.source(s -> s.filter(f -> f.includes(Arrays.asList(includesFields))));
    }
  }
}
