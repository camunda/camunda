/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.es;

import static io.camunda.tasklist.schema.indices.ProcessInstanceDependant.PROCESS_INSTANCE_ID;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.ProcessInstanceDependant;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceWriter {

  @Autowired ProcessInstanceIndex processInstanceIndex;

  @Autowired TaskReaderWriter taskReaderWriter;

  @Autowired List<ProcessInstanceDependant> processInstanceDependants;

  @Autowired TaskVariableTemplate taskVariableTemplate;

  @Autowired RetryElasticsearchClient retryElasticsearchClient;

  public boolean deleteProcessInstance(final String processInstanceId) {
    // Don't need to validate for canceled/completed process instances
    // because only completed will be imported by ProcessInstanceZeebeRecordProcessor
    if (retryElasticsearchClient.deleteDocument(
        processInstanceIndex.getFullQualifiedName(), processInstanceId)) {
      return deleteProcessInstanceDependantsFor(processInstanceId);
    }
    return false;
  }

  private boolean deleteProcessInstanceDependantsFor(String processInstanceId) {
    final List<String> dependantTaskIds = getDependantTasksIdsFor(processInstanceId);
    boolean allDeleted = true;
    for (ProcessInstanceDependant dependant : processInstanceDependants) {
      if (!retryElasticsearchClient.deleteDocumentsByQuery(
          dependant.getFullQualifiedName(), termQuery(PROCESS_INSTANCE_ID, processInstanceId))) {
        allDeleted = false;
      }
    }
    if (allDeleted) {
      return deleteVariablesFor(dependantTaskIds);
    }
    return false;
  }

  private List<String> getDependantTasksIdsFor(final String processInstanceId) {
    final List<String> dependantTaskIds;
    try {
      dependantTaskIds = taskReaderWriter.getTaskIdsByProcessInstanceId(processInstanceId);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
    return dependantTaskIds;
  }

  private boolean deleteVariablesFor(final List<String> taskIds) {
    return retryElasticsearchClient.deleteDocumentsByQuery(
        taskVariableTemplate.getFullQualifiedName(),
        termsQuery(TaskVariableTemplate.TASK_ID, taskIds));
  }
}
