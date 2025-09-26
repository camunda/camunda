/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.util.CollectionUtil.isNotEmpty;
import static io.camunda.tasklist.util.OpenSearchUtil.createSearchRequest;
import static io.camunda.tasklist.util.OpenSearchUtil.scrollInChunks;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.NAME;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.SCOPE_KEY;
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
  public Map<String, List<SnapshotTaskVariableEntity>> getTaskVariablesPerTaskId(
      final List<GetVariablesRequest> requests) {

    if (requests == null || requests.isEmpty()) {
      return new HashMap<>();
    }
    final Query.Builder taskIdsQ = new Query.Builder();
    final List<String> ids = requests.stream().map(GetVariablesRequest::getTaskId).toList();
    taskIdsQ.terms(
        terms ->
            terms
                .field(SnapshotTaskVariableTemplate.TASK_ID)
                .terms(t -> t.value(ids.stream().map(FieldValue::of).collect(toList()))));

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
                  .terms(t -> t.value(varNames.stream().map(FieldValue::of).collect(toList()))));
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
      final List<SnapshotTaskVariableEntity> entities =
          OpenSearchUtil.scroll(searchRequestBuilder, SnapshotTaskVariableEntity.class, osClient);
      return entities.stream()
          .collect(
              groupingBy(
                  SnapshotTaskVariableEntity::getTaskId, mapping(Function.identity(), toList())));
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
                                .field(SnapshotTaskVariableTemplate.TASK_ID)
                                .terms(
                                    t ->
                                        t.value(
                                            taskIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList())))))
            .fields(f -> f.field(SnapshotTaskVariableTemplate.ID));

    try {
      return OpenSearchUtil.scrollIdsWithIndexToMap(searchRequest, osClient);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public void persistTaskVariables(final Collection<SnapshotTaskVariableEntity> finalVariables) {
    final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
    final List<BulkOperation> operations = new ArrayList<BulkOperation>();
    for (final SnapshotTaskVariableEntity variableEntity : finalVariables) {
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
  public List<FlowNodeInstanceEntity> getFlowNodeInstances(final List<Long> processInstanceKeys) {
    try {
      return scrollInChunks(
          processInstanceKeys,
          tasklistProperties.getOpenSearch().getMaxTermsCount(),
          this::buildSearchFNIByProcessInstanceKeysRequest,
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
    request
        .index(variableIndex.getFullQualifiedName())
        .query(q -> q.ids(ids -> ids.values(variableId)));
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
  public SnapshotTaskVariableEntity getTaskVariable(
      final String variableId, final Set<String> fieldNames) {

    final SearchRequest.Builder request = createSearchRequest(taskVariableTemplate);
    request.query(q -> q.ids(ids -> ids.values(variableId)));
    applyFetchSourceForTaskVariableTemplate(request, fieldNames);
    try {
      final SearchResponse<SnapshotTaskVariableEntity> response =
          tenantAwareClient.search(request, SnapshotTaskVariableEntity.class);
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
  public List<Long> getProcessInstanceKeysWithMatchingVars(
      final List<String> varNames, final List<String> varValues) {
    Set<Long> processInstanceKeys = null;
    for (int i = 0; i < varNames.size(); i++) {
      final Query.Builder nameQ = new Query.Builder();
      final int finalI = i;
      nameQ.terms(
          terms ->
              terms
                  .field(VariableTemplate.NAME)
                  .terms(
                      t ->
                          t.value(Collections.singletonList(FieldValue.of(varNames.get(finalI))))));

      final Query.Builder valueQ = new Query.Builder();
      valueQ.terms(
          terms ->
              terms
                  .field(VariableTemplate.VALUE)
                  .terms(
                      t ->
                          t.value(
                              Collections.singletonList(FieldValue.of(varValues.get(finalI))))));
      final Query boolQuery = OpenSearchUtil.joinWithAnd(nameQ, valueQ);
      final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();
      searchRequestBuilder
          .index(variableIndex.getFullQualifiedName())
          .query(q -> q.constantScore(cs -> cs.filter(boolQuery)))
          .source(s -> s.filter(f -> f.includes(PROCESS_INSTANCE_KEY)))
          .size(tasklistProperties.getOpenSearch().getBatchSize());
      final Set<Long> currentKeys;
      try {
        currentKeys =
            new HashSet<>(
                OpenSearchUtil.scrollFieldToList(
                    searchRequestBuilder, PROCESS_INSTANCE_KEY, osClient));
      } catch (final IOException e) {
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

  private Builder buildSearchFNIByProcessInstanceKeysRequest(final List<Long> processInstanceKeys) {
    final var processInstanceKeyQuery =
        TermsQuery.of(
                t ->
                    t.field(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY)
                        .terms(
                            v ->
                                v.value(
                                    processInstanceKeys.stream()
                                        .map(FieldValue::of)
                                        .collect(toList()))))
            .toQuery();
    final var flowNodeInstanceStateQuery =
        TermQuery.of(
                t ->
                    t.field(FlowNodeInstanceTemplate.STATE)
                        .value(v -> v.stringValue(FlowNodeState.ACTIVE.toString())))
            .toQuery();

    final var typeQuery =
        new Query.Builder()
            .terms(
                terms ->
                    terms
                        .field(FlowNodeInstanceTemplate.TYPE)
                        .terms(
                            t ->
                                t.value(
                                    Arrays.asList(
                                        FieldValue.of(FlowNodeType.AD_HOC_SUB_PROCESS.toString()),
                                        FieldValue.of(
                                            FlowNodeType.AD_HOC_SUB_PROCESS_INNER_INSTANCE
                                                .toString()),
                                        FieldValue.of(FlowNodeType.USER_TASK.toString()),
                                        FieldValue.of(FlowNodeType.SUB_PROCESS.toString()),
                                        FieldValue.of(FlowNodeType.EVENT_SUB_PROCESS.toString()),
                                        FieldValue.of(FlowNodeType.MULTI_INSTANCE_BODY.toString()),
                                        FieldValue.of(FlowNodeType.PROCESS.toString())))))
            .build();

    final Query.Builder combinedQuery = new Query.Builder();
    combinedQuery.constantScore(
        cs ->
            cs.filter(
                OpenSearchUtil.joinWithAnd(
                    processInstanceKeyQuery, flowNodeInstanceStateQuery, typeQuery)));

    return new SearchRequest.Builder()
        .index(flowNodeInstanceIndex.getFullQualifiedName())
        .query(combinedQuery.build())
        .sort(
            sort ->
                sort.field(f -> f.field(FlowNodeInstanceTemplate.POSITION).order(SortOrder.Asc)))
        .size(tasklistProperties.getOpenSearch().getBatchSize());
  }

  private Builder buildSearchVariablesByScopeFNIsAndVarNamesRequest(
      final List<String> scopeFlowNodeIds,
      final List<String> varNames,
      final Set<String> fieldNames) {
    final Query.Builder flowNodeInstanceKeyQ = new Query.Builder();
    flowNodeInstanceKeyQ.terms(
        terms ->
            terms
                .field(SCOPE_KEY)
                .terms(t -> t.value(scopeFlowNodeIds.stream().map(FieldValue::of).toList())));

    Query.Builder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = new Query.Builder();
      varNamesQ.terms(
          terms ->
              terms
                  .field(VariableTemplate.NAME)
                  .terms(t -> t.value(varNames.stream().map(FieldValue::of).toList())));
    }
    final Query.Builder query = new Query.Builder();
    query.constantScore(
        new ConstantScoreQuery.Builder()
            .filter(OpenSearchUtil.joinWithAnd(flowNodeInstanceKeyQ, varNamesQ))
            .build());
    final SearchRequest.Builder searchRequest = new SearchRequest.Builder();
    searchRequest
        .index(variableIndex.getFullQualifiedName())
        .query(query.build())
        .size(tasklistProperties.getOpenSearch().getBatchSize());
    applyFetchSourceForVariableIndex(searchRequest, fieldNames);
    return searchRequest;
  }

  private BulkOperation createUpsertRequest(final SnapshotTaskVariableEntity variableEntity) {
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
          VariableStore.getVariableTemplateElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(ID);
      elsFieldNames.add(NAME);
      elsFieldNames.add(SCOPE_KEY);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchSourceBuilder.source(s -> s.filter(f -> f.includes(Arrays.asList(includesFields))));
    }
  }

  private void applyFetchSourceForTaskVariableTemplate(
      final SearchRequest.Builder searchRequestBuilder, final Set<String> fieldNames) {
    final String[] includesFields;
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames =
          VariableStore.getTaskVariableElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(SnapshotTaskVariableTemplate.ID);
      elsFieldNames.add(SnapshotTaskVariableTemplate.NAME);
      elsFieldNames.add(SnapshotTaskVariableTemplate.TASK_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchRequestBuilder.source(s -> s.filter(f -> f.includes(Arrays.asList(includesFields))));
    }
  }
}
