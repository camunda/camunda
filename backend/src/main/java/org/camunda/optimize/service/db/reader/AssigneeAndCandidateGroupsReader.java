/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public interface AssigneeAndCandidateGroupsReader {

  String NESTED_USER_TASKS_AGG = "userTasks";
  String COMPOSITE_AGG = "composite";
  String TERMS_AGG = "userTaskFieldTerms";

  void consumeAssigneesInBatches(@NonNull final String engineAlias,
                                 @NonNull final Consumer<List<String>> assigneeBatchConsumer,
                                 final int batchSize);

  void consumeCandidateGroupsInBatches(@NonNull final String engineAlias,
                                       @NonNull final Consumer<List<String>> candidateGroupBatchConsumer,
                                       final int batchSize);

  Set<String> getAssigneeIdsForProcess(final Map<String, Set<String>> definitionKeyToTenantsMap);

  Set<String> getCandidateGroupIdsForProcess(final Map<String, Set<String>> definitionKeyToTenantsMap);

}
