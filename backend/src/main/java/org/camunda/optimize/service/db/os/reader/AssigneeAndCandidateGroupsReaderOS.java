/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.reader.AssigneeAndCandidateGroupsReader;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class AssigneeAndCandidateGroupsReaderOS implements AssigneeAndCandidateGroupsReader {

  private final OptimizeOpenSearchClient osClient;

  @Override
  public void consumeAssigneesInBatches(@NonNull final String engineAlias,
                                        @NonNull final Consumer<List<String>> assigneeBatchConsumer,
                                        final int batchSize) {
    //todo will be handled in the OPT-7230
  }

  @Override
  public void consumeCandidateGroupsInBatches(@NonNull final String engineAlias,
                                              @NonNull final Consumer<List<String>> candidateGroupBatchConsumer,
                                              final int batchSize) {
    //todo will be handled in the OPT-7230
  }

  @Override
  public Set<String> getAssigneeIdsForProcess(final Map<String, Set<String>> definitionKeyToTenantsMap) {
    //todo will be handled in the OPT-7230
    return new HashSet<>();
  }

  @Override
  public Set<String> getCandidateGroupIdsForProcess(final Map<String, Set<String>> definitionKeyToTenantsMap) {
    //todo will be handled in the OPT-7230
    return new HashSet<>();
  }

}
