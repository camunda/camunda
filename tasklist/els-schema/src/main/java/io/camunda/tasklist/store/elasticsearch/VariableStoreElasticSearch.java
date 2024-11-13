/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.schema.v86.indices.VariableIndex.ID;
import static io.camunda.tasklist.schema.v86.indices.VariableIndex.NAME;
import static io.camunda.tasklist.schema.v86.indices.VariableIndex.PROCESS_INSTANCE_ID;
import static io.camunda.tasklist.schema.v86.indices.VariableIndex.SCOPE_FLOW_NODE_ID;
import static io.camunda.tasklist.schema.v86.indices.VariableIndex.VALUE;
import static io.camunda.tasklist.util.CollectionUtil.isNotEmpty;
import static io.camunda.tasklist.util.ElasticsearchUtil.SCROLL_KEEP_ALIVE_MS;
import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.tasklist.util.ElasticsearchUtil.createSearchRequest;
import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.tasklist.util.ElasticsearchUtil.scroll;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.v86.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.v86.indices.VariableIndex;
import io.camunda.tasklist.schema.v86.templates.TaskVariableTemplate;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
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
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
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

  public List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      List<String> flowNodeInstanceIds, List<String> varNames, final Set<String> fieldNames) {
    final TermsQueryBuilder flowNodeInstanceKeyQ =
        termsQuery(SCOPE_FLOW_NODE_ID, flowNodeInstanceIds);
    TermsQueryBuilder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = termsQuery(VariableIndex.NAME, varNames);
    }
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(constantScoreQuery(joinWithAnd(flowNodeInstanceKeyQ, varNamesQ)));
    applyFetchSourceForVariableIndex(searchSourceBuilder, fieldNames);

    final SearchRequest searchRequest =
        new SearchRequest(variableIndex.getAlias()).source(searchSourceBuilder);
    try {
      return scroll(searchRequest, VariableEntity.class, objectMapper, esClient);
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
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public Map<String, String> getTaskVariablesIdsWithIndexByTaskIds(final List<String> taskIds) {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(taskVariableTemplate)
            .source(
                SearchSourceBuilder.searchSource()
                    .query(termsQuery(TaskVariableTemplate.TASK_ID, taskIds))
                    .fetchField(TaskVariableTemplate.ID));
    try {
      return ElasticsearchUtil.scrollIdsWithIndexToMap(searchRequest, esClient);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  public void persistTaskVariables(final Collection<TaskVariableEntity> finalVariables) {
    final BulkRequest bulkRequest = new BulkRequest();
    for (TaskVariableEntity variableEntity : finalVariables) {
      bulkRequest.add(createUpsertRequest(variableEntity));
    }
    try {
      ElasticsearchUtil.processBulkRequest(
          esClient, bulkRequest, WriteRequest.RefreshPolicy.WAIT_UNTIL);
    } catch (PersistenceException ex) {
      throw new TasklistRuntimeException(ex);
    }
  }

  private UpdateRequest createUpsertRequest(TaskVariableEntity variableEntity) {
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

    } catch (IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error preparing the query to upsert task variable instance [%s]",
              variableEntity.getId()),
          e);
    }
  }

  public List<FlowNodeInstanceEntity> getFlowNodeInstances(final List<String> processInstanceIds) {
    final TermsQueryBuilder processInstanceKeyQuery =
        termsQuery(FlowNodeInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds);
    final SearchRequest searchRequest =
        new SearchRequest(flowNodeInstanceIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(processInstanceKeyQuery))
                    .sort(FlowNodeInstanceIndex.POSITION, SortOrder.ASC)
                    .size(tasklistProperties.getElasticsearch().getBatchSize()));
    try {
      return scroll(searchRequest, FlowNodeInstanceEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all flow nodes: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public VariableEntity getRuntimeVariable(final String variableId, Set<String> fieldNames) {
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
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variable: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public TaskVariableEntity getTaskVariable(final String variableId, Set<String> fieldNames) {
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
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining task variable: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private void applyFetchSourceForVariableIndex(
      SearchSourceBuilder searchSourceBuilder, final Set<String> fieldNames) {
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
      SearchSourceBuilder searchSourceBuilder, final Set<String> fieldNames) {
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

  public List<String> getProcessInstanceIdsWithMatchingVars(
      List<String> varNames, List<String> varValues) {

    final List<Set<String>> listProcessIdsMatchingVars = new ArrayList<>();

    for (int i = 0; i < varNames.size(); i++) {
      final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
      boolQuery.must(QueryBuilders.termQuery(NAME, varNames.get(i)));
      boolQuery.must(QueryBuilders.termQuery(VALUE, varValues.get(i)));

      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder().query(boolQuery).fetchSource(PROCESS_INSTANCE_ID, null);

      final SearchRequest searchRequest =
          new SearchRequest(variableIndex.getAlias()).source(searchSourceBuilder);
      searchRequest.scroll(new TimeValue(SCROLL_KEEP_ALIVE_MS));

      final Set<String> processInstanceIds = new HashSet<>();

      try {
        SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
        String scrollId = searchResponse.getScrollId();

        List<String> scrollProcessIds =
            Arrays.stream(searchResponse.getHits().getHits())
                .map(hit -> (String) hit.getSourceAsMap().get(PROCESS_INSTANCE_ID))
                .collect(Collectors.toList());

        processInstanceIds.addAll(scrollProcessIds);

        while (scrollProcessIds.size() > 0) {
          final SearchScrollRequest scrollRequest =
              new SearchScrollRequest(scrollId).scroll(new TimeValue(SCROLL_KEEP_ALIVE_MS));

          searchResponse = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);
          scrollId = searchResponse.getScrollId();
          scrollProcessIds =
              Arrays.stream(searchResponse.getHits().getHits())
                  .map(hit -> (String) hit.getSourceAsMap().get(PROCESS_INSTANCE_ID))
                  .toList();
          processInstanceIds.addAll(scrollProcessIds);
        }

        final ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
        clearScrollRequest.addScrollId(scrollId);
        esClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);

        listProcessIdsMatchingVars.add(processInstanceIds);

      } catch (IOException e) {
        final String message =
            String.format(
                "Exception occurred while obtaining flowNodeInstanceIds for variable %s: %s",
                varNames.get(i), e.getMessage());
        throw new TasklistRuntimeException(message, e);
      }
    }

    // Find intersection of all sets
    return new ArrayList<>(
        listProcessIdsMatchingVars.stream()
            .reduce(
                (set1, set2) -> {
                  set1.retainAll(set2);
                  return set1;
                })
            .orElse(Collections.emptySet()));
  }
}
