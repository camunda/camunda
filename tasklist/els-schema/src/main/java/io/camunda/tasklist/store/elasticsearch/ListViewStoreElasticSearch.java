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
import io.camunda.tasklist.entities.TasklistListViewEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.templates.TasklistListViewTemplate;
import io.camunda.tasklist.store.ListViewStore;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
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

  /**
   * Retrieves process variables for the given task's process instance, copies them, and creates new
   * task variables with the specified task's flow node instance ID as the parent.
   *
   * @param processInstanceId the ID of the process instance (parent)
   * @param taskFlowNodeInstanceId the flow node instance ID of the task (child)
   */
  @Override
  public void persistProcessVariablesToTaskVariables(
      final String processInstanceId, final String taskFlowNodeInstanceId) {
    try {
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(tasklistListViewTemplate)
              .source(
                  SearchSourceBuilder.searchSource()
                      .query(
                          termQuery(
                              TasklistListViewTemplate.VARIABLE_SCOPE_KEY, processInstanceId)));

      final var searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final SearchHit[] hits = searchResponse.getHits().getHits();

      for (final SearchHit hit : hits) {

        final TasklistListViewEntity tasklistViewEntity =
            fromSearchHit(hit.toString(), objectMapper, TasklistListViewEntity.class);
        final Map<String, Object> sourceMap = hit.getSourceAsMap();

        final Map<String, Object> updateFields = new HashMap<>(sourceMap);
        updateFields.put(
            TasklistListViewTemplate.JOIN_FIELD_NAME,
            Map.of("name", "taskVariable", "parent", taskFlowNodeInstanceId));

        updateFields.put(
            TasklistListViewTemplate.ID,
            taskFlowNodeInstanceId + "-" + tasklistViewEntity.getVarName());

        updateFields.put(TasklistListViewTemplate.VARIABLE_SCOPE_KEY, taskFlowNodeInstanceId);

        final Map<String, Object> jsonMap =
            objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

        final UpdateRequest updateRequest =
            new UpdateRequest()
                .index(tasklistListViewTemplate.getFullQualifiedName())
                .id(
                    taskFlowNodeInstanceId
                        + "-"
                        + updateFields.get(TasklistListViewTemplate.VARIABLE_NAME))
                .routing(taskFlowNodeInstanceId)
                .upsert(jsonMap)
                .doc(updateFields)
                .retryOnConflict(UPDATE_RETRY_COUNT);

        esClient.update(updateRequest, RequestOptions.DEFAULT);
      }
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error copying process variables to task variables for task [%s]",
              taskFlowNodeInstanceId),
          e);
    }
  }

  /**
   * Remove the task variable data for the given task's flow node instance ID. This ill be used
   * meanwhile we still support Job Workers, and to clean up the task variable data once the task is
   * completed from a Job Worker side.
   *
   * @param flowNodeInstanceId the flow node ID of the task
   */
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

      final var searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final SearchHit[] hits = searchResponse.getHits().getHits();

      for (final SearchHit hit : hits) {
        final TasklistListViewEntity tasklistViewEntity =
            fromSearchHit(hit.toString(), objectMapper, TasklistListViewEntity.class);

        final var deleteRequest =
            new org.elasticsearch.action.delete.DeleteRequest()
                .index(tasklistListViewTemplate.getFullQualifiedName())
                .id(tasklistViewEntity.getId())
                .routing(flowNodeInstanceId);

        esClient.delete(deleteRequest, RequestOptions.DEFAULT);
      }
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error removing job worker variable data for flowNodeInstanceId [%s]",
              flowNodeInstanceId),
          e);
    }
  }
}
