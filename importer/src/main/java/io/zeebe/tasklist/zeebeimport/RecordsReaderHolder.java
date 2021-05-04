/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport;

import static io.zeebe.tasklist.zeebe.ImportValueType.IMPORT_VALUE_TYPES;

import io.camunda.zeebe.protocol.Protocol;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.util.CollectionUtil;
import io.zeebe.tasklist.zeebe.ImportValueType;
import io.zeebe.tasklist.zeebe.PartitionHolder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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

  @Autowired private TasklistProperties tasklistProperties;

  public Set<RecordsReader> getAllRecordsReaders() {
    if (CollectionUtil.isNotEmpty(recordsReaders)) {
      return recordsReaders;
    }
    recordsReaders = new HashSet<>();
    final int queueSize = tasklistProperties.getImporter().getQueueSize();
    // create readers
    final List<Integer> partitionIds = partitionHolder.getPartitionIds();
    LOGGER.info("Starting import for partitions: {}", partitionIds);
    for (Integer partitionId : partitionIds) {
      // TODO what if it's not the final list of partitions
      for (ImportValueType importValueType : IMPORT_VALUE_TYPES) {
        // we load deployments only from deployment partition
        if (!importValueType.equals(ImportValueType.PROCESS)
            || partitionId.equals(Protocol.DEPLOYMENT_PARTITION)) {
          recordsReaders.add(
              beanFactory.getBean(RecordsReader.class, partitionId, importValueType, queueSize));
        }
      }
    }
    return recordsReaders;
  }

  public Set<RecordsReader> getActiveRecordsReaders() {
    return getAllRecordsReaders().stream().filter(r -> r.isActive()).collect(Collectors.toSet());
  }

  public RecordsReader getRecordsReader(int partitionId, ImportValueType importValueType) {
    for (RecordsReader recordsReader : recordsReaders) {
      if (recordsReader.getPartitionId() == partitionId
          && recordsReader.getImportValueType().equals(importValueType)) {
        return recordsReader;
      }
    }
    return null;
  }
}
