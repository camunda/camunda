/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebe.PartitionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static io.camunda.operate.zeebe.ImportValueType.IMPORT_VALUE_TYPES;

/**
 * Holder for all possible record readers.
 * It initializes the set of readers creating one reader per each pair [partition id, value type].
 */
@Component
public class RecordsReaderHolder {

  private static final Logger logger = LoggerFactory.getLogger(RecordsReaderHolder.class);

  private Set<RecordsReader> recordsReaders = null;

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private OperateProperties operateProperties;

  public Set<RecordsReader> getAllRecordsReaders() {
    if(CollectionUtil.isNotEmpty(recordsReaders)) {
      return recordsReaders;
    }
    recordsReaders = new HashSet<>();
    int queueSize = operateProperties.getImporter().getQueueSize();
    // create readers
    List<Integer> partitionIds = partitionHolder.getPartitionIds();
    logger.info("Starting import for partitions: {}", partitionIds);
    for (Integer partitionId : partitionIds) {
      // TODO what if it's not the final list of partitions
      for (ImportValueType importValueType : IMPORT_VALUE_TYPES) {
        recordsReaders.add(beanFactory.getBean(RecordsReader.class, partitionId, importValueType, queueSize));
      }
    }
    return recordsReaders;
  }

  public RecordsReader getRecordsReader(int partitionId, ImportValueType importValueType) {
    for (RecordsReader recordsReader: recordsReaders) {
      if (recordsReader.getPartitionId() == partitionId && recordsReader.getImportValueType().equals(importValueType)) {
        return recordsReader;
      }
    }
    return null;
  }


}
