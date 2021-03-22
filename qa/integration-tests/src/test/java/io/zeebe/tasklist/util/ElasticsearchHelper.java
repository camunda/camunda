/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import static io.zeebe.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.zeebe.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static io.zeebe.tasklist.util.ElasticsearchUtil.mapSearchHits;
import static io.zeebe.tasklist.util.ElasticsearchUtil.scroll;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.entities.WorkflowInstanceEntity;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.schema.indices.WorkflowInstanceIndex;
import io.zeebe.tasklist.schema.templates.TaskTemplate;
import io.zeebe.tasklist.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchHelper.class);

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private WorkflowInstanceIndex workflowInstanceIndex;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  public TaskEntity getTask(String taskId) {
    try {
      final GetRequest getRequest = new GetRequest(taskTemplate.getAlias()).id(taskId);
      final GetResponse response = esClient.get(getRequest, RequestOptions.DEFAULT);
      if (response.isExists()) {
        return fromSearchHit(response.getSourceAsString(), objectMapper, TaskEntity.class);
      } else {
        throw new NotFoundException(String.format("Could not find  task for taskId [%s].", taskId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the task: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public WorkflowInstanceEntity getWorkflowInstance(String workflowInstanceId) {
    try {
      final GetRequest getRequest =
          new GetRequest(workflowInstanceIndex.getAlias()).id(workflowInstanceId);
      final GetResponse response = esClient.get(getRequest, RequestOptions.DEFAULT);
      if (response.isExists()) {
        return fromSearchHit(
            response.getSourceAsString(), objectMapper, WorkflowInstanceEntity.class);
      } else {
        throw new NotFoundException(
            String.format("Could not find task for workflowInstanceId [%s].", workflowInstanceId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the workflow: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public List<WorkflowInstanceEntity> getWorkflowInstances(List<String> workflowInstanceIds) {
    try {
      final SearchRequest request =
          new SearchRequest(workflowInstanceIndex.getAlias())
              .source(
                  new SearchSourceBuilder()
                      .query(idsQuery().addIds(workflowInstanceIds.toArray(String[]::new))));
      final SearchResponse searchResponse = esClient.search(request, RequestOptions.DEFAULT);
      return scroll(request, WorkflowInstanceEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining list of workflows: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public List<TaskEntity> getTask(String workflowInstanceId, String flowNodeBpmnId) {
    final SearchRequest searchRequest =
        new SearchRequest(taskTemplate.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            termQuery(TaskTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId),
                            termQuery(TaskTemplate.FLOW_NODE_BPMN_ID, flowNodeBpmnId)))
                    .sort(TaskTemplate.CREATION_TIME, SortOrder.DESC));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().totalHits >= 1) {
        return mapSearchHits(response.getHits().getHits(), objectMapper, TaskEntity.class);
      } else {
        throw new NotFoundException(
            String.format(
                "Could not find task for workflowInstanceId [%s] with flowNodeBpmnId [%s].",
                workflowInstanceId, flowNodeBpmnId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the workflow: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }
}
