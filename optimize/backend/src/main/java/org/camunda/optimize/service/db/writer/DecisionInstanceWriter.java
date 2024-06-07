/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.DECISION_INSTANCE_INDEX;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.db.repository.DecisionInstanceRepository;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.camunda.optimize.service.db.repository.Repository;
import org.camunda.optimize.service.db.repository.TaskRepository;
import org.camunda.optimize.service.util.PeriodicAction;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionInstanceWriter {
  private final IndexRepository indexRepository;
  private final TaskRepository taskRepository;
  private final Repository repository;
  private final DecisionInstanceRepository decisionInstanceRepository;

  public void importDecisionInstances(List<DecisionInstanceDto> decisionInstanceDtos) {
    final String importItemName = "decision instances";
    log.debug("Writing [{}] {} to Database.", decisionInstanceDtos.size(), importItemName);
    final Set<String> decisionDefinitionKeys =
        decisionInstanceDtos.stream()
            .map(DecisionInstanceDto::getDecisionDefinitionKey)
            .collect(toSet());
    indexRepository.createMissingIndices(
        DECISION_INSTANCE_INDEX, Set.of(DECISION_INSTANCE_MULTI_ALIAS), decisionDefinitionKeys);
    decisionInstanceRepository.importDecisionInstances("decision instances", decisionInstanceDtos);
  }

  public void deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(
      final String decisionDefinitionKey, final OffsetDateTime evaluationDate) {
    if (!indexRepository.indexExists(DECISION_INSTANCE_INDEX, decisionDefinitionKey)) {
      log.info(
          "Aborting deletion of instances of definition with key {} because no instances exist for this definition.",
          decisionDefinitionKey);
      return;
    }
    executeWithTaskMonitoring(
        repository.getDeleteByQueryActionName(),
        () ->
            decisionInstanceRepository
                .deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(
                    decisionDefinitionKey, evaluationDate));
  }

  private void executeWithTaskMonitoring(String action, Runnable runnable) {
    final PeriodicAction progressReporter =
        new PeriodicAction(
            getClass().getName(),
            () ->
                taskRepository
                    .tasksProgress(action)
                    .forEach(
                        tasksProgressInfo ->
                            log.info(
                                "Current {} BulkByScrollTaskTask progress: {}%, total: {}, done: {}",
                                action,
                                tasksProgressInfo.progress(),
                                tasksProgressInfo.totalCount(),
                                tasksProgressInfo.processedCount())));

    try {
      progressReporter.start();
      runnable.run();
    } finally {
      progressReporter.stop();
    }
  }
}
