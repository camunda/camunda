/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.archiver;

import static io.camunda.tasklist.schema.ElasticsearchSchemaManager.INDEX_LIFECYCLE_NAME;
import static io.camunda.tasklist.schema.ElasticsearchSchemaManager.TASKLIST_DELETE_ARCHIVED_INDICES;
import static io.camunda.tasklist.util.ElasticsearchUtil.INTERNAL_SCROLL_KEEP_ALIVE_MS;
import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class ArchiverUtil {

  private static final String INDEX_NAME_PATTERN = "%s%s";
  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverUtil.class);

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  private ThreadPoolTaskScheduler archiverExecutor;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private Metrics metrics;

  @Autowired private TasklistProperties tasklistProperties;

  public CompletableFuture<Void> moveDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final String finishDate,
      final List<String> ids) {
    final var moveDocumentsFuture = new CompletableFuture<Void>();
    final var destinationIndexName = getDestinationIndexName(sourceIndexName, finishDate);

    reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, ids)
        .thenCompose(
            (ignore) -> {
              setIndexLifeCycle(destinationIndexName);
              return deleteDocuments(sourceIndexName, idFieldName, ids);
            })
        .whenComplete(
            (ignore, e) -> {
              if (e != null) {
                moveDocumentsFuture.completeExceptionally(e);
                return;
              }
              moveDocumentsFuture.complete(null);
            });

    return moveDocumentsFuture;
  }

  private void setIndexLifeCycle(final String destinationIndexName) {
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

  public String getDestinationIndexName(String sourceIndexName, String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }

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
              final var result = handleResponse(response, e, sourceIndexName, "delete");
              result.ifRightOrLeft(deleteFuture::complete, deleteFuture::completeExceptionally);
            });

    return deleteFuture;
  }

  private CompletableFuture<Long> reindexDocuments(
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

              final var result = handleResponse(response, e, sourceIndexName, "reindex");
              result.ifRightOrLeft(reindexFuture::complete, reindexFuture::completeExceptionally);
            });

    return reindexFuture;
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

  private <T extends AbstractBulkByScrollRequest<T>> T applyDefaultSettings(T request) {
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

  private Timer getArchiverDeleteQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_DELETE_QUERY);
  }
}
