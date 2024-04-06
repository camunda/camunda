/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.archiver.os;

import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.archiver.ArchiverUtilAbstract;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.manager.OpenSearchSchemaManager;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.json.JSONObject;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ArchiverUtilOpenSearch extends ArchiverUtilAbstract {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverUtilOpenSearch.class);

  @Autowired protected RestClient opensearchRestClient;

  @Autowired private OpenSearchAsyncClient osClient;

  public void setIndexLifeCycle(final String destinationIndexName) {
    if (tasklistProperties.getArchiver().isIlmEnabled()) {
      try {
        final Request request = new Request("POST", "_plugins/_ism/add/" + destinationIndexName);

        final JSONObject requestJson = new JSONObject();
        requestJson.put("policy_id", OpenSearchSchemaManager.TASKLIST_DELETE_ARCHIVED_INDICES);
        request.setJsonEntity(requestJson.toString());
        final Response response = opensearchRestClient.performRequest(request);
      } catch (IOException e) {
        LOGGER.warn(
            "Could not set ILM policy {} for index {}: {}",
            OpenSearchSchemaManager.TASKLIST_DELETE_ARCHIVED_INDICES,
            destinationIndexName,
            e.getMessage());
      }
    }
  }

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
