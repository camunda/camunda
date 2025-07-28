/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver.es;

import static io.camunda.tasklist.schema.manager.ElasticsearchSchemaManager.INDEX_LIFECYCLE_NAME;
import static io.camunda.tasklist.schema.manager.ElasticsearchSchemaManager.TASKLIST_DELETE_ARCHIVED_INDICES;
import static io.camunda.tasklist.util.ElasticsearchUtil.INTERNAL_SCROLL_KEEP_ALIVE_MS;
import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.archiver.ArchiverUtilAbstract;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.reindex.AbstractBulkByScrollRequest;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ArchiverUtilElasticSearch extends ArchiverUtilAbstract {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverUtilElasticSearch.class);

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  public CompletableFuture<Long> deleteDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys) {
    final var deleteFuture = new CompletableFuture<Long>();
    final var deleteRequest =
        createDeleteByQueryRequestWithDefaults(sourceIndexName)
            .setQuery(termsQuery(idFieldName, processInstanceKeys))
            .setMaxRetries(UPDATE_RETRY_COUNT);
    final var startTimer = Timer.start();

    sendDeleteRequest(deleteRequest)
        .whenComplete(
            (response, e) -> {
              final var timer = getArchiverDeleteQueryTimer();
              startTimer.stop(timer);

              if (e != null) {
                trackMetricForDeleteFailures(processInstanceKeys, e);
              }

              final var result = handleResponse(response, e, sourceIndexName, "delete");
              result.ifRightOrLeft(deleteFuture::complete, deleteFuture::completeExceptionally);
            });

    return deleteFuture;
  }

  public CompletableFuture<Long> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys) {
    final var reindexFuture = new CompletableFuture<Long>();
    final var reindexRequest =
        createReindexRequestWithDefaults()
            .setSourceIndices(sourceIndexName)
            .setDestIndex(destinationIndexName)
            .setSourceQuery(termsQuery(idFieldName, processInstanceKeys));

    final var startTimer = Timer.start();
    sendReindexRequest(reindexRequest)
        .whenComplete(
            (response, e) -> {
              final var reindexTimer = getArchiverReindexQueryTimer();
              startTimer.stop(reindexTimer);

              if (e != null) {
                trackMetricForReindexFailures(processInstanceKeys, e);
              }

              final var result = handleResponse(response, e, sourceIndexName, "reindex");
              result.ifRightOrLeft(reindexFuture::complete, reindexFuture::completeExceptionally);
            });

    return reindexFuture;
  }

  public void setIndexLifeCycle(final String destinationIndexName) {
    try {
      if (tasklistProperties.getArchiver().isIlmEnabled()) {
        esClient
            .indices()
            .putSettings(
                new UpdateSettingsRequest(destinationIndexName)
                    .settings(
                        Settings.builder()
                            .put(INDEX_LIFECYCLE_NAME, TASKLIST_DELETE_ARCHIVED_INDICES)
                            .build()),
                RequestOptions.DEFAULT);
      }
    } catch (Exception e) {
      LOGGER.warn(
          "Could not set ILM policy {} for index {}: {}",
          TASKLIST_DELETE_ARCHIVED_INDICES,
          destinationIndexName,
          e.getMessage());
    }
  }

  private CompletableFuture<BulkByScrollResponse> sendReindexRequest(
      final ReindexRequest reindexRequest) {
    return ElasticsearchUtil.reindexAsync(reindexRequest, archiverExecutor, esClient);
  }

  private ReindexRequest createReindexRequestWithDefaults() {
    final var reindexRequest = new ReindexRequest();
    return applyDefaultSettings(reindexRequest);
  }

  private DeleteByQueryRequest createDeleteByQueryRequestWithDefaults(final String index) {
    final var deleteRequest = new DeleteByQueryRequest(index);
    return applyDefaultSettings(deleteRequest);
  }

  private CompletableFuture<BulkByScrollResponse> sendDeleteRequest(
      final DeleteByQueryRequest deleteRequest) {
    return ElasticsearchUtil.deleteByQueryAsync(deleteRequest, archiverExecutor, esClient);
  }

  private <T extends AbstractBulkByScrollRequest<T>> T applyDefaultSettings(final T request) {
    return request
        .setScroll(TimeValue.timeValueMillis(INTERNAL_SCROLL_KEEP_ALIVE_MS))
        .setAbortOnVersionConflict(false)
        .setSlices(AUTO_SLICES);
  }

  private Either<Throwable, Long> handleResponse(
      final BulkByScrollResponse response,
      final Throwable error,
      final String sourceIndexName,
      final String operation) {
    if (error != null) {
      final var message =
          String.format(
              "Exception occurred, while performing operation %s on source index %s. the documents: %s",
              operation, sourceIndexName, error.getMessage());
      return Either.left(new TasklistRuntimeException(message, error));
    }

    final var bulkFailures = response.getBulkFailures();
    if (bulkFailures.size() > 0) {
      LOGGER.error(
          "Failures occurred when performing operation: {} on source index {}. See details below.",
          operation,
          sourceIndexName);
      bulkFailures.stream().forEach(f -> LOGGER.error(f.toString()));
      return Either.left(new ArchiverException(String.format("Operation % failed", operation)));
    }

    LOGGER.debug(
        "Operation {} succeded on source index {}. Response: {}",
        operation,
        sourceIndexName,
        response.toString());
    return Either.right(response.getTotal());
  }

  private Timer getArchiverReindexQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY);
  }

  private void trackMetricForReindexFailures(
      final List<String> processInstanceKeys, final Throwable e) {
    LOGGER.error(
        "Failed reindex while trying to reindex the following process instance keys [{}]",
        processInstanceKeys);

    metrics.recordCounts(
        Metrics.COUNTER_NAME_REINDEX_FAILURES,
        1,
        "exception",
        e.getCause().getClass().getSimpleName());
  }

  private void trackMetricForDeleteFailures(
      final List<String> processInstanceKeys, final Throwable e) {
    LOGGER.error(
        "Failed deletion while trying to archive the following process instance keys [{}]",
        processInstanceKeys);

    metrics.recordCounts(
        Metrics.COUNTER_NAME_DELETE_FAILURES,
        1,
        "exception",
        e.getCause().getClass().getSimpleName());
  }

  private Timer getArchiverDeleteQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_DELETE_QUERY);
  }
}
