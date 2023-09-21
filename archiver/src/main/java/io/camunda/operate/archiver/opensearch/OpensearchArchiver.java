/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.Metrics;
import io.camunda.operate.archiver.Archiver;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.zeebe.PartitionHolder;
import jakarta.annotation.PostConstruct;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@DependsOn("schemaStartup")
@Conditional(OpensearchCondition.class)
public class OpensearchArchiver implements Archiver {

  public static final int INTERNAL_SCROLL_KEEP_ALIVE_MS = 30000;    //this scroll timeout value is used for reindex and delete queries
  private static final String INDEX_NAME_PATTERN = "%s%s";
  private static final Logger logger = LoggerFactory.getLogger(OpensearchArchiver.class);

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private OpenSearchClient openSearchClient;

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  private ThreadPoolTaskScheduler archiverExecutor;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private Metrics metrics;

  @Override
  @PostConstruct
  public void startArchiving() {
    if (operateProperties.getArchiver().isRolloverEnabled()) {
      logger.info("INIT: Start archiving data...");

      //split the list of partitionIds to parallelize
      List<Integer> partitionIds = partitionHolder.getPartitionIds();
      logger.info("Starting archiver for partitions: {}", partitionIds);
      int threadsCount = operateProperties.getArchiver().getThreadsCount();
      if (threadsCount > partitionIds.size()) {
        logger.warn("Too many archiver threads are configured, not all of them will be in use. Number of threads: {}, number of partitions to parallelize by: {}",
            threadsCount, partitionIds.size());
      }

      for (int i=0; i < threadsCount; i++) {
        List<Integer> partitionIdsSubset = CollectionUtil.splitAndGetSublist(partitionIds, threadsCount, i);
        if (!partitionIdsSubset.isEmpty()) {
          final var archiverJob = beanFactory.getBean(OpensearchProcessInstancesArchiverJob.class, this, partitionIdsSubset);
          archiverExecutor.execute(archiverJob);
        }
        if (partitionIdsSubset.contains(1)) {
          final var batchOperationArchiverJob = beanFactory.getBean(OpensearchBatchOperationArchiverJob.class, this);
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

  private void setIndexLifeCycle(final String destinationIndexName){
    throw new UnsupportedOperationException();
  }

  @Override
  public String getDestinationIndexName(String sourceIndexName, String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }

  private CompletableFuture<Void> deleteDocuments(final String sourceIndexName, final String idFieldName,
      final List<Object> processInstanceKeys) {
    throw new UnsupportedOperationException();
  }

  private CompletableFuture<Void> reindexDocuments(final String sourceIndexName, final String destinationIndexName,
      final String idFieldName, final List<Object> processInstanceKeys) {
    throw new UnsupportedOperationException();
  }

}
