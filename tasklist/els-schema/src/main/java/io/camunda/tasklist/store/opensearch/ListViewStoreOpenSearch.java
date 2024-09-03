/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.TasklistListViewEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.templates.TasklistListViewTemplate;
import io.camunda.tasklist.store.ListViewStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ListViewStoreOpenSearch implements ListViewStore {

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient osClient;

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
      // Create a search request to find process variables associated with the processInstanceId
      final SearchRequest.Builder searchRequest =
          new SearchRequest.Builder()
              .index(tasklistListViewTemplate.getAlias())
              .query(
                  q ->
                      q.term(
                          t ->
                              t.field(TasklistListViewTemplate.VARIABLE_SCOPE_KEY)
                                  .value(FieldValue.of(processInstanceId))));

      final SearchResponse<TasklistListViewEntity> searchResponse =
          osClient.search(searchRequest.build(), TasklistListViewEntity.class);

      final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
      final List<BulkOperation> bulkOperations = new ArrayList<>();

      for (final Hit<TasklistListViewEntity> tasklistListViewEntityHit :
          searchResponse.hits().hits()) {
        final TasklistListViewEntity tasklistListViewEntity = tasklistListViewEntityHit.source();

        // Set Variable as a Scope Key for the task variable
        tasklistListViewEntity.setJoin(
            Map.of("name", "taskVariable", "parent", taskFlowNodeInstanceId));
        tasklistListViewEntity.setId(
            taskFlowNodeInstanceId + "-" + tasklistListViewEntity.getVarName());
        tasklistListViewEntity.setVarScopeKey(taskFlowNodeInstanceId);

        final BulkOperation bulkOperation =
            new BulkOperation.Builder()
                .update(
                    UpdateOperation.of(
                        u ->
                            u.index(tasklistListViewTemplate.getFullQualifiedName())
                                .id(
                                    taskFlowNodeInstanceId
                                        + "-"
                                        + tasklistListViewEntity.getVarName())
                                .docAsUpsert(true)
                                .document(
                                    CommonUtils.getJsonObjectFromEntity(tasklistListViewEntity))
                                .routing(taskFlowNodeInstanceId)))
                .build();

        bulkOperations.add(bulkOperation);
      }

      // Add the operations to the bulk request and execute it
      bulkRequest.operations(bulkOperations);
      osClient.bulk(bulkRequest.build());

    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error copying process variables to task variables for task [%s]",
              taskFlowNodeInstanceId),
          e);
    }
  }
}
