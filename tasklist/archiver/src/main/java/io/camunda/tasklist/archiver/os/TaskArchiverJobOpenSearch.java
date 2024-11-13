/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver.os;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.archiver.TaskArchiverJob;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.v86.templates.TaskTemplate;
import io.camunda.tasklist.schema.v86.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.*;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
@Conditional(OpenSearchCondition.class)
public class TaskArchiverJobOpenSearch extends AbstractArchiverJobOpenSearch
    implements TaskArchiverJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskArchiverJobOpenSearch.class);
  private static final String DATES_AGG = "datesAgg";
  private static final String INSTANCES_AGG = "instancesAgg";

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  @Autowired private TasklistProperties tasklistProperties;

  @Qualifier("tasklistOsClient")
  @Autowired
  private OpenSearchClient osClient;

  @Autowired private Metrics metrics;

  public TaskArchiverJobOpenSearch(final List<Integer> partitionIds) {
    super(partitionIds);
  }

  @Override
  public CompletableFuture<Map.Entry<String, Integer>> archiveBatch(ArchiveBatch archiveBatch) {
    final CompletableFuture<Map.Entry<String, Integer>> archiveBatchFuture;
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
          .thenAccept(
              (v) ->
                  archiveBatchFuture.complete(
                      Map.entry(archiveBatch.getFinishDate(), archiveBatch.getIds().size())))
          .exceptionally(
              (exception) -> {
                archiveBatchFuture.completeExceptionally(exception);
                return null;
              });

    } else {
      LOGGER.debug("Nothing to archive");
      archiveBatchFuture = CompletableFuture.completedFuture(Map.entry(NOTHING_TO_ARCHIVE, 0));
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

  private SearchRequest createFinishedTasksSearchRequest(Aggregation agg) {
    final List<FieldValue> partitions =
        getPartitionIds().stream().map(m -> FieldValue.of(m)).collect(Collectors.toList());
    final SearchRequest.Builder builder = new SearchRequest.Builder();

    final Query.Builder endDateQ = new Query.Builder();
    endDateQ.range(
        r ->
            r.field(TaskTemplate.COMPLETION_TIME)
                .lte(JsonData.of(tasklistProperties.getArchiver().getArchivingTimepoint())));

    final Query.Builder partitionQ = new Query.Builder();
    partitionQ.terms(
        terms -> terms.field(TaskTemplate.PARTITION_ID).terms(values -> values.value(partitions)));

    final Query q =
        new Query.Builder()
            .constantScore(cs -> cs.filter(OpenSearchUtil.joinWithAnd(endDateQ, partitionQ)))
            .build();

    builder
        .index(taskTemplate.getFullQualifiedName())
        .query(q)
        .sort(
            s ->
                s.field(
                    FieldSort.of(f -> f.field(TaskTemplate.COMPLETION_TIME).order(SortOrder.Asc))))
        .aggregations(DATES_AGG, agg)
        .size(0)
        .requestCache(false);

    LOGGER.debug(
        "Finished tasks for archiving request: \n{}\n and aggregation: \n{}",
        q.toString(),
        agg.toString());

    return builder.build();
  }

  private Aggregation createFinishedTasksAggregation(String datesAggName, String instancesAggName) {

    final Aggregation dateHistogram =
        new Aggregation.Builder()
            .dateHistogram(
                d ->
                    d.field(TaskTemplate.COMPLETION_TIME)
                        .calendarInterval(
                            CalendarInterval.valueOf(
                                Optional.ofNullable(
                                        tasklistProperties.getArchiver().getRolloverInterval())
                                    .orElse("Day")))
                        .format(tasklistProperties.getArchiver().getElsRolloverDateFormat())
                        .keyed(true))
            .aggregations(
                "datesSortedAgg",
                new Aggregation.Builder()
                    .bucketSort(
                        bs ->
                            bs.sort(
                                s ->
                                    s.field(
                                        FieldSort.of(f -> f.field("_key").order(SortOrder.Desc)))))
                    .build())
            .aggregations(
                instancesAggName,
                new Aggregation.Builder()
                    .topHits(
                        th ->
                            th.size(tasklistProperties.getArchiver().getRolloverBatchSize())
                                .sort(
                                    s ->
                                        s.field(f -> f.field(TaskTemplate.ID).order(SortOrder.Asc)))
                                .source(s -> s.filter(sf -> sf.includes(List.of(TaskTemplate.ID)))))
                    .build())
            .build();

    return dateHistogram;
  }

  protected ArchiveBatch createArchiveBatch(final SearchResponse searchResponse) {
    final Aggregate agg = ((Aggregate) searchResponse.aggregations().get(DATES_AGG));
    final DateHistogramAggregate histogramAgg = (DateHistogramAggregate) agg._get();
    final Buckets<DateHistogramBucket> buckets = histogramAgg.buckets();
    final HashMap bucket = (HashMap) buckets._get();

    if (bucket.size() > 0) {
      final Set<Map.Entry<String, DateHistogramBucket>> bucketEntrySet = bucket.entrySet();
      for (Map.Entry<String, DateHistogramBucket> bucketItem : bucketEntrySet) {
        final String finishDate = bucketItem.getKey();
        final HitsMetadata hits =
            bucketItem.getValue().aggregations().get(INSTANCES_AGG).topHits().hits();

        final List<String> ids =
            (List<String>)
                hits.hits().stream().map(hit -> ((Hit) hit).id()).collect(Collectors.toList());
        return new ArchiveBatch(finishDate, ids);
      }
      return null;
    } else {
      return null;
    }
  }

  private Timer getArchiverQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
  }
}
