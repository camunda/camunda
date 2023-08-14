/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.schema.indices.VariableIndex.ID;
import static io.camunda.tasklist.schema.indices.VariableIndex.NAME;
import static io.camunda.tasklist.schema.indices.VariableIndex.SCOPE_FLOW_NODE_ID;
import static io.camunda.tasklist.util.CollectionUtil.isNotEmpty;
import static io.camunda.tasklist.util.OpenSearchUtil.createSearchRequest;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
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
import io.camunda.tasklist.util.OpenSearchUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.ConstantScoreQuery;
import org.opensearch.client.opensearch._types.query_dsl.FieldAndFormat;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
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
  @Qualifier("openSearchClient")
  private OpenSearchClient osClient;

  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;
  @Autowired private VariableIndex variableIndex;
  @Autowired private TaskVariableTemplate taskVariableTemplate;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private ObjectMapper objectMapper;

  public List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      List<String> flowNodeInstanceIds, List<String> varNames, final Set<String> fieldNames) {

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
                  .field(VariableIndex.NAME)
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
      return OpenSearchUtil.scroll(searchRequest, VariableEntity.class, objectMapper, osClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

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
            .flatMap(x -> x == null ? null : x.stream())
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
    applyFetchSourceForTaskVariableTemplate(
        searchRequestBuilder,
        requests
            .get(0)
            .getFieldNames()); // we assume here that all requests has the same list of fields

    try {
      final List<TaskVariableEntity> entities =
          OpenSearchUtil.scroll(
              searchRequestBuilder, TaskVariableEntity.class, objectMapper, osClient);
      return entities.stream()
          .collect(
              groupingBy(TaskVariableEntity::getTaskId, mapping(Function.identity(), toList())));
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public void persistTaskVariables(final Collection<TaskVariableEntity> finalVariables) {
    final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
    final List<BulkOperation> operations = new ArrayList<BulkOperation>();
    for (TaskVariableEntity variableEntity : finalVariables) {
      operations.add(createUpsertRequest(variableEntity));
    }
    bulkRequest.operations(operations);
    try {
      OpenSearchUtil.processBulkRequest(osClient, bulkRequest.build());
    } catch (PersistenceException ex) {
      throw new TasklistRuntimeException(ex);
    }
  }

  private BulkOperation createUpsertRequest(TaskVariableEntity variableEntity) {
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
                                            .field(FlowNodeInstanceIndex.PROCESS_INSTANCE_ID)
                                            .terms(
                                                t ->
                                                    t.value(
                                                        processInstanceIds.stream()
                                                            .map(m -> FieldValue.of(m))
                                                            .collect(toList())))))))
        .sort(sort -> sort.field(f -> f.field(FlowNodeInstanceIndex.POSITION).order(SortOrder.Asc)))
        .size(tasklistProperties.getElasticsearch().getBatchSize());

    try {
      return OpenSearchUtil.scroll(
          searchRequestBuilder, FlowNodeInstanceEntity.class, objectMapper, osClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all flow nodes: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public VariableEntity getRuntimeVariable(final String variableId, Set<String> fieldNames) {

    final SearchRequest.Builder request = new SearchRequest.Builder();
    request
        .index(variableIndex.getAlias())
        .source(s -> s.filter(sf -> sf.includes(List.of(variableId))));
    applyFetchSourceForVariableIndex(request, fieldNames);

    try {
      final SearchResponse<VariableEntity> response =
          osClient.search(request.build(), VariableEntity.class);
      if (response.hits().total().value() == 1L) {
        return response.hits().hits().get(0).source();
      } else if (response.hits().total().value() > 1L) {
        throw new NotFoundException(
            String.format("Unique variable with id %s was not found", variableId));
      } else {
        throw new NotFoundException(String.format("Variable with id %s was not found", variableId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variable: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public TaskVariableEntity getTaskVariable(final String variableId, Set<String> fieldNames) {

    final SearchRequest.Builder request = createSearchRequest(taskVariableTemplate);
    request.source(s -> s.filter(sf -> sf.includes(List.of(variableId))));
    applyFetchSourceForTaskVariableTemplate(request, fieldNames);
    try {
      final SearchResponse<TaskVariableEntity> response =
          osClient.search(request.build(), TaskVariableEntity.class);
      if (response.hits().total().value() == 1L) {
        return response.hits().hits().get(0).source();
      } else if (response.hits().total().value() > 1L) {
        throw new NotFoundException(
            String.format("Unique task variable with id %s was not found", variableId));
      } else {
        throw new NotFoundException(
            String.format("Task variable with id %s was not found", variableId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining task variable: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private void applyFetchSourceForVariableIndex(
      SearchRequest.Builder searchSourceBuilder, final Set<String> fieldNames) {
    final String[] includesFields;
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames = VariableIndex.getElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(ID);
      elsFieldNames.add(NAME);
      elsFieldNames.add(SCOPE_FLOW_NODE_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchSourceBuilder.fields(
          Arrays.stream(includesFields)
              .map(m -> new FieldAndFormat.Builder().field(m).build())
              .collect(toList()));
    }
  }

  private void applyFetchSourceForTaskVariableTemplate(
      SearchRequest.Builder searchRequestBuilder, final Set<String> fieldNames) {
    final String[] includesFields;
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames =
          TaskVariableTemplate.getElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(TaskVariableTemplate.ID);
      elsFieldNames.add(TaskVariableTemplate.NAME);
      elsFieldNames.add(TaskVariableTemplate.TASK_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchRequestBuilder.fields(
          Arrays.stream(includesFields)
              .map(m -> new FieldAndFormat.Builder().field(m).build())
              .collect(toList()));
    }
  }
}
