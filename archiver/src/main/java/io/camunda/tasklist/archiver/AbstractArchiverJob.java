/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.archiver;

import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.property.TasklistProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.annotation.PreDestroy;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;

public abstract class AbstractArchiverJob implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractArchiverJob.class);

  private boolean shutdown = false;
  private List<Integer> partitionIds;

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  private TaskScheduler archiverExecutor;

  @Autowired private TasklistProperties tasklistProperties;

  public AbstractArchiverJob(List<Integer> partitionIds) {
    this.partitionIds = partitionIds;
  }

  protected abstract int archiveBatch(ArchiveBatch archiveBatch) throws ArchiverException;

  protected abstract ArchiveBatch getNextBatch();

  @Override
  public void run() {
    long delay;
    try {
      final int entitiesCount = archiveNextBatch();
      delay = tasklistProperties.getArchiver().getDelayBetweenRuns();

      if (entitiesCount == 0) {
        // TODO we can implement backoff strategy, if there is not enough data
        delay = 60000;
      }

    } catch (Exception ex) {
      // retry
      LOGGER.error("Error occurred while archiving data. Will be retried.", ex);
      delay = tasklistProperties.getArchiver().getDelayBetweenRuns();
    }
    if (!shutdown) {
      archiverExecutor.schedule(this, Date.from(Instant.now().plus(delay, ChronoUnit.MILLIS)));
    }
  }

  public int archiveNextBatch() throws ArchiverException {
    return archiveBatch(getNextBatch());
  }

  protected ArchiveBatch createArchiveBatch(
      SearchResponse searchResponse, String datesAggName, String instancesAggName) {
    final List<? extends Histogram.Bucket> buckets =
        ((Histogram) searchResponse.getAggregations().get(datesAggName)).getBuckets();

    if (buckets.size() > 0) {
      final Histogram.Bucket bucket = buckets.get(0);
      final String finishDate = bucket.getKeyAsString();
      final SearchHits hits = ((TopHits) bucket.getAggregations().get(instancesAggName)).getHits();
      final ArrayList<String> ids =
          Arrays.stream(hits.getHits())
              .collect(
                  ArrayList::new,
                  (list, hit) -> list.add(hit.getId()),
                  (list1, list2) -> list1.addAll(list2));
      return new ArchiveBatch(finishDate, ids);
    } else {
      return null;
    }
  }

  public List<Integer> getPartitionIds() {
    return partitionIds;
  }

  @PreDestroy
  public void shutdown() {
    shutdown = true;
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
