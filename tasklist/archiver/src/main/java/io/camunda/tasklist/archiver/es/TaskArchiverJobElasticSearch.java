/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver.es;

import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.archiver.TaskArchiverJob;
import io.camunda.tasklist.archiver.util.DateOfArchivedDocumentsUtil;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.Either;
import io.micrometer.core.instrument.Timer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
@Conditional(ElasticSearchCondition.class)
public class TaskArchiverJobElasticSearch extends AbstractArchiverJobElasticSearch
    implements TaskArchiverJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskArchiverJobElasticSearch.class);

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired private Metrics metrics;

  public TaskArchiverJobElasticSearch(final List<Integer> partitionIds) {
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
              (t) -> {
                archiveBatchFuture.completeExceptionally(t);
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
          String.format(
              "Exception occurred, while obtaining finished batch operations: %s",
              error.getMessage());
      return Either.left(new TasklistRuntimeException(message, error));
    }

    final var batch = createArchiveBatch(searchResponse);
    return Either.right(batch);
  }

  private SearchRequest createFinishedTasksSearchRequest() {
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
                    .fetchSource(false)
                    .docValueField(
                        TaskTemplate.COMPLETION_TIME,
                        tasklistProperties.getArchiver().getElsRolloverDateFormat())
                    .size(tasklistProperties.getArchiver().getRolloverBatchSize())
                    .sort(TaskTemplate.COMPLETION_TIME, SortOrder.ASC))
            .requestCache(false); // we don't need to cache this, as each time we need new data

    LOGGER.debug("Finished tasks for archiving request: \n{}", q.toString());
    return searchRequest;
  }

  protected ArchiveBatch createArchiveBatch(final SearchResponse searchResponse) {
    final SearchHit[] hits = searchResponse.getHits().getHits();
    if (hits.length == 0) {
      return null;
    }
    final String rolloverInterval = tasklistProperties.getArchiver().getRolloverInterval();
    final String dateFormat = tasklistProperties.getArchiver().getElsRolloverDateFormat();
    DateOfArchivedDocumentsUtil.validateRolloverConfiguration(rolloverInterval, dateFormat);
    final String firstCompletionTime = completionTimeOf(hits[0]);
    final String bucketStart =
        DateOfArchivedDocumentsUtil.getBucketStart(
            firstCompletionTime, rolloverInterval, dateFormat);
    final String nextBucketStart =
        DateOfArchivedDocumentsUtil.getNextBucketStart(
            firstCompletionTime, rolloverInterval, dateFormat);
    // hits are sorted by completionTime ASC, so the first hit defines the bucket.
    // Only the exclusive upper bound needs to be checked.
    final List<String> ids =
        Arrays.stream(hits)
            .takeWhile(hit -> completionTimeOf(hit).compareTo(nextBucketStart) < 0)
            .map(SearchHit::getId)
            .toList();
    return new ArchiveBatch(bucketStart, ids);
  }

  private static String completionTimeOf(final SearchHit hit) {
    return hit.field(TaskTemplate.COMPLETION_TIME).getValue();
  }

  private Timer getArchiverQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
  }
}
