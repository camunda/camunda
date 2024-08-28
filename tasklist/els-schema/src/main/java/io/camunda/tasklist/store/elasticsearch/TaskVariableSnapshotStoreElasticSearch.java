/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.templates.TasklistTaskVariableSnapshotTemplate;
import io.camunda.tasklist.store.TaskVariableSnapshotStore;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class TaskVariableSnapshotStoreElasticSearch implements TaskVariableSnapshotStore {

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private TasklistTaskVariableSnapshotTemplate tasklistTaskVariableSnapshotTemplate;

  @Override
  public List<String> retrieveVariableByScopeKey(final String scopeKey) {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(tasklistTaskVariableSnapshotTemplate)
            .source(
                SearchSourceBuilder.searchSource()
                    .query(
                        termQuery(
                            TasklistTaskVariableSnapshotTemplate.VARIABLE_SCOPE_KEY, scopeKey))
                    .fetchField(TasklistTaskVariableSnapshotTemplate.ID));
    try {
      final var result = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final var result1 = ElasticsearchUtil.scrollIdsToList(searchRequest, esClient);
      return result1;
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  /**
   * Retrieves process variables for the given task's process instance, copies them, and creates new
   * task variables with the specified task's flow node instance ID as the parent.
   *
   * @param processInstanceId the ID of the process instance (parent)
   * @param taskFlowNodeInstanceId the flow node instance ID of the task (child)
   */
  @Override
  public void copyProcessVariablesToTaskVariables(
      final String processInstanceId, final String taskFlowNodeInstanceId) {
    try {
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(tasklistTaskVariableSnapshotTemplate)
              .source(
                  SearchSourceBuilder.searchSource()
                      .query(termQuery("variableScopeKey.keyword", processInstanceId)));

      final var searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final SearchHit[] hits = searchResponse.getHits().getHits();

      // Step 2: Iterate through the process variables and create task variables
      for (final SearchHit hit : hits) {
        final Map<String, Object> sourceMap = hit.getSourceAsMap();

        // Step 3: Prepare the data for the new task variable
        final Map<String, Object> updateFields = new HashMap<>(sourceMap);
        updateFields.put(
            TasklistTaskVariableSnapshotTemplate.JOIN_FIELD_NAME,
            Map.of("name", "taskVariable", "parent", taskFlowNodeInstanceId));

        // Step 4: Convert the updated sourceMap to JSON format
        final Map<String, Object> jsonMap =
            objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

        // Step 5: Create an upsert request for each task variable
        final UpdateRequest updateRequest =
            new UpdateRequest()
                .index(tasklistTaskVariableSnapshotTemplate.getFullQualifiedName())
                .id(
                    "snapshot-"
                        + taskFlowNodeInstanceId
                        + "-"
                        + updateFields.get(TasklistTaskVariableSnapshotTemplate.VARIABLE_NAME))
                .routing(taskFlowNodeInstanceId)
                .upsert(jsonMap)
                .doc(updateFields)
                .retryOnConflict(UPDATE_RETRY_COUNT);

        // Step 6: Execute the upsert request
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
}
