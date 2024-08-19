/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.DECISION_INSTANCE_INDEX;
import static java.util.stream.Collectors.toSet;

import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.service.db.repository.DecisionInstanceRepository;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.db.repository.Repository;
import io.camunda.optimize.service.db.repository.TaskRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class DecisionInstanceWriter {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(DecisionInstanceWriter.class);
  private final IndexRepository indexRepository;
  private final TaskRepository taskRepository;
  private final Repository repository;
  private final DecisionInstanceRepository decisionInstanceRepository;

  public DecisionInstanceWriter(
      final IndexRepository indexRepository,
      final TaskRepository taskRepository,
      final Repository repository,
      final DecisionInstanceRepository decisionInstanceRepository) {
    this.indexRepository = indexRepository;
    this.taskRepository = taskRepository;
    this.repository = repository;
    this.decisionInstanceRepository = decisionInstanceRepository;
  }

  public void importDecisionInstances(final List<DecisionInstanceDto> decisionInstanceDtos) {
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
    taskRepository.executeWithTaskMonitoring(
        repository.getDeleteByQueryActionName(),
        () ->
            decisionInstanceRepository
                .deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(
                    decisionDefinitionKey, evaluationDate),
        log);
  }
}
