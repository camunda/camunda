/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import io.camunda.operate.exceptions.OperateRuntimeException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.tasks.GetTaskResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.tasks.RawTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.elasticsearch.index.query.QueryBuilders.*;

@Component
public class ElasticsearchTask {

  public static final String ID = "id";
  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchTask.class);

  public static final String ERROR = "error";
  public static final String REASON = "reason";
  public static final String RESPONSE = "response";
  public static final String FAILURES = "failures";
  public static final String CAUSE = "cause";
  public static final String SYSTEM_TASKS_INDEX = ".tasks";
  public static final String TOTAL = "total";
  public static final String CREATED = "created";
  public static final String UPDATED = "updated";
  public static final String DELETED = "deleted";
  public static final String TASK_ACTION = "task.action";
  public static final String TASK_ACTION_INDICES_REINDEX = "indices:data/write/reindex";
  public static final String TASK = "task";
  public static final String DESCRIPTION = "description";
  public static final String DESCRIPTION_PREFIX_FROM_INDEX = "reindex from [";
  public static final String DESCRIPTION_PREFIX_TO_INDEX = "to [";
  public static final String NODE = "node";
  public static final int MAX_TASKS_ENTRIES = 2_000;
  @Autowired
  private RestHighLevelClient esClient;

  public void checkForErrorsOrFailures(final String node,final Integer id) throws IOException {
    final Map<String,Object> taskStatus =
        esClient.get(new GetRequest(SYSTEM_TASKS_INDEX,node+":"+id), RequestOptions.DEFAULT)
        .getSourceAsMap();
    checkForErrors(taskStatus);
    checkForFailures(taskStatus);
  }

  public List<String> getRunningReindexTasksIdsFor(final String fromIndex,final String toIndex) throws IOException {
    if (!systemTaskIndexExists() || fromIndex == null || toIndex == null) {
      return List.of();
    }

    return getReindexTasks().stream()
           .filter( taskState -> descriptionContainsReindexFromTo(taskState, fromIndex, toIndex))
           .map(this::toTaskId)
           .toList();
  }

  private String toTaskId(Map<String,Object> taskState) {
      return String.format("%s:%s", taskState.get(NODE), taskState.get(ID));
  }

  private boolean descriptionContainsReindexFromTo(final Map<String,Object> taskState,final String fromIndex, final String toIndex) {
    final String desc = (String) taskState.get(DESCRIPTION);
    return desc != null &&
        desc.contains(DESCRIPTION_PREFIX_FROM_INDEX + fromIndex) &&
        desc.contains(DESCRIPTION_PREFIX_TO_INDEX + toIndex);
  }

  private List<Map<String,Object>> getReindexTasks() throws IOException {
    final SearchResponse searchResponse = esClient.search(new SearchRequest()
        .indices(SYSTEM_TASKS_INDEX)
        .source(SearchSourceBuilder.searchSource()
            .query(
              termQuery(TASK_ACTION, TASK_ACTION_INDICES_REINDEX))
            .size(MAX_TASKS_ENTRIES)),
        RequestOptions.DEFAULT);

    return Arrays.stream(searchResponse.getHits().getHits())
        .map(h -> (Map<String,Object>) h.getSourceAsMap().get(TASK))
        .toList();
  }

  private boolean systemTaskIndexExists() throws IOException {
    return esClient.indices().exists(new GetIndexRequest(SYSTEM_TASKS_INDEX), RequestOptions.DEFAULT);
  }

  private void checkForErrors(final Map<String, Object> taskStatus) {
    if (taskStatus!= null && !taskStatus.isEmpty() && taskStatus.containsKey(ERROR)) {
      final Map<String, String> taskError = (Map<String, String>) taskStatus.get(ERROR);
      logger.error("Task status contains error: " + taskError);
      throw new OperateRuntimeException(taskError.get(REASON));
    }
  }

  private void checkForFailures(final Map<String, Object> taskStatus) {
    if (taskStatus!= null && !taskStatus.isEmpty() && taskStatus.containsKey(RESPONSE)) {
      final Map<String, Object> taskResponse = (Map<String, Object>) taskStatus.get(RESPONSE);
      if(taskResponse.containsKey(FAILURES)){
          final List<Map<String,Object>> failures = (List<Map<String,Object>>) taskResponse.get(FAILURES);
          if(!failures.isEmpty()) {
            final Map<String, Object> failure = failures.get(0);
            final Map<String,String> cause = (Map<String, String>) failure.get(CAUSE);
            throw new OperateRuntimeException(cause.get(REASON));
          }
      }
    }
  }

  public boolean needsToPollAgain(final Optional<GetTaskResponse> taskResponse) {
    if (taskResponse.isEmpty()) {
      return false;
    }
    final Map<String, Object> statusMap = getTaskStatusMap(taskResponse.get());
    final long total = (Integer) statusMap.get(TOTAL);
    final long created = (Integer) statusMap.get(CREATED);
    final long updated = (Integer) statusMap.get(UPDATED);
    final long deleted = (Integer) statusMap.get(DELETED);
    return !taskResponse.get().isCompleted() || (created + updated + deleted != total);
  }

  private Map<String, Object> getTaskStatusMap(final GetTaskResponse taskResponse){
    return ((RawTaskStatus) taskResponse.getTaskInfo().getStatus()).toMap();
  }

  public int getTotal(final GetTaskResponse taskResponse){
    return (Integer) getTaskStatusMap(taskResponse).get(TOTAL);
  }

}
