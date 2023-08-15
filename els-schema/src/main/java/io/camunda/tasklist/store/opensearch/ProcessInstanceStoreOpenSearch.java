/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.schema.indices.ProcessInstanceDependant.PROCESS_INSTANCE_ID;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.enums.DeletionStatus;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import io.camunda.tasklist.schema.indices.ProcessInstanceDependant;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.store.ProcessInstanceStore;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.util.OpenSearchUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessInstanceStoreOpenSearch implements ProcessInstanceStore {

  @Autowired ProcessInstanceIndex processInstanceIndex;

  @Autowired TaskStore taskStore;

  @Autowired List<ProcessInstanceDependant> processInstanceDependants;

  @Autowired TaskVariableTemplate taskVariableTemplate;

  @Autowired RetryOpenSearchClient retryOpenSearchClient;

  @Override
  public DeletionStatus deleteProcessInstance(final String processInstanceId) {
    // Don't need to validate for canceled/completed process instances
    // because only completed will be imported by ProcessInstanceZeebeRecordProcessor
    final boolean processInstanceWasDeleted =
        retryOpenSearchClient.deleteDocument(
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
          retryOpenSearchClient.deleteDocumentsByQuery(
                  dependant.getAllIndicesPattern(),
                  new Query.Builder()
                      .term(
                          term ->
                              term.field(PROCESS_INSTANCE_ID)
                                  .value(FieldValue.of(processInstanceId)))
                      .build())
              || deleted;
    }
    if (deleted) {
      deleteVariablesFor(dependantTaskIds);
      return DeletionStatus.DELETED;
    }
    return DeletionStatus.FAILED;
  }

  private List<String> getDependantTasksIdsFor(final String processInstanceId) {
    return taskStore.getTaskIdsByProcessInstanceId(processInstanceId);
  }

  private boolean deleteVariablesFor(final List<String> taskIds) {
    return retryOpenSearchClient.deleteDocumentsByQuery(
        OpenSearchUtil.whereToSearch(taskVariableTemplate, OpenSearchUtil.QueryType.ALL),
        new Query.Builder()
            .terms(
                terms ->
                    terms
                        .field(TaskVariableTemplate.TASK_ID)
                        .terms(
                            v ->
                                v.value(
                                    taskIds.stream()
                                        .map(m -> FieldValue.of(m))
                                        .collect(Collectors.toList()))))
            .build());
  }
}
