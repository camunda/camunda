/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public interface AssigneeAndCandidateGroupsReader {

  String NESTED_USER_TASKS_AGG = "userTasks";
  String COMPOSITE_AGG = "composite";
  String TERMS_AGG = "userTaskFieldTerms";

  default void consumeAssigneesInBatches(
      final String engineAlias,
      final Consumer<List<String>> assigneeBatchConsumer,
      final int batchSize) {
    if (engineAlias == null) {
      throw new IllegalArgumentException("engineAlias cannot be null");
    }
    if (assigneeBatchConsumer == null) {
      throw new IllegalArgumentException("assigneeBatchConsumer cannot be null");
    }

    consumeUserTaskFieldTermsInBatches(
        ProcessInstanceDto.Fields.dataSource + "." + DataSourceDto.Fields.name,
        engineAlias,
        USER_TASK_ASSIGNEE,
        assigneeBatchConsumer,
        batchSize);
  }

  default void consumeCandidateGroupsInBatches(
      final String engineAlias,
      final Consumer<List<String>> candidateGroupBatchConsumer,
      final int batchSize) {
    if (engineAlias == null) {
      throw new IllegalArgumentException("engineAlias cannot be null");
    }
    if (candidateGroupBatchConsumer == null) {
      throw new IllegalArgumentException("candidateGroupBatchConsumer cannot be null");
    }

    consumeUserTaskFieldTermsInBatches(
        ProcessInstanceDto.Fields.dataSource + "." + DataSourceDto.Fields.name,
        engineAlias,
        USER_TASK_CANDIDATE_GROUPS,
        candidateGroupBatchConsumer,
        batchSize);
  }

  default Set<String> getAssigneeIdsForProcess(
      final Map<String, Set<String>> definitionKeyToTenantsMap) {
    return getUserTaskFieldTerms(
        ProcessInstanceIndex.USER_TASK_ASSIGNEE, definitionKeyToTenantsMap);
  }

  default Set<String> getCandidateGroupIdsForProcess(
      final Map<String, Set<String>> definitionKeyToTenantsMap) {
    return getUserTaskFieldTerms(
        ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS, definitionKeyToTenantsMap);
  }

  default void consumeUserTaskFieldTermsInBatches(
      final String termField,
      final String termValue,
      final String userTaskFieldName,
      final Consumer<List<String>> termBatchConsumer,
      final int batchSize) {
    consumeUserTaskFieldTermsInBatches(
        PROCESS_INSTANCE_MULTI_ALIAS,
        termField,
        termValue,
        userTaskFieldName,
        termBatchConsumer,
        batchSize);
  }

  default String getUserTaskFieldPath(final String fieldName) {
    return FLOW_NODE_INSTANCES + "." + fieldName;
  }

  void consumeUserTaskFieldTermsInBatches(
      final String indexName,
      final String termField,
      final String termValue,
      final String userTaskFieldName,
      final Consumer<List<String>> termBatchConsumer,
      final int batchSize);

  Set<String> getUserTaskFieldTerms(
      final String userTaskFieldName, final Map<String, Set<String>> definitionKeyToTenantsMap);
}
