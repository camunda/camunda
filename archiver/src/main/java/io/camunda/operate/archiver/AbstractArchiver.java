/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.zeebe.PartitionHolder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractArchiver implements Archiver {
  protected static final String INDEX_NAME_PATTERN = "%s%s";

  @Autowired
  protected BeanFactory beanFactory;

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  protected PartitionHolder partitionHolder;

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  protected ThreadPoolTaskScheduler archiverExecutor;

  protected abstract Logger getLogger();

  @Override
  @PostConstruct
  public void startArchiving() {
    if (operateProperties.getArchiver().isRolloverEnabled()) {
      getLogger().info("INIT: Start archiving data...");

      //split the list of partitionIds to parallelize
      List<Integer> partitionIds = partitionHolder.getPartitionIds();
      getLogger().info("Starting archiver for partitions: {}", partitionIds);
      int threadsCount = operateProperties.getArchiver().getThreadsCount();
      if (threadsCount > partitionIds.size()) {
        getLogger().warn("Too many archiver threads are configured, not all of them will be in use. Number of threads: {}, number of partitions to parallelize by: {}",
            threadsCount, partitionIds.size());
      }

      for (int i=0; i < threadsCount; i++) {
        List<Integer> partitionIdsSubset = CollectionUtil.splitAndGetSublist(partitionIds, threadsCount, i);
        if (!partitionIdsSubset.isEmpty()) {
          final var archiverJob = beanFactory.getBean(ProcessInstancesArchiverJob.class, this, partitionIdsSubset);
          archiverExecutor.execute(archiverJob);
        }
        if (partitionIdsSubset.contains(1)) {
          final var batchOperationArchiverJob = beanFactory.getBean(BatchOperationArchiverJob.class, this);
          archiverExecutor.execute(batchOperationArchiverJob);
        }
      }
    }
  }

  @Override
  public CompletableFuture<Void> moveDocuments(String sourceIndexName, String idFieldName, String finishDate,
                                               List<Object> ids) {
    final var destinationIndexName = getDestinationIndexName(sourceIndexName, finishDate);
    return reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, ids).thenCompose(
        (ignore) -> {
          setIndexLifeCycle(destinationIndexName);
          return deleteDocuments(sourceIndexName, idFieldName, ids);
        });
  }

  @Override
  public String getDestinationIndexName(String sourceIndexName, String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }

  protected abstract void setIndexLifeCycle(final String destinationIndexName);

  protected abstract CompletableFuture<Void> deleteDocuments(final String sourceIndexName, final String idFieldName,
      final List<Object> processInstanceKeys);

  protected abstract CompletableFuture<Void> reindexDocuments(final String sourceIndexName, final String destinationIndexName,
      final String idFieldName, final List<Object> processInstanceKeys);

}
