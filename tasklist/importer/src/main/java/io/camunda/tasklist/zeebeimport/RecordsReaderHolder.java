/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebe.PartitionHolder;
import io.camunda.zeebe.protocol.Protocol;
import java.util.HashSet;
import java.util.List;
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

  private Set<RecordsReader> recordsReader = null;

  @Autowired private BeanFactory beanFactory;

  @Autowired private PartitionHolder partitionHolder;

  @Autowired private TasklistProperties tasklistProperties;

  public Set<RecordsReader> getAllRecordsReaders() {
    if (CollectionUtil.isNotEmpty(recordsReader)) {
      return recordsReader;
    }
    recordsReader = new HashSet<>();
    final int queueSize = tasklistProperties.getImporter().getQueueSize();
    // create readers
    final List<Integer> partitionIds = partitionHolder.getPartitionIds();
    LOGGER.info("Starting import for partitions: {}", partitionIds);
    for (Integer partitionId : partitionIds) {
      // TODO what if it's not the final list of partitions
      for (ImportValueType importValueType : ImportValueType.values()) {
        // we load deployments only from deployment partition
        if (!importValueType.equals(ImportValueType.PROCESS)
            || partitionId.equals(Protocol.DEPLOYMENT_PARTITION)) {
          recordsReader.add(
              beanFactory.getBean(RecordsReader.class, partitionId, importValueType, queueSize));
        }
      }
    }
    return recordsReader;
  }

  public RecordsReader getRecordsReader(int partitionId, ImportValueType importValueType) {
    for (RecordsReader record : recordsReader) {
      if (record.getPartitionId() == partitionId
          && record.getImportValueType().equals(importValueType)) {
        return record;
      }
    }
    return null;
  }
}
