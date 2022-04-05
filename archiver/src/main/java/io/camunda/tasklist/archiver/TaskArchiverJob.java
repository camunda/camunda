/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.archiver;

import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.dateHistogram;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;
import static org.elasticsearch.search.aggregations.PipelineAggregatorBuilders.bucketSort;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.micrometer.core.annotation.Timed;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
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

@Component
@Scope(SCOPE_PROTOTYPE)
public class TaskArchiverJob extends AbstractArchiverJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskArchiverJob.class);

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private Archiver archiver;

  public TaskArchiverJob(final List<Integer> partitionIds) {
    super(partitionIds);
  }

  @Override
  public int archiveBatch(ArchiveBatch archiveBatch) throws ArchiverException {
    if (archiveBatch != null) {
      LOGGER.debug("Following batch operations are found for archiving: {}", archiveBatch);
      try {

        // archive task variables
        archiver.moveDocuments(
            taskVariableTemplate.getFullQualifiedName(),
            TaskVariableTemplate.TASK_ID,
            archiveBatch.getFinishDate(),
            archiveBatch.getIds());

        archiver.moveDocuments(
            taskTemplate.getFullQualifiedName(),
            TaskTemplate.ID,
            archiveBatch.getFinishDate(),
            archiveBatch.getIds());
        return archiveBatch.getIds().size();
      } catch (ArchiverException e) {
        throw e;
      }
    } else {
      LOGGER.debug("Nothing to archive");
      return 0;
    }
  }

  @Override
  public ArchiveBatch getNextBatch() {
    final String datesAgg = "datesAgg";
    final String instancesAgg = "instancesAgg";

    final AggregationBuilder agg = createFinishedTasksAggregation(datesAgg, instancesAgg);

    final SearchRequest searchRequest = createFinishedTasksSearchRequest(agg);

    try {
      final SearchResponse searchResponse = runSearch(searchRequest);

      return createArchiveBatch(searchResponse, datesAgg, instancesAgg);
    } catch (Exception e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining finished batch operations: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private SearchRequest createFinishedTasksSearchRequest(AggregationBuilder agg) {
    final QueryBuilder endDateQ =
        rangeQuery(TaskTemplate.COMPLETION_TIME)
            .lte(tasklistProperties.getArchiver().getArchivingTimepoint());
    final TermsQueryBuilder partitionQ = termsQuery(TaskTemplate.PARTITION_ID, getPartitionIds());
    final ConstantScoreQueryBuilder q = constantScoreQuery(joinWithAnd(endDateQ, partitionQ));

    final SearchRequest searchRequest =
        new SearchRequest(taskTemplate.getFullQualifiedName())
            .source(
                new SearchSourceBuilder()
                    .query(q)
                    .aggregation(agg)
                    .fetchSource(false)
                    .size(0)
                    .sort(TaskTemplate.COMPLETION_TIME, SortOrder.ASC))
            .requestCache(false); // we don't need to cache this, as each time we need new data

    LOGGER.debug(
        "Finished tasks for archiving request: \n{}\n and aggregation: \n{}",
        q.toString(),
        agg.toString());
    return searchRequest;
  }

  private AggregationBuilder createFinishedTasksAggregation(
      String datesAggName, String instancesAggName) {
    return dateHistogram(datesAggName)
        .field(TaskTemplate.COMPLETION_TIME)
        .calendarInterval(
            new DateHistogramInterval(tasklistProperties.getArchiver().getRolloverInterval()))
        .format(tasklistProperties.getArchiver().getElsRolloverDateFormat())
        .keyed(true) // get result as a map (not an array)
        // we want to get only one bucket at a time
        .subAggregation(
            bucketSort("datesSortedAgg", Arrays.asList(new FieldSortBuilder("_key"))).size(1))
        // we need process instance ids, also taking into account batch size
        .subAggregation(
            topHits(instancesAggName)
                .size(tasklistProperties.getArchiver().getRolloverBatchSize())
                .sort(TaskTemplate.ID, SortOrder.ASC)
                .fetchSource(TaskTemplate.ID, null));
  }

  @Timed(value = Metrics.TIMER_NAME_ARCHIVER_QUERY, description = "Archiver: search query latency")
  private SearchResponse runSearch(SearchRequest searchRequest) throws IOException {
    return esClient.search(searchRequest, RequestOptions.DEFAULT);
  }
}
