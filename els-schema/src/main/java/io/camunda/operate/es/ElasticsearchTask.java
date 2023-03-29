/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import io.camunda.operate.exceptions.OperateRuntimeException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.tasks.GetTaskResponse;
import org.elasticsearch.tasks.RawTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ElasticsearchTask {

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
  @Autowired
  private RestHighLevelClient esClient;

  public void checkForErrorsOrFailures(final String node,final Integer id) throws IOException {
    final Map<String,Object> taskStatus =
        esClient.get(new GetRequest(SYSTEM_TASKS_INDEX,node+":"+id), RequestOptions.DEFAULT)
        .getSourceAsMap();
    checkForErrors(taskStatus);
    checkForFailures(taskStatus);
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
