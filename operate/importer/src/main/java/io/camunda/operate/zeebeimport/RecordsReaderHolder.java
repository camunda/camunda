/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.zeebe.ImportValueType.IMPORT_VALUE_TYPES;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Holder for all possible record readers. It initializes the set of readers creating one reader per
 * each pair [partition id, value type].
 */
@Component
public class RecordsReaderHolder {

  private static final Logger LOGGER = LoggerFactory.getLogger(RecordsReaderHolder.class);

  private Set<RecordsReader> recordsReaders = null;

  @Autowired private BeanFactory beanFactory;

  @Autowired private PartitionHolder partitionHolder;

  @Autowired private OperateProperties operateProperties;

  private final Set<Integer> partitionsCompletedImporting = new HashSet<>();

  private final Map<RecordsReader, Integer> countEmptyBatchesAfterImportingDone = new HashMap<>();

  public Set<RecordsReader> getAllRecordsReaders() {
    if (CollectionUtil.isNotEmpty(recordsReaders)) {
      return recordsReaders;
    }
    recordsReaders = new HashSet<>();
    final int queueSize = operateProperties.getImporter().getQueueSize();
    // create readers
    final List<Integer> partitionIds = partitionHolder.getPartitionIds();
    LOGGER.info("Starting import for partitions: {}", partitionIds);
    for (final Integer partitionId : partitionIds) {
      // TODO what if it's not the final list of partitions
      for (final ImportValueType importValueType : IMPORT_VALUE_TYPES) {
        final var recordReader =
            beanFactory.getBean(RecordsReader.class, partitionId, importValueType, queueSize);
        recordsReaders.add(recordReader);
        countEmptyBatchesAfterImportingDone.put(recordReader, 0);
      }
    }
    return recordsReaders;
  }

  public void addPartitionCompletedImporting(final int partitionId) {
    partitionsCompletedImporting.add(partitionId);
  }

  public boolean hasPartitionCompletedImporting(final int partitionId) {
    return partitionsCompletedImporting.contains(partitionId);
  }

  public void incrementEmptyBatches(final int partitionId, final ImportValueType importValueType) {
    final var reader = getRecordsReader(partitionId, importValueType);
    countEmptyBatchesAfterImportingDone.merge(reader, 1, Integer::sum);
  }

  public boolean isRecordReaderCompletedImporting(
      final int partitionId, final ImportValueType importValueType) {
    if (hasPartitionCompletedImporting(partitionId)) {

      final var reader = getRecordsReader(partitionId, importValueType);
      return countEmptyBatchesAfterImportingDone.get(reader)
          >= operateProperties.getImporter().getCompletedReaderMinEmptyBatches();
    }

    return false;
  }

  public void recordLatestLoadedPositionAsCompleted(
      final ImportPositionHolder importPositionHolder,
      final String aliasTemplate,
      final int partitionId)
      throws IOException {
    final ImportPositionEntity currentLatestPosition =
        importPositionHolder.getLatestScheduledPosition(aliasTemplate, partitionId);
    importPositionHolder.recordLatestLoadedPosition(currentLatestPosition.setCompleted(true));
  }

  @VisibleForTesting
  public void resetCountEmptyBatches() {
    countEmptyBatchesAfterImportingDone.replaceAll((k, v) -> v = 0);
  }

  @VisibleForTesting
  public void resetPartitionsCompletedImporting() {
    partitionsCompletedImporting.clear();
  }

  public RecordsReader getRecordsReader(
      final int partitionId, final ImportValueType importValueType) {
    for (final RecordsReader recordsReader : recordsReaders) {
      if (recordsReader.getPartitionId() == partitionId
          && recordsReader.getImportValueType().equals(importValueType)) {
        return recordsReader;
      }
    }
    return null;
  }
}
