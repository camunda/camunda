/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.archiver;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import org.camunda.operate.Metrics;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.schema.templates.WorkflowInstanceDependant;
import org.camunda.operate.exceptions.ArchiverException;
import org.camunda.operate.exceptions.OperateRuntimeException;
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
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
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
public class WorkflowInstancesArchiverJob extends AbstractArchiverJob {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowInstancesArchiverJob.class);

  private List<Integer> partitionIds;

  @Autowired
  private Archiver archiver;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ListViewTemplate workflowInstanceTemplate;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private List<WorkflowInstanceDependant> workflowInstanceDependantTemplates;

  @Autowired
  private Metrics metrics;

  protected WorkflowInstancesArchiverJob() {
  }

  public WorkflowInstancesArchiverJob(List<Integer> partitionIds) {
    this.partitionIds = partitionIds;
  }

  @Override
  public ArchiveBatch getNextBatch() {

    final String datesAgg = "datesAgg";
    final String instancesAgg = "instancesAgg";

    final AggregationBuilder agg = createFinishedInstancesAggregation(datesAgg, instancesAgg);

    final SearchRequest searchRequest = createFinishedInstancesSearchRequest(agg);

    try {
      final SearchResponse searchResponse = withTimer(() -> esClient.search(searchRequest, RequestOptions.DEFAULT));

      return createArchiveBatch(searchResponse, datesAgg, instancesAgg);
    } catch (Exception e) {
      final String message = String.format("Exception occurred, while obtaining finished workflow instances: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private SearchRequest createFinishedInstancesSearchRequest(AggregationBuilder agg) {
    final QueryBuilder endDateQ = rangeQuery(ListViewTemplate.END_DATE).lte(operateProperties.getArchiver().getArchivingTimepoint());
    final TermQueryBuilder isWorkflowInstanceQ = termQuery(ListViewTemplate.JOIN_RELATION, ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION);
    final TermsQueryBuilder partitionQ = termsQuery(ListViewTemplate.PARTITION_ID, partitionIds);
    final ConstantScoreQueryBuilder q = constantScoreQuery(ElasticsearchUtil.joinWithAnd(endDateQ, isWorkflowInstanceQ, partitionQ));

    final SearchRequest searchRequest = new SearchRequest(workflowInstanceTemplate.getFullQualifiedName())
        .source(new SearchSourceBuilder()
            .query(q)
            .aggregation(agg)
            .fetchSource(false)
            .size(0)
            .sort(ListViewTemplate.END_DATE, SortOrder.ASC))
        .requestCache(false);  //we don't need to cache this, as each time we need new data

    logger.debug("Finished workflow instances for archiving request: \n{}\n and aggregation: \n{}", q.toString(), agg.toString());
    return searchRequest;
  }

  private AggregationBuilder createFinishedInstancesAggregation(String datesAggName, String instancesAggName) {
    return dateHistogram(datesAggName)
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
            topHits(instancesAggName)
                .size(operateProperties.getArchiver().getRolloverBatchSize())
                .sort(ListViewTemplate.ID, SortOrder.ASC)
                .fetchSource(ListViewTemplate.ID, null)
        );
  }

  private SearchResponse withTimer(Callable<SearchResponse> callable) throws Exception {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY)
        .recordCallable(callable);
  }

  @Override
  public int archiveBatch(WorkflowInstancesArchiverJob.ArchiveBatch archiveBatch) throws ArchiverException {
    if (archiveBatch != null) {
      logger.debug("Following workflow instances are found for archiving: {}", archiveBatch);
      try {
        //1st remove dependent data
        for (WorkflowInstanceDependant template: workflowInstanceDependantTemplates) {
          archiver.moveDocuments(template.getFullQualifiedName(), WorkflowInstanceDependant.WORKFLOW_INSTANCE_KEY, archiveBatch.getFinishDate(),
              archiveBatch.getIds());
        }

        //then remove workflow instances themselves
        archiver.moveDocuments(workflowInstanceTemplate.getFullQualifiedName(), ListViewTemplate.WORKFLOW_INSTANCE_KEY, archiveBatch.getFinishDate(),
            archiveBatch.getIds());
        metrics.recordCounts(Metrics.COUNTER_NAME_ARCHIVED, archiveBatch.getIds().size());
        return archiveBatch.getIds().size();
      } catch (ArchiverException e) {
        throw e;
      }
    } else {
      logger.debug("Nothing to archive");
      return 0;
    }
  }

}
