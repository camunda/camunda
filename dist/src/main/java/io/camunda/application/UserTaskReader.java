/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.UserTaskState;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.util.Arrays;
import java.util.List;

/**
 * Reader that reads user tasks from Elasticsearch and converts them to RDBMS models.
 *
 * <p>This is part of the ES to RDBMS migration tooling (Tier 4).
 */
public final class UserTaskReader {

  private static final String INDEX_NAME = "tasklist-task-8.8.0_alias";

  private UserTaskReader() {}

  /**
   * Reads all user tasks from Elasticsearch.
   *
   * @param esClient the Elasticsearch client
   * @return list of TaskEntity objects from ES
   */
  public static List<TaskEntity> readAllUserTasksFromEs(final ElasticsearchClient esClient) {
    final var query =
        Query.of(q -> q.term(t -> t.field("join.name").value(FieldValue.of("task"))));

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(INDEX_NAME)
            .query(query)
            .size(ElasticsearchUtil.QUERY_MAX_SIZE);

    return ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, TaskEntity.class)
        .flatMap(response -> response.hits().hits().stream())
        .map(Hit::source)
        .toList();
  }

  /**
   * Converts a TaskEntity (ES model) to a UserTaskDbModel (RDBMS model).
   *
   * @param entity the TaskEntity from Elasticsearch
   * @return the corresponding UserTaskDbModel for RDBMS
   */
  public static UserTaskDbModel toRdbmsModel(final TaskEntity entity) {
    final var builder =
        new UserTaskDbModel.Builder()
            .userTaskKey(entity.getKey())
            .elementId(entity.getFlowNodeBpmnId())
            .name(entity.getName())
            .processDefinitionId(entity.getBpmnProcessId())
            .creationDate(entity.getCreationTime())
            .completionDate(entity.getCompletionTime())
            .assignee(entity.getAssignee())
            .state(mapState(entity.getState()))
            .formKey(parseFormKey(entity.getFormKey()))
            .processDefinitionKey(parseProcessDefinitionKey(entity.getProcessDefinitionId()))
            .processInstanceKey(parseLong(entity.getProcessInstanceId()))
            .rootProcessInstanceKey(
                entity.getRootProcessInstanceKey() != null
                    ? entity.getRootProcessInstanceKey()
                    : parseLong(entity.getProcessInstanceId()))
            .elementInstanceKey(parseLong(entity.getFlowNodeInstanceId()))
            .tenantId(entity.getTenantId())
            .dueDate(entity.getDueDate())
            .followUpDate(entity.getFollowUpDate())
            .externalFormReference(entity.getExternalFormReference())
            .customHeaders(entity.getCustomHeaders())
            .priority(entity.getPriority() != null ? entity.getPriority() : 50)
            .tags(entity.getTags())
            .partitionId(entity.getPartitionId());

    if (entity.getProcessDefinitionVersion() != null) {
      builder.processDefinitionVersion(entity.getProcessDefinitionVersion());
    }

    final var model = builder.build();

    // Set candidate users and groups
    if (entity.getCandidateUsers() != null) {
      model.candidateUsers(Arrays.asList(entity.getCandidateUsers()));
    }
    if (entity.getCandidateGroups() != null) {
      model.candidateGroups(Arrays.asList(entity.getCandidateGroups()));
    }

    return model;
  }

  private static UserTaskState mapState(final TaskState esState) {
    if (esState == null) {
      return null;
    }
    return switch (esState) {
      case CREATING -> UserTaskState.CREATING;
      case CREATED -> UserTaskState.CREATED;
      case ASSIGNING -> UserTaskState.ASSIGNING;
      case UPDATING -> UserTaskState.UPDATING;
      case COMPLETING -> UserTaskState.COMPLETING;
      case COMPLETED -> UserTaskState.COMPLETED;
      case CANCELING -> UserTaskState.CANCELING;
      case CANCELED -> UserTaskState.CANCELED;
      case FAILED -> UserTaskState.FAILED;
    };
  }

  private static Long parseFormKey(final String formKey) {
    if (formKey == null || formKey.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(formKey);
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  private static Long parseProcessDefinitionKey(final String processDefinitionId) {
    if (processDefinitionId == null || processDefinitionId.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(processDefinitionId);
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  private static Long parseLong(final String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  /**
   * Reads all user tasks from Elasticsearch and converts them to RDBMS models.
   *
   * @param esClient the Elasticsearch client
   * @return list of UserTaskDbModel objects ready for RDBMS insertion
   */
  public static List<UserTaskDbModel> readUserTasks(final ElasticsearchClient esClient) {
    return readAllUserTasksFromEs(esClient).stream().map(UserTaskReader::toRdbmsModel).toList();
  }
}
