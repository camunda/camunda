/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es;

import static io.camunda.tasklist.schema.indices.ProcessInstanceDependant.PROCESS_INSTANCE_ID;
import static io.camunda.tasklist.util.ElasticsearchUtil.QueryType.ALL;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.ProcessInstanceDependant;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.webapp.es.enums.DeletionStatus;
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

  public DeletionStatus deleteProcessInstance(final String processInstanceId) {
    // Don't need to validate for canceled/completed process instances
    // because only completed will be imported by ProcessInstanceZeebeRecordProcessor
    final boolean processInstanceWasDeleted =
        retryElasticsearchClient.deleteDocument(
            processInstanceIndex.getFullQualifiedName(), processInstanceId);
    // if deleted -> process instance was really finished and we can delete dependent data
    if (processInstanceWasDeleted) {
      return deleteProcessInstanceDependantsFor(processInstanceId);
    } else {
      return DeletionStatus.NOT_FOUND;
    }
  }

  private DeletionStatus deleteProcessInstanceDependantsFor(String processInstanceId) {
    final List<String> dependantTaskIds = getDependantTasksIdsFor(processInstanceId);
    boolean deleted = false;
    for (ProcessInstanceDependant dependant : processInstanceDependants) {
      deleted =
          retryElasticsearchClient.deleteDocumentsByQuery(
                  dependant.getAllIndicesPattern(),
                  termQuery(PROCESS_INSTANCE_ID, processInstanceId))
              || deleted;
    }
    if (deleted) {
      deleteVariablesFor(dependantTaskIds);
      return DeletionStatus.DELETED;
    }
    return DeletionStatus.FAILED;
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
        ElasticsearchUtil.whereToSearch(taskVariableTemplate, ALL),
        termsQuery(TaskVariableTemplate.TASK_ID, taskIds));
  }
}
