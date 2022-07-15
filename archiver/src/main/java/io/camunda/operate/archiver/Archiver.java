/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver;

import static io.camunda.operate.util.ElasticsearchUtil.INTERNAL_SCROLL_KEEP_ALIVE_MS;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import io.camunda.operate.Metrics;
import io.camunda.operate.archiver.util.Either;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.zeebe.PartitionHolder;
import io.micrometer.core.instrument.Timer;

import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.reindex.AbstractBulkByScrollRequest;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
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

  private boolean shutdown = false;

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

  public CompletableFuture<Void> moveDocuments(String sourceIndexName, String idFieldName, String finishDate, List<Object> ids) {
    final var moveDocumentsFuture = new CompletableFuture<Void>();
    final var destinationIndexName = getDestinationIndexName(sourceIndexName, finishDate);

    reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, ids)
      .thenCompose((ignore) -> {
        return deleteDocuments(sourceIndexName, idFieldName, ids);
      })
      .whenComplete((ignore, e) -> {
        if (e != null) {
          moveDocumentsFuture.completeExceptionally(e);
          return;
        }
        moveDocumentsFuture.complete(null);
      });

    return moveDocumentsFuture;
  }

  public String getDestinationIndexName(String sourceIndexName, String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }

  private CompletableFuture<Long> deleteDocuments(final String sourceIndexName, final String idFieldName, final List<Object> processInstanceKeys) {
    final var deleteFuture = new CompletableFuture<Long>();
    final var deleteRequest = createDeleteByQueryRequestWithDefaults(sourceIndexName)
        .setQuery(termsQuery(idFieldName, processInstanceKeys));
    final var startTimer = Timer.start();

    sendDeleteRequest(deleteRequest)
      .whenComplete((response, e) -> {
        final var timer = getArchiverDeleteQueryTimer();
        startTimer.stop(timer);

        final var result = handleResponse(response, e, sourceIndexName, "delete");
        result.ifRightOrLeft(deleteFuture::complete, deleteFuture::completeExceptionally);
      });

    return deleteFuture;
  }

  private CompletableFuture<Long> reindexDocuments(final String sourceIndexName, final String destinationIndexName,
      final String idFieldName, final List<Object> processInstanceKeys) {
    final var reindexFuture = new CompletableFuture<Long>();
    final var reindexRequest = createReindexRequestWithDefaults()
        .setSourceIndices(sourceIndexName)
        .setDestIndex(destinationIndexName)
        .setSourceQuery(termsQuery(idFieldName, processInstanceKeys));

    final var startTimer = Timer.start();
    sendReindexRequest(reindexRequest)
      .whenComplete((response, e) -> {
        final var reindexTimer = getArchiverReindexQueryTimer();
        startTimer.stop(reindexTimer);

        final var result = handleResponse(response, e, sourceIndexName, "reindex");
        result.ifRightOrLeft(reindexFuture::complete, reindexFuture::completeExceptionally);
      });

    return reindexFuture;
  }

  private ReindexRequest createReindexRequestWithDefaults() {
    final var reindexRequest = new ReindexRequest();
    return applyDefaultSettings(reindexRequest);
  }

  private CompletableFuture<BulkByScrollResponse> sendReindexRequest(final ReindexRequest reindexRequest) {
    return ElasticsearchUtil.reindexAsync(reindexRequest, archiverExecutor, esClient);
  }

  private Either<Throwable, Long> handleResponse(final BulkByScrollResponse response, final Throwable error, final String sourceIndexName, final String operation) {
    if (error != null) {
      final var exceptionMessage = String.format("Exception occurred when performing operation %s. error: %s", operation, error.getMessage());
      final var archiverException  = new ArchiverException(exceptionMessage, error);
      return Either.left(archiverException);
    }

    final var expected = response.getTotal();
    var actual = response.getUpdated() + response.getCreated() + response.getDeleted(); 

    if (actual < expected) {
      //there were some failures
      final String errorMsg = String.format(
          "Failures occurred when performing operation %s on source index %s. Check Elasticsearch logs.",
          operation, sourceIndexName);
      return Either.left(new OperateRuntimeException(errorMsg));
    }

    logger.debug("Operation {} succeeded on source index {}.", operation, sourceIndexName);
    return Either.right(expected);
  }

  private DeleteByQueryRequest createDeleteByQueryRequestWithDefaults(final String index) {
    final var deleteRequest = new DeleteByQueryRequest(index);
    return applyDefaultSettings(deleteRequest);
  }

  private CompletableFuture<BulkByScrollResponse> sendDeleteRequest(final DeleteByQueryRequest deleteRequest) {
    return ElasticsearchUtil.deleteByQueryAsync(deleteRequest, archiverExecutor, esClient);
  }

  private <T extends AbstractBulkByScrollRequest<T>> T applyDefaultSettings(T request) {
    return request.setScroll(TimeValue.timeValueMillis(INTERNAL_SCROLL_KEEP_ALIVE_MS))
        .setAbortOnVersionConflict(false)
        .setSlices(AUTO_SLICES);
  }

  @PreDestroy
  private void shutdown() {
    shutdown = true;
  }

  private Timer getArchiverReindexQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY);
  }

  private Timer getArchiverDeleteQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_DELETE_QUERY);
  }

}
