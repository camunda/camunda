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
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ArchiverUtilElasticSearch extends ArchiverUtilAbstract {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverUtilElasticSearch.class);

  @Autowired private RestHighLevelClient esClient;

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
