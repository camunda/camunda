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
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.Either;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.metrics.TopHits;
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
  private static final String DATES_AGG = "datesAgg";
  private static final String INSTANCES_AGG = "instancesAgg";

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private Metrics metrics;

  public TaskArchiverJob(final List<Integer> partitionIds) {
    super(partitionIds);
  }

  @Override
  public CompletableFuture<Integer> archiveBatch(ArchiveBatch archiveBatch) {
    final CompletableFuture<Integer> archiveBatchFuture;
    if (archiveBatch != null) {
      LOGGER.debug("Following batch operations are found for archiving: {}", archiveBatch);
      archiveBatchFuture = new CompletableFuture<>();

      // archive task variables
      final var moveVariableDocuments =
          archiverUtil.moveDocuments(
              taskVariableTemplate.getFullQualifiedName(),
              TaskVariableTemplate.TASK_ID,
              archiveBatch.getFinishDate(),
              archiveBatch.getIds());

      // archive tasks
      final var moveTaskDocuments =
          archiverUtil.moveDocuments(
              taskTemplate.getFullQualifiedName(),
              TaskTemplate.ID,
              archiveBatch.getFinishDate(),
              archiveBatch.getIds());

      CompletableFuture.allOf(moveVariableDocuments, moveTaskDocuments)
          .thenAccept((v) -> archiveBatchFuture.complete(archiveBatch.getIds().size()))
          .exceptionally(
              (t) -> {
                archiveBatchFuture.completeExceptionally(t);
                return null;
              });

    } else {
      LOGGER.debug("Nothing to archive");
      archiveBatchFuture = CompletableFuture.completedFuture(0);
    }

    return archiveBatchFuture;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    final var batchFuture = new CompletableFuture<ArchiveBatch>();
    final var aggregation = createFinishedTasksAggregation(DATES_AGG, INSTANCES_AGG);
    final var searchRequest = createFinishedTasksSearchRequest(aggregation);

    final var startTimer = Timer.start();
    sendSearchRequest(searchRequest)
        .whenComplete(
            (response, e) -> {
              final var timer = getArchiverQueryTimer();
              startTimer.stop(timer);

              final var result = handleSearchResponse(response, e);
              result.ifRightOrLeft(batchFuture::complete, batchFuture::completeExceptionally);
            });

    return batchFuture;
  }

  protected Either<Throwable, ArchiveBatch> handleSearchResponse(
      final SearchResponse searchResponse, final Throwable error) {
    if (error != null) {
      final var message =
          String.format(
              "Exception occurred, while obtaining finished batch operations: %s",
              error.getMessage());
      return Either.left(new TasklistRuntimeException(message, error));
    }

    final var batch = createArchiveBatch(searchResponse);
    return Either.right(batch);
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

  protected ArchiveBatch createArchiveBatch(final SearchResponse searchResponse) {
    final List<? extends Histogram.Bucket> buckets =
        ((Histogram) searchResponse.getAggregations().get(DATES_AGG)).getBuckets();

    if (buckets.size() > 0) {
      final Histogram.Bucket bucket = buckets.get(0);
      final String finishDate = bucket.getKeyAsString();
      final SearchHits hits = ((TopHits) bucket.getAggregations().get(INSTANCES_AGG)).getHits();
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

  private Timer getArchiverQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
  }
}
