/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.read.service.HistoryDeletionDbReader;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryDeletionConfig;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel.HistoryDeletionTypeDbModel;
import java.time.Duration;
import java.util.List;
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
  private final HistoryDeletionConfig config;
  private Duration currentDeletionInterval;

  public HistoryDeletionService(
      final RdbmsWriters rdbmsWriters,
      final HistoryDeletionDbReader historyDeletionDbReader,
      final HistoryDeletionConfig config) {
    this.rdbmsWriters = rdbmsWriters;
    this.historyDeletionDbReader = historyDeletionDbReader;
    this.config = config;
    this.currentDeletionInterval = config.delayBetweenRuns();
  }

  public Duration deleteHistory(final int partitionId) {
    final var batch = historyDeletionDbReader.getNextBatch(partitionId, config.queueBatchSize());
    LOG.trace("Deleting historic data for entities: {}", batch);

    final var deletedProcessInstances = deleteProcessInstances(batch);
    final var deletedResourceCount = deleteFromHistoryDeletionTable(deletedProcessInstances);

    if (deletedResourceCount > 0) {
      return config.delayBetweenRuns();
    } else if (currentDeletionInterval.compareTo(config.maxDelayBetweenRuns()) >= 0) {
      return config.maxDelayBetweenRuns();
    } else {
      currentDeletionInterval = currentDeletionInterval.multipliedBy(2);
      return currentDeletionInterval;
    }
  }

  private List<Long> deleteProcessInstances(final List<HistoryDeletionDbModel> batch) {
    final var processInstanceKeys =
        batch.stream()
            .filter(
                deletionModel ->
                    deletionModel
                        .resourceType()
                        .equals(HistoryDeletionTypeDbModel.PROCESS_INSTANCE))
            .map(HistoryDeletionDbModel::resourceKey)
            .toList();

    if (processInstanceKeys.isEmpty()) {
      return List.of();
    }

    final var allProcessInstanceDependantDataDeleted =
        rdbmsWriters.getProcessInstanceDependantWriters().stream()
            .allMatch(
                dependant -> {
                  final var limit = config.dependentRowLimit();
                  final var deletedRows =
                      dependant.deleteProcessInstanceRelatedData(processInstanceKeys, limit);
                  return deletedRows < limit;
                });

    if (allProcessInstanceDependantDataDeleted) {
      rdbmsWriters.getProcessInstanceWriter().deleteByKeys(processInstanceKeys);
      return processInstanceKeys;
    }

    return List.of();
  }

  private int deleteFromHistoryDeletionTable(final List<Long> deletedResourceKeys) {
    if (deletedResourceKeys.isEmpty()) {
      return 0;
    }

    return rdbmsWriters.getHistoryDeletionWriter().deleteByResourceKeys(deletedResourceKeys);
  }
}
