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

  private final Map<Integer, Boolean> partitionCompletedImporting = new HashMap<>();

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
      partitionCompletedImporting.put(partitionId, false);
      // TODO what if it's not the final list of partitions
      for (final ImportValueType importValueType : IMPORT_VALUE_TYPES) {
        recordsReaders.add(
            beanFactory.getBean(RecordsReader.class, partitionId, importValueType, queueSize));
      }
    }
    return recordsReaders;
  }

  public void setPartitionCompletedImporting(final int partitionId) {
    partitionCompletedImporting.put(partitionId, true);
  }

  public boolean getPartitionCompletedImporting(final int partitionId) {
    return partitionCompletedImporting.get(partitionId);
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
