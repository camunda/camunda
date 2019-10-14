/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.archiver;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.ReindexException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.dateHistogram;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;
import static org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders.bucketSort;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Component
@Scope(SCOPE_PROTOTYPE)
public class ArchiverJob implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(ArchiverJob.class);

  private boolean shutdown = false;

  private List<Integer> partitionIds;

  @Autowired
  private ArchiverHelper archiverHelper;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ListViewTemplate workflowInstanceTemplate;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  private TaskScheduler archiverExecutor;

  public ArchiverJob(List<Integer> partitionIds) {
    this.partitionIds = partitionIds;
  }

  @Override
  public void run() {
    long delay = 1;
    try {
      int entitiesCount = archiveNextBatch();

      if (entitiesCount == 0) {
        //TODO we can implement backoff strategy, if there is not enough data
        delay = 60000;
      }

    } catch (Exception ex) {
      //retry
      logger.error("Error occurred while archiving data. Will be retried.", ex);
      delay = 2000;
    }
    if (!shutdown) {
      archiverExecutor.schedule(this, Date.from(Instant.now().plus(delay, ChronoUnit.MILLIS)));
    }
  }


  public int archiveNextBatch() throws ReindexException {
    return archiverHelper.archiveNextBatch(queryFinishedWorkflowInstances());
  }

  public ArchiveBatch queryFinishedWorkflowInstances() {
    final QueryBuilder endDateQ =
        rangeQuery(ListViewTemplate.END_DATE)
            .lte("now-1h");
    final TermQueryBuilder isWorkflowInstanceQ = termQuery(ListViewTemplate.JOIN_RELATION, ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION);
    final TermsQueryBuilder partitionQ = termsQuery(ListViewTemplate.PARTITION_ID, partitionIds);
    final ConstantScoreQueryBuilder q = constantScoreQuery(ElasticsearchUtil.joinWithAnd(endDateQ, isWorkflowInstanceQ, partitionQ));

    final String datesAgg = "datesAgg";
    final String instancesAgg = "instancesAgg";

    AggregationBuilder agg =
        dateHistogram(datesAgg)
            .field(ListViewTemplate.END_DATE)
            .dateHistogramInterval(new DateHistogramInterval(operateProperties.getArchiver().getRolloverInterval()))
            .format(operateProperties.getArchiver().getElsRolloverDateFormat())
            .keyed(true)      //get result as a map (not an array)
            //we want to get only one bucket at a time
            .subAggregation(
                bucketSort("datesSortedAgg", Arrays.asList(new FieldSortBuilder("_key")))
                    .size(1)
            )
            //we need workflow instance ids, also taking into account batch size
            .subAggregation(
                topHits(instancesAgg)
                    .size(operateProperties.getArchiver().getRolloverBatchSize())
                    .sort(ListViewTemplate.ID, SortOrder.ASC)
                    .fetchSource(ListViewTemplate.ID, null)
            );

    final SearchRequest searchRequest = new SearchRequest(workflowInstanceTemplate.getMainIndexName())
        .source(new SearchSourceBuilder()
            .query(q)
            .aggregation(agg)
            .fetchSource(false)
            .size(0)
            .sort(ListViewTemplate.END_DATE, SortOrder.ASC));

    logger.debug("Finished workflow instances for archiving request: \n{}\n and aggregation: \n{}", q.toString(), agg.toString());

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final List<? extends Histogram.Bucket> buckets =
          ((Histogram) searchResponse.getAggregations().get(datesAgg))
              .getBuckets();

      if (buckets.size() > 0) {
        final Histogram.Bucket bucket = buckets.get(0);
        final String finishDate = bucket.getKeyAsString();
        SearchHits hits = ((TopHits)bucket.getAggregations().get(instancesAgg)).getHits();
        final ArrayList<Long> ids = Arrays.stream(hits.getHits())
            .collect(ArrayList::new, (list, hit) -> list.add(Long.valueOf(hit.getId())), (list1, list2) -> list1.addAll(list2));
        return new ArchiveBatch(finishDate, ids);
      } else {
        return null;
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining finished workflow instances: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @PreDestroy
  public void shutdown() {
    logger.info("Shutdown archiver for partitions: {}", partitionIds);
    shutdown = true;
  }

  public static class ArchiveBatch {

    private String finishDate;
    private List<Long> workflowInstanceKeys;

    public ArchiveBatch(String finishDate, List<Long> workflowInstanceKeys) {
      this.finishDate = finishDate;
      this.workflowInstanceKeys = workflowInstanceKeys;
    }

    public String getFinishDate() {
      return finishDate;
    }

    public void setFinishDate(String finishDate) {
      this.finishDate = finishDate;
    }

    public List<Long> getWorkflowInstanceKeys() {
      return workflowInstanceKeys;
    }

    public void setWorkflowInstanceKeys(List<Long> workflowInstanceKeys) {
      this.workflowInstanceKeys = workflowInstanceKeys;
    }

    @Override
    public String toString() {
      return "ArchiveBatch{" + "finishDate='" + finishDate + '\'' + ", workflowInstanceKeys=" + workflowInstanceKeys + '}';
    }
  }
}
