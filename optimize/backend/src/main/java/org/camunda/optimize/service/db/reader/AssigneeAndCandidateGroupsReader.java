/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;

public interface AssigneeAndCandidateGroupsReader {

  String NESTED_USER_TASKS_AGG = "userTasks";
  String COMPOSITE_AGG = "composite";
  String TERMS_AGG = "userTaskFieldTerms";

  default void consumeAssigneesInBatches(
      @NonNull final String engineAlias,
      @NonNull final Consumer<List<String>> assigneeBatchConsumer,
      final int batchSize) {
    consumeUserTaskFieldTermsInBatches(
        ProcessInstanceDto.Fields.dataSource + "." + DataSourceDto.Fields.name,
        engineAlias,
        USER_TASK_ASSIGNEE,
        assigneeBatchConsumer,
        batchSize);
  }

  default void consumeCandidateGroupsInBatches(
      @NonNull final String engineAlias,
      @NonNull final Consumer<List<String>> candidateGroupBatchConsumer,
      final int batchSize) {

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
