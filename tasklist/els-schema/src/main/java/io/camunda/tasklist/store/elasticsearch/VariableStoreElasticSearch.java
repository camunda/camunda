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
import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.tasklist.util.ElasticsearchUtil.scroll;
import static io.camunda.tasklist.util.ElasticsearchUtil.scrollInChunks;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.NAME;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.SCOPE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.VALUE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.util.ElasticsearchTenantHelper;
import io.camunda.tasklist.util.ElasticsearchUtil;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
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

  @Autowired
  @Qualifier("tasklistEs8Client")
  private ElasticsearchClient es8Client;

  @Autowired private ElasticsearchTenantHelper tenantHelper;

  @Autowired private VariableTemplate variableIndex;

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  @Autowired private SnapshotTaskVariableTemplate taskVariableTemplate;

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
  public Map<String, List<SnapshotTaskVariableEntity>> getTaskVariablesPerTaskId(
      final List<GetVariablesRequest> requests) {

    if (requests == null || requests.isEmpty()) {
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
    try {
      final var bulkOperations = finalVariables.stream().map(this::createUpsertRequest).toList();

      final var bulkRequest =
          BulkRequest.of(b -> b.operations(bulkOperations).refresh(Refresh.WaitFor));

      final var bulkResponse = es8Client.bulk(bulkRequest);

      if (bulkResponse.errors()) {
        final var errorMessages =
            bulkResponse.items().stream()
                .filter(item -> item.error() != null)
                .map(item -> item.error().reason())
                .collect(java.util.stream.Collectors.joining(", "));
        throw new TasklistRuntimeException(
            "Failed to persist task variables. Errors: " + errorMessages);
      }
    } catch (final IOException e) {
      throw new TasklistRuntimeException("Error persisting task variables", e);
    }
  }

  @Override
  public List<FlowNodeInstanceEntity> getFlowNodeInstances(final List<Long> processInstanceKeys) {
    try {
      return scrollInChunks(
          processInstanceKeys,
          tasklistProperties.getElasticsearch().getMaxTermsCount(),
          this::buildSearchFNIByProcessInstanceKeysRequest,
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
    final var query = ElasticsearchUtil.idsQuery(variableId);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var requestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(variableIndex.getFullQualifiedName())
            .query(tenantAwareQuery);

    applyFetchSourceForVariableIndexEs8(requestBuilder, fieldNames);

    try {
      final var response = es8Client.search(requestBuilder.build(), VariableEntity.class);
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
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(taskVariableTemplate.getAlias())
            .query(tenantAwareQuery);

    applyFetchSourceForTaskVariableTemplateEs8(requestBuilder, fieldNames);

    try {
      final var response =
          es8Client.search(requestBuilder.build(), SnapshotTaskVariableEntity.class);
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
      final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
      boolQuery.must(QueryBuilders.termQuery(NAME, varNames.get(i)));
      boolQuery.must(QueryBuilders.termQuery(VALUE, varValues.get(i)));

      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(boolQuery)
              .fetchSource(PROCESS_INSTANCE_KEY, null)
              .size(tasklistProperties.getElasticsearch().getBatchSize());

      final SearchRequest searchRequest =
          new SearchRequest(variableIndex.getFullQualifiedName()).source(searchSourceBuilder);

      final Set<Long> currentKeys;
      try {
        currentKeys =
            new HashSet<>(
                ElasticsearchUtil.scrollFieldToList(searchRequest, PROCESS_INSTANCE_KEY, esClient));
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

  private SearchRequest buildSearchFNIByProcessInstanceKeysRequest(
      final List<Long> processInstanceKeys) {
    final BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

    final TermsQueryBuilder processInstanceKeyQuery =
        termsQuery(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys);
    final var flowNodeInstanceStateQuery =
        termsQuery(FlowNodeInstanceTemplate.STATE, FlowNodeState.ACTIVE.toString());

    queryBuilder.must(flowNodeInstanceStateQuery);
    queryBuilder.must(processInstanceKeyQuery);

    final TermsQueryBuilder typeQuery =
        QueryBuilders.termsQuery(
            FlowNodeInstanceTemplate.TYPE,
            FlowNodeType.AD_HOC_SUB_PROCESS.toString(),
            FlowNodeType.AD_HOC_SUB_PROCESS_INNER_INSTANCE.toString(),
            FlowNodeType.USER_TASK.toString(),
            FlowNodeType.SUB_PROCESS.toString(),
            FlowNodeType.EVENT_SUB_PROCESS.toString(),
            FlowNodeType.MULTI_INSTANCE_BODY.toString(),
            FlowNodeType.PROCESS.toString());
    queryBuilder.must(typeQuery);

    final var query =
        ElasticsearchUtil.joinWithAnd(
            typeQuery, processInstanceKeyQuery, flowNodeInstanceStateQuery);
    return new SearchRequest(flowNodeInstanceIndex.getFullQualifiedName())
        .source(
            new SearchSourceBuilder()
                .query(constantScoreQuery(query))
                .sort(FlowNodeInstanceTemplate.POSITION, SortOrder.ASC)
                .size(tasklistProperties.getElasticsearch().getBatchSize()));
  }

  private SearchRequest buildSearchVariablesByScopeFNIsAndVarNamesRequest(
      final List<String> scopeFlowNodeIds,
      final List<String> varNames,
      final Set<String> fieldNames) {
    final TermsQueryBuilder flowNodeInstanceKeyQ = termsQuery(SCOPE_KEY, scopeFlowNodeIds);
    TermsQueryBuilder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = termsQuery(VariableTemplate.NAME, varNames);
    }
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(constantScoreQuery(joinWithAnd(flowNodeInstanceKeyQ, varNamesQ)))
            .size(tasklistProperties.getElasticsearch().getBatchSize());
    applyFetchSourceForVariableIndex(searchSourceBuilder, fieldNames);

    return new SearchRequest(variableIndex.getFullQualifiedName()).source(searchSourceBuilder);
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

  private void applyFetchSourceForVariableIndexEs8(
      final co.elastic.clients.elasticsearch.core.SearchRequest.Builder requestBuilder,
      final Set<String> fieldNames) {
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

  private void applyFetchSourceForTaskVariableTemplateEs8(
      final co.elastic.clients.elasticsearch.core.SearchRequest.Builder requestBuilder,
      final Set<String> fieldNames) {
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
    final var updateFields = new HashMap<String, Object>();
    updateFields.put(SnapshotTaskVariableTemplate.TASK_ID, variableEntity.getTaskId());
    updateFields.put(SnapshotTaskVariableTemplate.NAME, variableEntity.getName());
    updateFields.put(SnapshotTaskVariableTemplate.VALUE, variableEntity.getValue());

    return BulkOperation.of(
        op ->
            op.update(
                u ->
                    u.index(taskVariableTemplate.getFullQualifiedName())
                        .id(variableEntity.getId())
                        .retryOnConflict(UPDATE_RETRY_COUNT)
                        .action(a -> a.doc(updateFields).upsert(variableEntity))));
  }
}
