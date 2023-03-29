/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.BackoffIdleStrategy;
import io.camunda.operate.util.ElasticsearchUtil;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public abstract class AbstractArchiverJob implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(AbstractArchiverJob.class);

  protected static final String DATES_AGG = "datesAgg";
  protected static final String INSTANCES_AGG = "instancesAgg";

  private final BackoffIdleStrategy idleStrategy;
  private final BackoffIdleStrategy errorStrategy;

  private boolean shutdown = false;

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  protected ThreadPoolTaskScheduler archiverExecutor;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private OperateProperties operateProperties;

  public AbstractArchiverJob() {
    this.idleStrategy = new BackoffIdleStrategy(2_000, 1.2f, 60_000);
    this.errorStrategy = new BackoffIdleStrategy(100, 1.2f, 10_000);
  }

  protected abstract CompletableFuture<Integer> archiveBatch(ArchiveBatch archiveBatch);

  protected abstract CompletableFuture<ArchiveBatch> getNextBatch();

  @Override
  public void run() {

    archiveNextBatch()
      .thenApply((count) -> {
        errorStrategy.reset();

        if (count >= operateProperties.getArchiver().getRolloverBatchSize()) {
          idleStrategy.reset();
        } else {
          idleStrategy.idle();
        }

        final var delay = Math.max(
            operateProperties.getArchiver().getDelayBetweenRuns(),
            idleStrategy.idleTime());

        return delay;
      })
      .exceptionally((t) -> {
        logger.error("Error occurred while archiving data. Will be retried.", t);
        errorStrategy.idle();
        final var delay = Math.max(
            operateProperties.getArchiver().getDelayBetweenRuns(),
            errorStrategy.idleTime());
        return delay;
      })
      .thenAccept((delay) -> {
        if (!shutdown) {
          archiverExecutor.schedule(this, Date.from(Instant.now().plusMillis(delay)));
        }
      });
  }

  public CompletableFuture<Integer> archiveNextBatch() {
    return getNextBatch().thenCompose(this::archiveBatch);
  }

  protected ArchiveBatch createArchiveBatch(SearchResponse searchResponse, String datesAggName, String instancesAggName) {
    final List<? extends Histogram.Bucket> buckets =
        ((Histogram) searchResponse.getAggregations().get(datesAggName))
            .getBuckets();

    if (buckets.size() > 0) {
      final Histogram.Bucket bucket = buckets.get(0);
      final String finishDate = bucket.getKeyAsString();
      SearchHits hits = ((TopHits)bucket.getAggregations().get(instancesAggName)).getHits();
      final ArrayList<Object> ids = Arrays.stream(hits.getHits())
          .collect(ArrayList::new, (list, hit) -> list.add(hit.getId()), (list1, list2) -> list1.addAll(list2));
      return new ArchiveBatch(finishDate, ids);
    } else {
      return null;
    }
  }

  protected CompletableFuture<SearchResponse> sendSearchRequest(final SearchRequest searchRequest) {
    return ElasticsearchUtil.searchAsync(searchRequest, archiverExecutor, esClient);
  }

  @PreDestroy
  public void shutdown() {
    shutdown = true;
  }

  public static class ArchiveBatch {

    private String finishDate;
    private List<Object> ids;

    public ArchiveBatch(String finishDate, List<Object> ids) {
      this.finishDate = finishDate;
      this.ids = ids;
    }

    public String getFinishDate() {
      return finishDate;
    }

    public void setFinishDate(String finishDate) {
      this.finishDate = finishDate;
    }

    public List<Object> getIds() {
      return ids;
    }

    public void setIds(List<Object> ids) {
      this.ids = ids;
    }

    @Override
    public String toString() {
      return "AbstractArchiverJob{" + "finishDate='" + finishDate + '\'' + ", ids=" + ids + '}';
    }
  }
}
