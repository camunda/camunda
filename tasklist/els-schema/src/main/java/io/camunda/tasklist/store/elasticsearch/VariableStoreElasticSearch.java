/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.schema.indices.VariableIndex.ID;
import static io.camunda.tasklist.schema.indices.VariableIndex.NAME;
import static io.camunda.tasklist.schema.indices.VariableIndex.PROCESS_INSTANCE_ID;
import static io.camunda.tasklist.schema.indices.VariableIndex.SCOPE_FLOW_NODE_ID;
import static io.camunda.tasklist.schema.indices.VariableIndex.VALUE;
import static io.camunda.tasklist.util.CollectionUtil.isNotEmpty;
import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.tasklist.util.ElasticsearchUtil.createSearchRequest;
import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.tasklist.util.ElasticsearchUtil.scroll;
import static io.camunda.tasklist.util.ElasticsearchUtil.scrollInChunks;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
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
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.XContentType;
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
  private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;
  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;
  @Autowired private VariableIndex variableIndex;
  @Autowired private TaskVariableTemplate taskVariableTemplate;
  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      final List<String> flowNodeInstanceIds,
      final List<String> varNames,
      final Set<String> fieldNames) {

    try {
      return scrollInChunks(
          flowNodeInstanceIds,
          tasklistProperties.getElasticsearch().getMaxTermsCount(),
          chunk -> buildSearchVariablesByScopeFNIsAndVarNamesRequest(chunk, varNames, fieldNames),
          VariableEntity.class,
          objectMapper,
          esClient);
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public Map<String, List<TaskVariableEntity>> getTaskVariablesPerTaskId(
      final List<GetVariablesRequest> requests) {

    if (requests == null || requests.isEmpty()) {
      return new HashMap<>();
    }

    final TermsQueryBuilder taskIdsQ =
        termsQuery(
            TaskVariableTemplate.TASK_ID,
            requests.stream().map(GetVariablesRequest::getTaskId).collect(toList()));
    final List<String> varNames =
        requests.stream()
            .map(GetVariablesRequest::getVarNames)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .distinct()
            .collect(toList());
    TermsQueryBuilder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = termsQuery(VariableIndex.NAME, varNames);
    }

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(constantScoreQuery(joinWithAnd(taskIdsQ, varNamesQ)));
    applyFetchSourceForTaskVariableTemplate(
        searchSourceBuilder,
        requests
            .get(0)
            .getFieldNames()); // we assume here that all requests has the same list of fields

    final SearchRequest searchRequest =
        new SearchRequest(taskVariableTemplate.getAlias()).source(searchSourceBuilder);
    try {
      final List<TaskVariableEntity> entities =
          scroll(searchRequest, TaskVariableEntity.class, objectMapper, esClient);
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
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(taskVariableTemplate)
            .source(
                SearchSourceBuilder.searchSource()
                    .query(termsQuery(TaskVariableTemplate.TASK_ID, taskIds))
                    .fetchField(TaskVariableTemplate.ID));
    try {
      return ElasticsearchUtil.scrollIdsWithIndexToMap(searchRequest, esClient);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public void persistTaskVariables(final Collection<TaskVariableEntity> finalVariables) {
    final BulkRequest bulkRequest = new BulkRequest();
    for (final TaskVariableEntity variableEntity : finalVariables) {
      bulkRequest.add(createUpsertRequest(variableEntity));
    }
    try {
      ElasticsearchUtil.processBulkRequest(
          esClient, bulkRequest, WriteRequest.RefreshPolicy.WAIT_UNTIL);
    } catch (final PersistenceException ex) {
      throw new TasklistRuntimeException(ex);
    }
  }

  @Override
  public List<FlowNodeInstanceEntity> getFlowNodeInstances(final List<String> processInstanceIds) {
    try {
      return scrollInChunks(
          processInstanceIds,
          tasklistProperties.getElasticsearch().getMaxTermsCount(),
          this::buildSearchFNIByProcessInstanceIdsRequest,
          FlowNodeInstanceEntity.class,
          objectMapper,
          esClient);
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all flow nodes: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public VariableEntity getRuntimeVariable(final String variableId, final Set<String> fieldNames) {
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(idsQuery().addIds(variableId));
    applyFetchSourceForVariableIndex(searchSourceBuilder, fieldNames);
    final SearchRequest request =
        new SearchRequest(variableIndex.getAlias()).source(searchSourceBuilder);
    try {
      final SearchResponse response = tenantAwareClient.search(request);
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(
            response.getHits().getHits()[0].getSourceAsString(),
            objectMapper,
            VariableEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
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
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(idsQuery().addIds(variableId));
    applyFetchSourceForTaskVariableTemplate(searchSourceBuilder, fieldNames);
    final SearchRequest request =
        createSearchRequest(taskVariableTemplate).source(searchSourceBuilder);
    try {
      final SearchResponse response = tenantAwareClient.search(request);
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(
            response.getHits().getHits()[0].getSourceAsString(),
            objectMapper,
            TaskVariableEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
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
      final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
      boolQuery.must(QueryBuilders.termQuery(NAME, varNames.get(i)));
      boolQuery.must(QueryBuilders.termQuery(VALUE, varValues.get(i)));

      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(boolQuery)
              .fetchSource(PROCESS_INSTANCE_ID, null)
              .size(tasklistProperties.getElasticsearch().getBatchSize());

      final SearchRequest searchRequest =
          new SearchRequest(variableIndex.getAlias()).source(searchSourceBuilder);

      final Set<String> currentIds;
      try {
        currentIds =
            new HashSet<>(
                ElasticsearchUtil.scrollFieldToList(searchRequest, PROCESS_INSTANCE_ID, esClient));
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

  private SearchRequest buildSearchFNIByProcessInstanceIdsRequest(
      final List<String> processInstanceIds) {
    final BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

    final TermsQueryBuilder processInstanceKeyQuery =
        termsQuery(FlowNodeInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds);

    final TermQueryBuilder stateActiveQuery =
        termQuery(FlowNodeInstanceIndex.STATE, FlowNodeState.ACTIVE.name());
    final BoolQueryBuilder stateMissingQuery =
        QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(FlowNodeInstanceIndex.STATE));
    final BoolQueryBuilder stateQuery =
        QueryBuilders.boolQuery()
            .should(stateActiveQuery)
            .should(stateMissingQuery)
            .minimumShouldMatch(1);

    final TermsQueryBuilder typeQuery =
        QueryBuilders.termsQuery(
            FlowNodeInstanceIndex.TYPE,
            FlowNodeType.AD_HOC_SUB_PROCESS.toString(),
            FlowNodeType.USER_TASK.toString(),
            FlowNodeType.SUB_PROCESS.toString(),
            FlowNodeType.EVENT_SUB_PROCESS.toString(),
            FlowNodeType.MULTI_INSTANCE_BODY.toString(),
            FlowNodeType.PROCESS.toString());

    queryBuilder.must(processInstanceKeyQuery);
    queryBuilder.must(stateQuery);
    queryBuilder.must(typeQuery);

    return new SearchRequest(flowNodeInstanceIndex.getAlias())
        .source(
            new SearchSourceBuilder()
                .query(constantScoreQuery(queryBuilder))
                .sort(FlowNodeInstanceIndex.POSITION, SortOrder.ASC)
                .size(tasklistProperties.getElasticsearch().getBatchSize()));
  }

  private SearchRequest buildSearchVariablesByScopeFNIsAndVarNamesRequest(
      final List<String> scopeFlowNodeIds,
      final List<String> varNames,
      final Set<String> fieldNames) {
    final TermsQueryBuilder flowNodeInstanceKeyQ = termsQuery(SCOPE_FLOW_NODE_ID, scopeFlowNodeIds);
    TermsQueryBuilder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = termsQuery(VariableIndex.NAME, varNames);
    }
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(constantScoreQuery(joinWithAnd(flowNodeInstanceKeyQ, varNamesQ)))
            .size(tasklistProperties.getElasticsearch().getBatchSize());
    applyFetchSourceForVariableIndex(searchSourceBuilder, fieldNames);

    return new SearchRequest(variableIndex.getAlias()).source(searchSourceBuilder);
  }

  private UpdateRequest createUpsertRequest(final TaskVariableEntity variableEntity) {
    try {
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TaskVariableTemplate.TASK_ID, variableEntity.getTaskId());
      updateFields.put(TaskVariableTemplate.NAME, variableEntity.getName());
      updateFields.put(TaskVariableTemplate.VALUE, variableEntity.getValue());

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return new UpdateRequest()
          .index(taskVariableTemplate.getFullQualifiedName())
          .id(variableEntity.getId())
          .upsert(objectMapper.writeValueAsString(variableEntity), XContentType.JSON)
          .doc(jsonMap)
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error preparing the query to upsert task variable instance [%s]",
              variableEntity.getId()),
          e);
    }
  }

  private void applyFetchSourceForVariableIndex(
      final SearchSourceBuilder searchSourceBuilder, final Set<String> fieldNames) {
    final String[] includesFields;
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames = VariableIndex.getElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(ID);
      elsFieldNames.add(NAME);
      elsFieldNames.add(SCOPE_FLOW_NODE_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchSourceBuilder.fetchSource(includesFields, null);
    }
  }

  private void applyFetchSourceForTaskVariableTemplate(
      final SearchSourceBuilder searchSourceBuilder, final Set<String> fieldNames) {
    final String[] includesFields;
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames =
          TaskVariableTemplate.getElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(TaskVariableTemplate.ID);
      elsFieldNames.add(TaskVariableTemplate.NAME);
      elsFieldNames.add(TaskVariableTemplate.TASK_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchSourceBuilder.fetchSource(includesFields, null);
    }
  }
}
