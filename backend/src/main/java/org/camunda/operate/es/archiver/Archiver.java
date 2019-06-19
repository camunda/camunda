/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.archiver;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.camunda.operate.Shutdownable;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.WorkflowInstanceDependant;
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
import static org.elasticsearch.search.aggregations.AggregationBuilders.dateHistogram;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;
import static org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders.bucketSort;

@Component
public class Archiver extends Thread implements Shutdownable{

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

  public void startArchiving() {
    if (operateProperties.getElasticsearch().isRolloverEnabled()) {
      start();
    }
  }

  @PreDestroy
  @Override
  public void shutdown() {
    logger.info("Shutdown Archiver");
    shutdown = true;
  }

  @Override
  public void run() {
    logger.debug("Start archiving data");
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
    final FinishedAtDateIds finishedAtDateIds = queryFinishedWorkflowInstances();
    if (finishedAtDateIds != null) {
      logger.debug("Following workflow instances are found for archiving: {}", finishedAtDateIds);
      try {
        //1st remove dependent data
        for (WorkflowInstanceDependant template: workflowInstanceDependantTemplates) {
          reindexHelper.moveDocuments(template.getMainIndexName(), WorkflowInstanceDependant.WORKFLOW_INSTANCE_ID, finishedAtDateIds.getFinishDate(),
            finishedAtDateIds.getWorkflowInstanceIds());
        }

        //then remove workflow instances themselves
        reindexHelper.moveDocuments(workflowInstanceTemplate.getMainIndexName(), ListViewTemplate.WORKFLOW_INSTANCE_ID, finishedAtDateIds.getFinishDate(),
          finishedAtDateIds.getWorkflowInstanceIds());
        return finishedAtDateIds.getWorkflowInstanceIds().size();
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
    final ConstantScoreQueryBuilder q = constantScoreQuery(ElasticsearchUtil.joinWithAnd(endDateQ, isWorkflowInstanceQ));

    final String datesAgg = "datesAgg";
    final String instancesAgg = "instancesAgg";

    AggregationBuilder agg =
      dateHistogram(datesAgg)
        .field(ListViewTemplate.END_DATE)
        .dateHistogramInterval(new DateHistogramInterval(operateProperties.getElasticsearch().getRolloverInterval()))
        .format(operateProperties.getElasticsearch().getRolloverDateFormat())
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
        final ArrayList<String> ids = Arrays.stream(hits.getHits())
          .collect(ArrayList::new, (list, hit) -> list.add(hit.getId()), (list1, list2) -> list1.addAll(list2));
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

  static class FinishedAtDateIds {

    private String finishDate;
    private List<String> workflowInstanceIds;

    public FinishedAtDateIds(String finishDate, List<String> workflowInstanceIds) {
      this.finishDate = finishDate;
      this.workflowInstanceIds = workflowInstanceIds;
    }

    public String getFinishDate() {
      return finishDate;
    }

    public void setFinishDate(String finishDate) {
      this.finishDate = finishDate;
    }

    public List<String> getWorkflowInstanceIds() {
      return workflowInstanceIds;
    }

    public void setWorkflowInstanceIds(List<String> workflowInstanceIds) {
      this.workflowInstanceIds = workflowInstanceIds;
    }

    @Override
    public String toString() {
      return "FinishedAtDateIds{" + "finishDate='" + finishDate + '\'' + ", workflowInstanceIds=" + workflowInstanceIds + '}';
    }
  }


}
