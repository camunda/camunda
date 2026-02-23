/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.read.service.HistoryDeletionDbReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryDeletionConfig;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.HistoryDeletionBatch;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel.HistoryDeletionTypeDbModel;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.zeebe.util.ExponentialBackoff;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This service is for deleting history on user request. For data retention see {@link
 * HistoryCleanupService}.
 */
public class HistoryDeletionService {
  private static final Logger LOG = LoggerFactory.getLogger(HistoryDeletionService.class);

  private final RdbmsWriters rdbmsWriters;
  private final HistoryDeletionDbReader historyDeletionDbReader;
  private final ProcessInstanceDbReader processInstanceDbReader;
  private final DecisionInstanceDbReader decisionInstanceDbReader;
  private final HistoryDeletionConfig config;
  private final ExponentialBackoff exponentialBackoff;
  private Duration currentDelayBetweenRuns;

  public HistoryDeletionService(
      final RdbmsWriters rdbmsWriters,
      final HistoryDeletionDbReader historyDeletionDbReader,
      final ProcessInstanceDbReader processInstanceDbReader,
      final DecisionInstanceDbReader decisionInstanceDbReader,
      final HistoryDeletionConfig config) {
    this.rdbmsWriters = rdbmsWriters;
    this.historyDeletionDbReader = historyDeletionDbReader;
    this.processInstanceDbReader = processInstanceDbReader;
    this.decisionInstanceDbReader = decisionInstanceDbReader;
    this.config = config;
    exponentialBackoff =
        new ExponentialBackoff(
            config.maxDelayBetweenRuns().toMillis(),
            config.delayBetweenRuns().toMillis(),
            2,
            0.0); // Use 0.0 jitter for deterministic backoff and clamp to min delay to avoid
    // sub-min values
    currentDelayBetweenRuns = config.delayBetweenRuns();
  }

  public Duration deleteHistory(final int partitionId) {
    final var batch = historyDeletionDbReader.getNextBatch(partitionId, config.queueBatchSize());
    LOG.trace("Deleting historic data for entities: {}", batch.historyDeletionModels());

    final List<Long> deletedProcessInstances = deleteProcessInstances(batch);
    final List<Long> deletedProcessDefinitions = deleteProcessDefinitions(batch);
    final List<Long> deletedDecisionInstances = deleteDecisionInstances(batch);
    final List<Long> deletedDecisionRequirements = deleteDecisionRequirements(batch);

    final List<Long> deletedResources =
        Stream.of(
                deletedProcessInstances,
                deletedProcessDefinitions,
                deletedDecisionInstances,
                deletedDecisionRequirements)
            .flatMap(List::stream)
            .toList();
    final var deletedResourceCount = deleteFromHistoryDeletionTable(deletedResources);

    return nextDelay(deletedResourceCount);
  }

  private List<Long> deleteProcessInstances(final HistoryDeletionBatch batch) {
    final var processInstanceKeys =
        batch.getResourceKeys(HistoryDeletionTypeDbModel.PROCESS_INSTANCE);

    if (processInstanceKeys.isEmpty()) {
      return List.of();
    }

    boolean allProcessInstanceDependantDataDeleted = true;
    final var limit = config.dependentRowLimit();
    for (final var dependant : rdbmsWriters.getProcessInstanceDependantWriters()) {
      if (dependant instanceof AuditLogWriter) {
        continue;
      }
      final var deletedRows =
          dependant.deleteProcessInstanceRelatedData(processInstanceKeys, limit);
      if (deletedRows >= limit) {
        allProcessInstanceDependantDataDeleted = false;
      }
    }

    if (allProcessInstanceDependantDataDeleted) {
      rdbmsWriters.getProcessInstanceWriter().deleteByKeys(processInstanceKeys);
      return processInstanceKeys;
    }

    return List.of();
  }

  private List<Long> deleteProcessDefinitions(final HistoryDeletionBatch batch) {
    final var processDefinitionKeys =
        batch.getResourceKeys(
            HistoryDeletionTypeDbModel.PROCESS_DEFINITION, this::hasDeletedAllProcessInstances);

    if (processDefinitionKeys.isEmpty()) {
      return List.of();
    }

    rdbmsWriters.getProcessDefinitionWriter().deleteByKeys(processDefinitionKeys);

    return processDefinitionKeys;
  }

  private List<Long> deleteDecisionInstances(final HistoryDeletionBatch batch) {
    final var decisionInstanceKeys =
        batch.getResourceKeys(HistoryDeletionTypeDbModel.DECISION_INSTANCE);

    if (decisionInstanceKeys.isEmpty()) {
      return List.of();
    }

    rdbmsWriters.getDecisionInstanceWriter().deleteByKeys(decisionInstanceKeys);
    return decisionInstanceKeys;
  }

  private List<Long> deleteDecisionRequirements(final HistoryDeletionBatch batch) {
    final var decisionRequirementsKeys =
        batch.getResourceKeys(
            HistoryDeletionTypeDbModel.DECISION_REQUIREMENTS, this::hasDeletedAllDecisionInstances);

    if (decisionRequirementsKeys.isEmpty()) {
      return List.of();
    }

    boolean allDecisionRequirementsDependantDataDeleted = true;
    final var limit = config.dependentRowLimit();
    final var deletedRows =
        rdbmsWriters
            .getDecisionDefinitionWriter()
            .deleteByDecisionRequirementsKeys(decisionRequirementsKeys, limit);
    if (deletedRows >= limit) {
      allDecisionRequirementsDependantDataDeleted = false;
    }

    if (allDecisionRequirementsDependantDataDeleted) {
      rdbmsWriters.getDecisionRequirementsWriter().deleteByKeys(decisionRequirementsKeys);
      return decisionRequirementsKeys;
    }

    return List.of();
  }

  private boolean hasDeletedAllProcessInstances(
      final HistoryDeletionDbModel historyDeletionDbModel) {
    final boolean hasDependents =
        processInstanceDbReader
                .search(
                    ProcessInstanceQuery.of(
                        b ->
                            b.filter(
                                new ProcessInstanceFilter.Builder()
                                    .processDefinitionKeys(historyDeletionDbModel.resourceKey())
                                    .build())))
                .total()
            != 0;

    if (hasDependents) {
      LOG.debug(
          "Process definition {} still has process instances and will not be deleted.",
          historyDeletionDbModel.resourceKey());
    }
    return !hasDependents;
  }

  private boolean hasDeletedAllDecisionInstances(
      final HistoryDeletionDbModel historyDeletionDbModel) {
    final boolean hasDependents =
        decisionInstanceDbReader
                .search(
                    DecisionInstanceQuery.of(
                        b ->
                            b.filter(
                                new DecisionInstanceFilter.Builder()
                                    .decisionRequirementsKeys(historyDeletionDbModel.resourceKey())
                                    .build())))
                .total()
            != 0;

    if (hasDependents) {
      LOG.debug(
          "Decision requirement {} still has decision instances and will not be deleted.",
          historyDeletionDbModel.resourceKey());
    }
    return !hasDependents;
  }

  private int deleteFromHistoryDeletionTable(final List<Long> deletedResourceKeys) {
    if (deletedResourceKeys.isEmpty()) {
      return 0;
    }

    return rdbmsWriters.getHistoryDeletionWriter().deleteByResourceKeys(deletedResourceKeys);
  }

  private Duration nextDelay(final int deletedResourceCount) {
    if (deletedResourceCount > 0) {
      currentDelayBetweenRuns = config.delayBetweenRuns();
    } else {
      final long nextMs = exponentialBackoff.supplyRetryDelay(currentDelayBetweenRuns.toMillis());
      currentDelayBetweenRuns = Duration.ofMillis(nextMs);
    }
    return currentDelayBetweenRuns;
  }

  public Duration getCurrentDelayBetweenRuns() {
    return currentDelayBetweenRuns;
  }
}
