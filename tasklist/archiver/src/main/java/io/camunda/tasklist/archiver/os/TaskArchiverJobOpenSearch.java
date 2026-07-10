/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver.os;

import static java.lang.String.format;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.archiver.TaskArchiverJob;
import io.camunda.tasklist.archiver.util.DateOfArchivedDocumentsUtil;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
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
  public CompletableFuture<Map.Entry<String, Integer>> archiveBatch(
      final ArchiveBatch archiveBatch) {
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
    final var searchRequest = createFinishedTasksSearchRequest();

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
          format(
              "Exception occurred, while obtaining finished batch operations: %s",
              error.getMessage());
      return Either.left(new TasklistRuntimeException(message, error));
    }

    final var batch = createArchiveBatch(searchResponse);
    return Either.right(batch);
  }

  private SearchRequest createFinishedTasksSearchRequest() {
    final List<FieldValue> partitions =
        getPartitionIds().stream().map(m -> FieldValue.of(m)).collect(Collectors.toList());

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

    final String dateFormat = tasklistProperties.getArchiver().getElsRolloverDateFormat();

    LOGGER.debug("Finished tasks for archiving request: \n{}", q.toString());

    return new SearchRequest.Builder()
        .index(taskTemplate.getFullQualifiedName())
        .query(q)
        .source(s -> s.fetch(false))
        .fields(f -> f.field(TaskTemplate.COMPLETION_TIME).format(dateFormat))
        .size(tasklistProperties.getArchiver().getRolloverBatchSize())
        .sort(
            s ->
                s.field(
                    FieldSort.of(f -> f.field(TaskTemplate.COMPLETION_TIME).order(SortOrder.Asc))))
        .requestCache(false)
        .build();
  }

  protected ArchiveBatch createArchiveBatch(final SearchResponse searchResponse) {
    final HitsMetadata<?> metadata = searchResponse.hits();
    final List<? extends Hit<?>> hits = metadata.hits();
    if (hits.isEmpty()) {
      return null;
    }
    final String rolloverInterval = tasklistProperties.getArchiver().getRolloverInterval();
    final String dateFormat = tasklistProperties.getArchiver().getElsRolloverDateFormat();
    DateOfArchivedDocumentsUtil.validateRolloverConfiguration(rolloverInterval, dateFormat);
    final String firstCompletionTime = completionTimeOf(hits.get(0));
    final String bucketStart =
        DateOfArchivedDocumentsUtil.getBucketStart(
            firstCompletionTime, rolloverInterval, dateFormat);
    final String nextBucketStart =
        DateOfArchivedDocumentsUtil.getNextBucketStart(
            firstCompletionTime, rolloverInterval, dateFormat);
    // hits are sorted by completionTime ASC, so the first hit defines the bucket.
    // Only the exclusive upper bound needs to be checked.
    final List<String> ids =
        hits.stream()
            .takeWhile(hit -> completionTimeOf(hit).compareTo(nextBucketStart) < 0)
            .map(Hit::id)
            .toList();
    return new ArchiveBatch(bucketStart, ids);
  }

  private static String completionTimeOf(final Hit<?> hit) {
    return hit.fields().get(TaskTemplate.COMPLETION_TIME).toJson().asJsonArray().getString(0);
  }

  private Timer getArchiverQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
  }
}
