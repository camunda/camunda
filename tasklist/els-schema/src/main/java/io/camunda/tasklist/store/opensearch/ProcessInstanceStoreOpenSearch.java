/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.v86.schema.indices.TasklistProcessInstanceDependant.PROCESS_INSTANCE_ID;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.enums.DeletionStatus;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.ProcessInstanceStore;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.v86.entities.ProcessInstanceEntity;
import io.camunda.tasklist.v86.schema.indices.TasklistProcessInstanceDependant;
import io.camunda.tasklist.v86.schema.indices.TasklistProcessInstanceIndex;
import io.camunda.tasklist.v86.schema.templates.TasklistDraftTaskVariableTemplate;
import io.camunda.tasklist.v86.schema.templates.TasklistTaskVariableTemplate;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessInstanceStoreOpenSearch implements ProcessInstanceStore {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceStoreOpenSearch.class);

  @Autowired TasklistProcessInstanceIndex processInstanceIndex;

  @Autowired TaskStore taskStore;

  @Autowired List<TasklistProcessInstanceDependant> processInstanceDependants;

  @Autowired TasklistTaskVariableTemplate taskVariableTemplate;

  @Autowired RetryOpenSearchClient retryOpenSearchClient;

  @Autowired TenantAwareOpenSearchClient tenantAwareClient;

  @Autowired TasklistProperties tasklistProperties;

  @Override
  public DeletionStatus deleteProcessInstance(final String processInstanceId) {
    if (tasklistProperties.getMultiTenancy().isEnabled() && getById(processInstanceId).isEmpty()) {
      return DeletionStatus.NOT_FOUND;
    }

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

  private DeletionStatus deleteProcessInstanceDependantsFor(final String processInstanceId) {
    final List<String> dependantTaskIds = getDependantTasksIdsFor(processInstanceId);
    boolean deleted = false;
    for (final TasklistProcessInstanceDependant dependant : processInstanceDependants) {
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
                        .field(TasklistTaskVariableTemplate.TASK_ID)
                        .terms(
                            v ->
                                v.value(
                                    taskIds.stream()
                                        .map(m -> FieldValue.of(m))
                                        .collect(Collectors.toList()))))
            .build());
  }

  private Optional<ProcessInstanceEntity> getById(final String variableId) {
    try {
      final SearchRequest.Builder searchRequest = new SearchRequest.Builder();
      searchRequest.index(processInstanceIndex.getFullQualifiedName());
      searchRequest.query(
          q ->
              q.term(
                  term ->
                      term.field(TasklistDraftTaskVariableTemplate.ID)
                          .value(FieldValue.of(variableId))));

      final SearchResponse<ProcessInstanceEntity> searchResponse =
          tenantAwareClient.search(searchRequest, ProcessInstanceEntity.class);

      final List<Hit<ProcessInstanceEntity>> hits = searchResponse.hits().hits();
      if (hits.size() == 0) {
        return Optional.empty();
      }

      final Hit<ProcessInstanceEntity> hit = hits.get(0);
      return Optional.of(hit.source());
    } catch (final IOException e) {
      LOGGER.error(String.format("Error retrieving processInstance with ID [%s]", variableId), e);
      return Optional.empty();
    }
  }
}
