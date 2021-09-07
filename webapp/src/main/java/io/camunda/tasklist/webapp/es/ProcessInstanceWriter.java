/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.es;

import static io.camunda.tasklist.schema.indices.ProcessInstanceDependant.PROCESS_INSTANCE_ID;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.schema.indices.ProcessInstanceDependant;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceWriter {

  @Autowired ProcessInstanceIndex processInstanceIndex;

  @Autowired List<ProcessInstanceDependant> processInstanceDependants;

  @Autowired RetryElasticsearchClient retryElasticsearchClient;

  public Boolean deleteProcessInstance(final String processInstanceId) {
    // Don't need to validate for canceled/completed process instances
    // because only completed will be imported by ProcessInstanceZeebeRecordProcessor
    return retryElasticsearchClient.deleteDocument(
            processInstanceIndex.getFullQualifiedName(), processInstanceId)
        && deleteProcessInstanceDependantsFor(processInstanceId);
  }

  private Boolean deleteProcessInstanceDependantsFor(String processInstanceId) {
    Boolean allDeleted = true;
    for (ProcessInstanceDependant dependant : processInstanceDependants) {
      if (!retryElasticsearchClient.deleteDocumentsByQuery(
          dependant.getFullQualifiedName(), termQuery(PROCESS_INSTANCE_ID, processInstanceId))) {
        allDeleted = false;
      }
    }
    return allDeleted;
  }
}
