/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver;

import static io.camunda.operate.schema.ElasticsearchSchemaManager.*;
import static io.camunda.operate.util.ElasticsearchUtil.*;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import jakarta.annotation.PostConstruct;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.zeebe.PartitionHolder;
import io.micrometer.core.instrument.Timer;

import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@DependsOn("schemaStartup")
public class Archiver {

  private static final String INDEX_NAME_PATTERN = "%s%s";
  private static final Logger logger = LoggerFactory.getLogger(Archiver.class);

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  private ThreadPoolTaskScheduler archiverExecutor;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private Metrics metrics;

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
          final var archiverJob = beanFactory.getBean(ProcessInstancesArchiverJob.class, partitionIdsSubset);
          archiverExecutor.execute(archiverJob);
        }
        if (partitionIdsSubset.contains(1)) {
          final var batchOperationArchiverJob = beanFactory.getBean(BatchOperationArchiverJob.class);
          archiverExecutor.execute(batchOperationArchiverJob);
        }
      }
    }
  }

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
    try {
      if ( operateProperties.getArchiver().isIlmEnabled() ) {
        esClient.indices().putSettings(new UpdateSettingsRequest(destinationIndexName).settings(
            Settings.builder().put(INDEX_LIFECYCLE_NAME, OPERATE_DELETE_ARCHIVED_INDICES).build()), RequestOptions.DEFAULT);
      }
    } catch (Exception e){
      logger.warn("Could not set ILM policy {} for index {}: {}", OPERATE_DELETE_ARCHIVED_INDICES, destinationIndexName, e.getMessage());
    }
  }

  public String getDestinationIndexName(String sourceIndexName, String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }

  private CompletableFuture<Void> deleteDocuments(final String sourceIndexName, final String idFieldName,
      final List<Object> processInstanceKeys) {
    final var deleteFuture = new CompletableFuture<Void>();

    final var startTimer = Timer.start();
    deleteAsyncWithConnectionRelease(archiverExecutor, sourceIndexName, idFieldName, processInstanceKeys, objectMapper,
        esClient).thenAccept(ignore -> {
      final var deleteTimer = getArchiverDeleteQueryTimer();
      startTimer.stop(deleteTimer);
      deleteFuture.complete(null);
    }).exceptionally((e) -> {
      deleteFuture.completeExceptionally(e);
      return null;
    });
    return deleteFuture;
  }

  private CompletableFuture<Void> reindexDocuments(final String sourceIndexName, final String destinationIndexName,
      final String idFieldName, final List<Object> processInstanceKeys) {
    final var reindexFuture = new CompletableFuture<Void>();
    final var reindexRequest = createReindexRequestWithDefaults().setSourceIndices(sourceIndexName)
        .setDestIndex(destinationIndexName).setSourceQuery(termsQuery(idFieldName, processInstanceKeys));

    final var startTimer = Timer.start();

    ElasticsearchUtil.reindexAsyncWithConnectionRelease(archiverExecutor, reindexRequest, sourceIndexName, esClient)
        .thenAccept(ignore -> {
          final var reindexTimer = getArchiverReindexQueryTimer();
          startTimer.stop(reindexTimer);
          reindexFuture.complete(null);
        }).exceptionally((e) -> {
          reindexFuture.completeExceptionally(e);
          return null;
        });
    return reindexFuture;
  }

  private ReindexRequest createReindexRequestWithDefaults() {
    final var reindexRequest = new ReindexRequest().setScroll(TimeValue.timeValueMillis(INTERNAL_SCROLL_KEEP_ALIVE_MS))
        .setAbortOnVersionConflict(false)
        .setSlices(AUTO_SLICES);
    return reindexRequest;
  }

  private Timer getArchiverReindexQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY);
  }

  private Timer getArchiverDeleteQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_DELETE_QUERY);
  }

}
