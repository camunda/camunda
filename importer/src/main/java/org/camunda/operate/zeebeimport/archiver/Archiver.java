/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.archiver;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.WorkflowInstanceDependant;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.ReindexException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.PartitionHolder;
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
import org.springframework.stereotype.Component;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.dateHistogram;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;
import static org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders.bucketSort;

@Component
public class Archiver extends Thread {

  private static final Logger logger = LoggerFactory.getLogger(ArchiverHelper.class);

  private boolean shutdown = false;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ArchiverHelper reindexHelper;

  @Autowired
  private ListViewTemplate workflowInstanceTemplate;

  @Autowired
  private List<WorkflowInstanceDependant> workflowInstanceDependantTemplates;

  @Autowired
  private PartitionHolder partitionHolder;

  @PostConstruct
  public void startArchiving() {
    if (operateProperties.getElasticsearch().isRolloverEnabled()) {
      start();
    }
  }

  @PreDestroy
  public void shutdown() {
    logger.info("Shutdown Archiver");
    shutdown = true;
  }

  @Override
  public void run() {
    logger.info("INIT: Start archiving data...");
    while (!shutdown) {
      try {

        int entitiesCount = archiveNextBatch();

        //TODO we can implement backoff strategy, if there is not enough data
        if (entitiesCount == 0) {
          try {
            Thread.sleep(60000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      } catch (Exception ex) {
        //retry
        logger.error("Error occurred while archiving data. Will be retried.", ex);
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }

    }
  }

  public int archiveNextBatch() throws ReindexException {
    return archiveNextBatch(queryFinishedWorkflowInstances());
  }
  
  public int archiveNextBatch(FinishedAtDateIds finishedAtDateIds) throws ReindexException {
    if (finishedAtDateIds != null) {
      logger.debug("Following workflow instances are found for archiving: {}", finishedAtDateIds);
      try {
        //1st remove dependent data
        for (WorkflowInstanceDependant template: workflowInstanceDependantTemplates) {
          reindexHelper.moveDocuments(template.getMainIndexName(), WorkflowInstanceDependant.WORKFLOW_INSTANCE_KEY, finishedAtDateIds.getFinishDate(),
            finishedAtDateIds.getWorkflowInstanceKeys());
        }

        //then remove workflow instances themselves
        reindexHelper.moveDocuments(workflowInstanceTemplate.getMainIndexName(), ListViewTemplate.WORKFLOW_INSTANCE_KEY, finishedAtDateIds.getFinishDate(),
          finishedAtDateIds.getWorkflowInstanceKeys());
        return finishedAtDateIds.getWorkflowInstanceKeys().size();
      } catch (ReindexException e) {
        logger.error(e.getMessage(), e);
        throw e;
      }
    } else {
      logger.debug("Nothing to archive");
      return 0;
    }
  }

  public FinishedAtDateIds queryFinishedWorkflowInstances() {
    final QueryBuilder endDateQ =
      rangeQuery(ListViewTemplate.END_DATE)
        .lte("now-1h");
    final TermQueryBuilder isWorkflowInstanceQ = termQuery(ListViewTemplate.JOIN_RELATION, ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION);
    final TermsQueryBuilder belognsToPartitions = termsQuery(ListViewTemplate.PARTITION_ID, partitionHolder.getPartitionIds());
    final ConstantScoreQueryBuilder q = constantScoreQuery(ElasticsearchUtil.joinWithAnd(endDateQ, isWorkflowInstanceQ, belognsToPartitions));

    final String datesAgg = "datesAgg";
    final String instancesAgg = "instancesAgg";

    AggregationBuilder agg =
      dateHistogram(datesAgg)
        .field(ListViewTemplate.END_DATE)
        .dateHistogramInterval(new DateHistogramInterval(operateProperties.getElasticsearch().getRolloverInterval()))
        .format(operateProperties.getElasticsearch().getElsRolloverDateFormat())
        .keyed(true)      //get result as a map (not an array)
        //we want to get only one bucket at a time
        .subAggregation(
          bucketSort("datesSortedAgg", Arrays.asList(new FieldSortBuilder("_key")))
            .size(1)
        )
        //we need workflow instance ids, also taking into account batch size
        .subAggregation(
          topHits(instancesAgg)
            .size(operateProperties.getElasticsearch().getRolloverBatchSize())
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
        return new FinishedAtDateIds(finishDate, ids);
      } else {
        return null;
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining finished workflow instances: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  public static class FinishedAtDateIds {

    private String finishDate;
    private List<Long> workflowInstanceKeys;

    public FinishedAtDateIds(String finishDate, List<Long> workflowInstanceKeys) {
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
      return "FinishedAtDateIds{" + "finishDate='" + finishDate + '\'' + ", workflowInstanceKeys=" + workflowInstanceKeys + '}';
    }
  }


}
