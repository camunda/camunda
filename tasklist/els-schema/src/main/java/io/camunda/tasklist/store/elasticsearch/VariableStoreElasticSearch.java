/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.CollectionUtil.isNotEmpty;
import static io.camunda.tasklist.util.ElasticsearchUtil.SCROLL_KEEP_ALIVE_MS;
import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.tasklist.util.ElasticsearchUtil.createSearchRequest;
import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.tasklist.util.ElasticsearchUtil.scroll;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.NAME;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.SCOPE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.VALUE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
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

  @Autowired
  @Qualifier("tasklistVariableTemplate")
  private VariableTemplate variableIndex;

  @Autowired
  @Qualifier("tasklistFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  @Autowired
  @Qualifier("tasklistSnapshotTaskVariableTemplate")
  private SnapshotTaskVariableTemplate taskVariableTemplate;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      final List<String> flowNodeInstanceIds,
      final List<String> varNames,
      final Set<String> fieldNames) {
    final TermsQueryBuilder flowNodeInstanceKeyQ = termsQuery(SCOPE_KEY, flowNodeInstanceIds);
    TermsQueryBuilder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = termsQuery(VariableTemplate.NAME, varNames);
    }
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(constantScoreQuery(joinWithAnd(flowNodeInstanceKeyQ, varNamesQ)));
    applyFetchSourceForVariableIndex(searchSourceBuilder, fieldNames);

    final SearchRequest searchRequest =
        new SearchRequest(variableIndex.getFullQualifiedName()).source(searchSourceBuilder);
    try {
      return scroll(searchRequest, VariableEntity.class, objectMapper, esClient);
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public Map<String, List<SnapshotTaskVariableEntity>> getTaskVariablesPerTaskId(
      final List<GetVariablesRequest> requests) {

    if (requests == null || requests.size() == 0) {
      return new HashMap<>();
    }

    final TermsQueryBuilder taskIdsQ =
        termsQuery(
            SnapshotTaskVariableTemplate.TASK_ID,
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
      varNamesQ = termsQuery(VariableTemplate.NAME, varNames);
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
      final List<SnapshotTaskVariableEntity> entities =
          scroll(searchRequest, SnapshotTaskVariableEntity.class, objectMapper, esClient);
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
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(taskVariableTemplate)
            .source(
                SearchSourceBuilder.searchSource()
                    .query(termsQuery(SnapshotTaskVariableTemplate.TASK_ID, taskIds))
                    .fetchField(SnapshotTaskVariableTemplate.ID));
    try {
      return ElasticsearchUtil.scrollIdsWithIndexToMap(searchRequest, esClient);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public void persistTaskVariables(final Collection<SnapshotTaskVariableEntity> finalVariables) {
    final BulkRequest bulkRequest = new BulkRequest();
    for (final SnapshotTaskVariableEntity variableEntity : finalVariables) {
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
    final TermsQueryBuilder processInstanceKeyQuery =
        termsQuery(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceIds);
    final var flowNodeInstanceStateQuery =
        termsQuery(FlowNodeInstanceTemplate.STATE, FlowNodeState.ACTIVE.toString());
    final var query =
        ElasticsearchUtil.joinWithAnd(processInstanceKeyQuery, flowNodeInstanceStateQuery);
    final SearchRequest searchRequest =
        new SearchRequest(flowNodeInstanceIndex.getFullQualifiedName())
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(query))
                    .sort(FlowNodeInstanceTemplate.POSITION, SortOrder.ASC)
                    .size(tasklistProperties.getElasticsearch().getBatchSize()));
    try {
      return scroll(searchRequest, FlowNodeInstanceEntity.class, objectMapper, esClient);
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
        new SearchRequest(variableIndex.getFullQualifiedName()).source(searchSourceBuilder);
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
  public SnapshotTaskVariableEntity getTaskVariable(
      final String variableId, final Set<String> fieldNames) {
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
            SnapshotTaskVariableEntity.class);
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

    final List<Set<String>> listProcessIdsMatchingVars = new ArrayList<>();

    for (int i = 0; i < varNames.size(); i++) {
      final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
      boolQuery.must(QueryBuilders.termQuery(NAME, varNames.get(i)));
      boolQuery.must(QueryBuilders.termQuery(VALUE, varValues.get(i)));

      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder().query(boolQuery).fetchSource(PROCESS_INSTANCE_KEY, null);

      final SearchRequest searchRequest =
          new SearchRequest(variableIndex.getFullQualifiedName()).source(searchSourceBuilder);
      searchRequest.scroll(new TimeValue(SCROLL_KEEP_ALIVE_MS));

      final Set<String> processInstanceIds = new HashSet<>();

      try {
        SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
        String scrollId = searchResponse.getScrollId();

        List<String> scrollProcessIds =
            Arrays.stream(searchResponse.getHits().getHits())
                .map(hit -> hit.getSourceAsMap().get(PROCESS_INSTANCE_KEY))
                .map(String::valueOf)
                .collect(Collectors.toList());

        processInstanceIds.addAll(scrollProcessIds);

        while (scrollProcessIds.size() > 0) {
          final SearchScrollRequest scrollRequest =
              new SearchScrollRequest(scrollId).scroll(new TimeValue(SCROLL_KEEP_ALIVE_MS));

          searchResponse = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);
          scrollId = searchResponse.getScrollId();
          scrollProcessIds =
              Arrays.stream(searchResponse.getHits().getHits())
                  .map(hit -> hit.getSourceAsMap().get(PROCESS_INSTANCE_KEY))
                  .map(String::valueOf)
                  .toList();
          processInstanceIds.addAll(scrollProcessIds);
        }

        final ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
        clearScrollRequest.addScrollId(scrollId);
        esClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);

        listProcessIdsMatchingVars.add(processInstanceIds);

      } catch (final IOException e) {
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

  private UpdateRequest createUpsertRequest(final SnapshotTaskVariableEntity variableEntity) {
    try {
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(SnapshotTaskVariableTemplate.TASK_ID, variableEntity.getTaskId());
      updateFields.put(SnapshotTaskVariableTemplate.NAME, variableEntity.getName());
      updateFields.put(SnapshotTaskVariableTemplate.VALUE, variableEntity.getValue());

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
      final Set<String> elsFieldNames =
          VariableStore.getVariableTemplateElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(ID);
      elsFieldNames.add(NAME);
      elsFieldNames.add(SCOPE_KEY);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchSourceBuilder.fetchSource(includesFields, null);
    }
  }

  private void applyFetchSourceForTaskVariableTemplate(
      final SearchSourceBuilder searchSourceBuilder, final Set<String> fieldNames) {
    final String[] includesFields;
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames =
          VariableStore.getTaskVariableElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(SnapshotTaskVariableTemplate.ID);
      elsFieldNames.add(SnapshotTaskVariableTemplate.NAME);
      elsFieldNames.add(SnapshotTaskVariableTemplate.TASK_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchSourceBuilder.fetchSource(includesFields, null);
    }
  }
}
