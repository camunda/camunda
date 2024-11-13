/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.listview.VariableListViewEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.v86.templates.TasklistListViewTemplate;
import io.camunda.tasklist.store.ListViewStore;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ListViewStoreElasticSearch implements ListViewStore {

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private TasklistListViewTemplate tasklistListViewTemplate;

  @Override
  public void removeVariableByFlowNodeInstanceId(final String flowNodeInstanceId) {
    try {
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(tasklistListViewTemplate)
              .source(
                  SearchSourceBuilder.searchSource()
                      .query(
                          termQuery(
                              TasklistListViewTemplate.VARIABLE_SCOPE_KEY, flowNodeInstanceId)));

      ElasticsearchUtil.scrollWith(
          searchRequest,
          esClient,
          hits -> {
            for (final SearchHit hit : hits.getHits()) {
              final VariableListViewEntity variableListViewEntity =
                  ElasticsearchUtil.fromSearchHit(
                      hit.getSourceAsString(), objectMapper, VariableListViewEntity.class);

              final var deleteRequest =
                  new DeleteRequest()
                      .index(tasklistListViewTemplate.getFullQualifiedName())
                      .id(variableListViewEntity.getId())
                      .routing(flowNodeInstanceId);
              try {
                esClient.delete(deleteRequest, RequestOptions.DEFAULT);
              } catch (final IOException e) {
                throw new TasklistRuntimeException(
                    String.format("Error removing variable [%s]", variableListViewEntity.getName()),
                    e);
              }
            }
          },
          null, // No need for an aggregation processor
          null // No need for processing first response metadata
          );
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error to retrieve variable for the flowNodeInstanceId [%s]", flowNodeInstanceId),
          e);
    }
  }

  @Override
  public List<VariableListViewEntity> getVariablesByVariableName(final String varName) {
    final List<VariableListViewEntity> variableList = new ArrayList<>();

    try {
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(tasklistListViewTemplate)
              .source(
                  SearchSourceBuilder.searchSource()
                      .query(termQuery(TasklistListViewTemplate.VARIABLE_NAME, varName)));

      // Initialize scroll request
      ElasticsearchUtil.scrollWith(
          searchRequest,
          esClient,
          hits -> {
            for (final SearchHit hit : hits.getHits()) {
              variableList.add(
                  fromSearchHit(
                      hit.getSourceAsString(), objectMapper, VariableListViewEntity.class));
            }
          },
          null, // No need for an aggregation processor
          null // No need for processing first response metadata
          );

    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format("Error retrieving variables for variable name [%s]", varName), e);
    }

    return variableList;
  }

  @Override
  public void persistTaskVariables(final Collection<VariableListViewEntity> finalVariables) {
    if (finalVariables.isEmpty()) {
      return;
    }

    final BulkRequest bulkRequest = new BulkRequest();

    for (final VariableListViewEntity variableEntity : finalVariables) {
      bulkRequest.add(createUpsertRequest(variableEntity));
    }
    try {
      ElasticsearchUtil.processBulkRequest(
          esClient, bulkRequest, WriteRequest.RefreshPolicy.WAIT_UNTIL);
    } catch (final PersistenceException ex) {
      throw new TasklistRuntimeException(ex);
    }
  }

  private UpdateRequest createUpsertRequest(final VariableListViewEntity variableEntity) {
    try {
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TasklistListViewTemplate.VARIABLE_NAME, variableEntity.getName());
      updateFields.put(TasklistListViewTemplate.VARIABLE_VALUE, variableEntity.getValue());
      updateFields.put(TasklistListViewTemplate.VARIABLE_FULL_VALUE, variableEntity.getFullValue());
      updateFields.put(TasklistListViewTemplate.JOIN_FIELD_NAME, variableEntity.getJoin());

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return new UpdateRequest()
          .index(tasklistListViewTemplate.getFullQualifiedName())
          .id(variableEntity.getId())
          .upsert(objectMapper.writeValueAsString(variableEntity), XContentType.JSON)
          .doc(jsonMap)
          .routing(variableEntity.getScopeKey())
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error preparing the query to upsert task variable instance [%s]",
              variableEntity.getId()),
          e);
    }
  }
}
