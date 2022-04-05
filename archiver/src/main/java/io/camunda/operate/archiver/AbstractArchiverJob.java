/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.property.OperateProperties;
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

  private static final Logger logger = LoggerFactory.getLogger(AbstractArchiverJob.class);

  private boolean shutdown = false;

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  private TaskScheduler archiverExecutor;

  @Autowired
  private OperateProperties operateProperties;

  public AbstractArchiverJob() {
  }

  protected abstract int archiveBatch(ArchiveBatch archiveBatch) throws ArchiverException;

  protected abstract ArchiveBatch getNextBatch();

  @Override
  public void run() {
    long delay;
    try {
      int entitiesCount = archiveNextBatch();
      delay = operateProperties.getArchiver().getDelayBetweenRuns();

      if (entitiesCount == 0) {
        //TODO we can implement backoff strategy, if there is not enough data
        delay = 60000;
      }

    } catch (Exception ex) {
      //retry
      logger.error("Error occurred while archiving data. Will be retried.", ex);
      delay = operateProperties.getArchiver().getDelayBetweenRuns();
    }
    if (!shutdown) {
      archiverExecutor.schedule(this, Date.from(Instant.now().plus(delay, ChronoUnit.MILLIS)));
    }
  }

  public int archiveNextBatch() throws ArchiverException {
    return archiveBatch(getNextBatch());
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
