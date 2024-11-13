/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver.os;

import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.archiver.ArchiverUtilAbstract;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.v86.manager.OpenSearchSchemaManager;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.micrometer.core.instrument.Timer;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.ReindexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ArchiverUtilOpenSearch extends ArchiverUtilAbstract {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverUtilOpenSearch.class);

  @Autowired
  @Qualifier("tasklistOsRestClient")
  private RestClient opensearchRestClient;

  @Autowired
  @Qualifier("tasklistOsAsyncClient")
  private OpenSearchAsyncClient osClient;

  @Override
  public CompletableFuture<Long> deleteDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys) {
    final var deleteFuture = new CompletableFuture<Long>();
    final var deleteRequest =
        createDeleteByQueryRequestWithDefaults(sourceIndexName)
            .query(
                q ->
                    q.terms(
                        t ->
                            t.field(idFieldName)
                                .terms(
                                    terms ->
                                        terms.value(
                                            processInstanceKeys.stream()
                                                .map(p -> FieldValue.of(p))
                                                .collect(Collectors.toList())))))
            .build();

    final var startTimer = Timer.start();

    sendDeleteRequest(deleteRequest)
        .whenComplete(
            (response, e) -> {
              final var timer = getArchiverDeleteQueryTimer();
              startTimer.stop(timer);
              final var result =
                  handleDeleteByQueryResponse(response, e, sourceIndexName, "delete");
              result.ifRightOrLeft(deleteFuture::complete, deleteFuture::completeExceptionally);
            });

    return deleteFuture;
  }

  @Override
  public CompletableFuture<Long> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys) {
    final var reindexFuture = new CompletableFuture<Long>();
    final var reindexRequest =
        createReindexRequestWithDefaults()
            .source(
                s ->
                    s.query(
                            q ->
                                q.terms(
                                    t ->
                                        t.field(idFieldName)
                                            .terms(
                                                tv ->
                                                    tv.value(
                                                        processInstanceKeys.stream()
                                                            .map(m -> FieldValue.of(m))
                                                            .collect(Collectors.toList())))))
                        .index(List.of(sourceIndexName)))
            .dest(d -> d.index(destinationIndexName))
            .build();

    final var startTimer = Timer.start();
    sendReindexRequest(reindexRequest)
        .whenComplete(
            (response, e) -> {
              final var reindexTimer = getArchiverReindexQueryTimer();
              startTimer.stop(reindexTimer);

              final var result = handleReindexResponse(response, e, sourceIndexName, "reindex");
              result.ifRightOrLeft(reindexFuture::complete, reindexFuture::completeExceptionally);
            });

    return reindexFuture;
  }

  @Override
  public void setIndexLifeCycle(final String destinationIndexName) {
    if (tasklistProperties.getArchiver().isIlmEnabled()) {
      try {
        final Request request = new Request("POST", "/_plugins/_ism/add/" + destinationIndexName);

        final JsonObject requestJson =
            Json.createObjectBuilder()
                .add(
                    "policy_id",
                    Json.createValue(OpenSearchSchemaManager.TASKLIST_DELETE_ARCHIVED_INDICES))
                .build();
        request.setJsonEntity(requestJson.toString());
        final Response response = opensearchRestClient.performRequest(request);
      } catch (final IOException e) {
        LOGGER.warn(
            "Could not set ILM policy {} for index {}: {}",
            OpenSearchSchemaManager.TASKLIST_DELETE_ARCHIVED_INDICES,
            destinationIndexName,
            e.getMessage());
      }
    }
  }

  private CompletableFuture<ReindexResponse> sendReindexRequest(
      final ReindexRequest reindexRequest) {
    return OpenSearchUtil.reindexAsync(reindexRequest, archiverExecutor, osClient);
  }

  private ReindexRequest.Builder createReindexRequestWithDefaults() {
    final var reindexRequest = new ReindexRequest.Builder();
    return reindexRequest
        .scroll(Time.of(t -> t.time(OpenSearchUtil.INTERNAL_SCROLL_KEEP_ALIVE_MS)))
        .conflicts(Conflicts.Proceed)
        .slices((long) AUTO_SLICES);
  }

  private DeleteByQueryRequest.Builder createDeleteByQueryRequestWithDefaults(final String index) {
    final var deleteRequest = new DeleteByQueryRequest.Builder().index(index);
    return deleteRequest
        .scroll(Time.of(t -> t.time(OpenSearchUtil.INTERNAL_SCROLL_KEEP_ALIVE_MS)))
        .slices((long) AUTO_SLICES)
        .conflicts(Conflicts.Proceed);
  }

  private CompletableFuture<DeleteByQueryResponse> sendDeleteRequest(
      final DeleteByQueryRequest deleteRequest) {
    return OpenSearchUtil.deleteByQueryAsync(deleteRequest, archiverExecutor, osClient);
  }

  private Either<Throwable, Long> handleReindexResponse(
      final ReindexResponse response,
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

    final var bulkFailures = response.failures();
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
    return Either.right(response.total());
  }

  private Either<Throwable, Long> handleDeleteByQueryResponse(
      final DeleteByQueryResponse response,
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

    final var bulkFailures = response.failures();
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
    return Either.right(response.total());
  }

  private Timer getArchiverReindexQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY);
  }

  private Timer getArchiverDeleteQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_DELETE_QUERY);
  }
}
