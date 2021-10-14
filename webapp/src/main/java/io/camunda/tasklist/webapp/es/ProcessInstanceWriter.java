/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.es;

import static io.camunda.tasklist.schema.indices.ProcessInstanceDependant.PROCESS_INSTANCE_ID;
import static io.camunda.tasklist.util.ElasticsearchUtil.QueryType.ONLY_ARCHIVE;
import static io.camunda.tasklist.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.ProcessInstanceDependant;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.ElasticsearchUtil;
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
  @Autowired TaskTemplate taskTemplate;

  @Autowired RetryElasticsearchClient retryElasticsearchClient;

  public boolean deleteProcessInstance(final String processInstanceId) {
    // Don't need to validate for canceled/completed process instances
    // because only completed will be imported by ProcessInstanceZeebeRecordProcessor
    final boolean deleted =
        retryElasticsearchClient.deleteDocument(
                processInstanceIndex.getFullQualifiedName(), processInstanceId)
            && deleteProcessInstanceDependantsFor(processInstanceId);

    if (!deleted) {
      // if not deleted, process instance might be archived. Retrying from archive
      return deleteProcessInstanceArchivedDataFor(processInstanceId);
    }

    return true;
  }

  private boolean deleteProcessInstanceDependantsFor(String processInstanceId) {
    final List<String> dependantTaskIds = getDependantTasksIdsFor(processInstanceId);
    boolean deleted = false;
    for (ProcessInstanceDependant dependant : processInstanceDependants) {
      deleted =
          retryElasticsearchClient.deleteDocumentsByQuery(
                  dependant.getFullQualifiedName(),
                  termQuery(PROCESS_INSTANCE_ID, processInstanceId))
              || deleted;
    }
    if (deleted) {
      deleteVariablesFor(dependantTaskIds, ONLY_RUNTIME);
      return true;
    }
    return false;
  }

  private boolean deleteProcessInstanceArchivedDataFor(String processInstanceId) {
    final List<String> dependantTaskIds = getDependantTasksIdsFor(processInstanceId);
    if (deleteArchivedTasks(processInstanceId)) {
      deleteVariablesFor(dependantTaskIds, ONLY_ARCHIVE);
      // no need to check results for variables, as we might have no variables added
      return true;
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

  private boolean deleteVariablesFor(final List<String> taskIds, ElasticsearchUtil.QueryType type) {
    return retryElasticsearchClient.deleteDocumentsByQuery(
        ElasticsearchUtil.whereToSearch(taskVariableTemplate, type),
        termsQuery(TaskVariableTemplate.TASK_ID, taskIds));
  }

  private boolean deleteArchivedTasks(String instanceId) {
    return retryElasticsearchClient.deleteDocumentsByQuery(
        ElasticsearchUtil.whereToSearch(taskTemplate, ONLY_ARCHIVE),
        termsQuery(PROCESS_INSTANCE_ID, instanceId));
  }
}
