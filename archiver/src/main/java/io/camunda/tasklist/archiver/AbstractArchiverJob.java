/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.archiver;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.BackoffIdleStrategy;
import io.camunda.tasklist.util.ElasticsearchUtil;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public abstract class AbstractArchiverJob implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractArchiverJob.class);

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  protected ThreadPoolTaskScheduler archiverExecutor;

  @Autowired protected ArchiverUtil archiverUtil;

  private final BackoffIdleStrategy idleStrategy;
  private final BackoffIdleStrategy errorStrategy;

  private boolean shutdown = false;
  private List<Integer> partitionIds;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RestHighLevelClient esClient;

  public AbstractArchiverJob(final List<Integer> partitionIds) {
    this.partitionIds = partitionIds;
    this.idleStrategy = new BackoffIdleStrategy(2_000, 1.2f, 60_000);
    this.errorStrategy = new BackoffIdleStrategy(100, 1.2f, 10_000);
  }

  protected abstract CompletableFuture<Integer> archiveBatch(final ArchiveBatch archiveBatch);

  protected abstract CompletableFuture<ArchiveBatch> getNextBatch();

  @Override
  public void run() {
    archiveNextBatch()
        .thenApply(
            (count) -> {
              errorStrategy.reset();
              if (count >= tasklistProperties.getArchiver().getRolloverBatchSize()) {
                idleStrategy.reset();
              } else {
                idleStrategy.idle();
              }

              final var delay =
                  Math.max(
                      tasklistProperties.getArchiver().getDelayBetweenRuns(),
                      idleStrategy.idleTime());

              return delay;
            })
        .exceptionally(
            (t) -> {
              LOGGER.error("Error occurred while archiving data. Will be retried.", t);
              errorStrategy.idle();
              return errorStrategy.idleTime();
            })
        .thenAccept(
            (delay) -> {
              if (!shutdown) {
                archiverExecutor.schedule(this, Date.from(Instant.now().plusMillis(delay)));
              }
            });
  }

  public CompletableFuture<Integer> archiveNextBatch() {
    return getNextBatch().thenCompose(this::archiveBatch);
  }

  public List<Integer> getPartitionIds() {
    return partitionIds;
  }

  @PreDestroy
  public void shutdown() {
    shutdown = true;
  }

  protected CompletableFuture<SearchResponse> sendSearchRequest(final SearchRequest searchRequest) {
    return ElasticsearchUtil.searchAsync(searchRequest, archiverExecutor, esClient);
  }

  public static class ArchiveBatch {

    private String finishDate;
    private List<String> ids;

    public ArchiveBatch(final List<String> ids) {
      this.ids = ids;
    }

    public ArchiveBatch(String finishDate, List<String> ids) {
      this.finishDate = finishDate;
      this.ids = ids;
    }

    public String getFinishDate() {
      return finishDate;
    }

    public void setFinishDate(String finishDate) {
      this.finishDate = finishDate;
    }

    public List<String> getIds() {
      return ids;
    }

    public void setIds(List<String> ids) {
      this.ids = ids;
    }

    @Override
    public String toString() {
      return "AbstractArchiverJob{" + "finishDate='" + finishDate + '\'' + ", ids=" + ids + '}';
    }
  }
}
