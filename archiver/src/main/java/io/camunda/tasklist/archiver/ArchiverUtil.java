/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.archiver;

import static io.camunda.tasklist.util.ElasticsearchUtil.INTERNAL_SCROLL_KEEP_ALIVE_MS;
import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.util.List;
import java.util.concurrent.Callable;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.reindex.AbstractBulkByScrollRequest;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ArchiverUtil {

  private static final String INDEX_NAME_PATTERN = "%s%s";
  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverUtil.class);

  @Autowired private RestHighLevelClient esClient;

  @Autowired private Metrics metrics;

  public void moveDocuments(
      String sourceIndexName, String idFieldName, String finishDate, List<String> ids)
      throws ArchiverException {

    final String destinationIndexName = getDestinationIndexName(sourceIndexName, finishDate);

    reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, ids);

    deleteDocuments(sourceIndexName, idFieldName, ids);
  }

  private BulkByScrollResponse deleteWithTimer(Callable<BulkByScrollResponse> callable)
      throws Exception {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_DELETE_QUERY).recordCallable(callable);
  }

  private BulkByScrollResponse reindexWithTimer(Callable<BulkByScrollResponse> callable)
      throws Exception {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY).recordCallable(callable);
  }

  public String getDestinationIndexName(String sourceIndexName, String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }

  public long deleteDocuments(
      String sourceIndexName, String idFieldName, List<String> processInstanceKeys)
      throws ArchiverException {
    DeleteByQueryRequest request =
        new DeleteByQueryRequest(sourceIndexName)
            .setBatchSize(processInstanceKeys.size())
            .setQuery(termsQuery(idFieldName, processInstanceKeys))
            .setMaxRetries(UPDATE_RETRY_COUNT);
    request = applyDefaultSettings(request);
    try {
      final DeleteByQueryRequest finalRequest = request;
      final BulkByScrollResponse response =
          deleteWithTimer(() -> esClient.deleteByQuery(finalRequest, RequestOptions.DEFAULT));
      return checkResponse(response, sourceIndexName, "delete");
    } catch (ArchiverException ex) {
      throw ex;
    } catch (Exception e) {
      final String message =
          String.format("Exception occurred, while deleting the documents: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private <T extends AbstractBulkByScrollRequest<T>> T applyDefaultSettings(T request) {
    return request
        .setScroll(TimeValue.timeValueMillis(INTERNAL_SCROLL_KEEP_ALIVE_MS))
        .setAbortOnVersionConflict(false)
        .setSlices(AUTO_SLICES);
  }

  private long reindexDocuments(
      String sourceIndexName,
      String destinationIndexName,
      String idFieldName,
      List<String> processInstanceKeys)
      throws ArchiverException {

    ReindexRequest reindexRequest =
        new ReindexRequest()
            .setSourceIndices(sourceIndexName)
            .setSourceBatchSize(processInstanceKeys.size())
            .setDestIndex(destinationIndexName)
            .setSourceQuery(termsQuery(idFieldName, processInstanceKeys));

    reindexRequest = applyDefaultSettings(reindexRequest);

    try {
      final ReindexRequest finalReindexRequest = reindexRequest;
      final BulkByScrollResponse response =
          reindexWithTimer(() -> esClient.reindex(finalReindexRequest, RequestOptions.DEFAULT));

      return checkResponse(response, sourceIndexName, "reindex");
    } catch (ArchiverException ex) {
      throw ex;
    } catch (Exception e) {
      final String message =
          String.format("Exception occurred, while reindexing the documents: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private long checkResponse(
      BulkByScrollResponse response, String sourceIndexName, String operation)
      throws ArchiverException {
    final List<BulkItemResponse.Failure> bulkFailures = response.getBulkFailures();
    if (bulkFailures.size() > 0) {
      LOGGER.error(
          "Failures occurred when performing operation: {} on source index {}. See details below.",
          operation,
          sourceIndexName);
      bulkFailures.stream().forEach(f -> LOGGER.error(f.toString()));
      throw new ArchiverException(String.format("Operation %s failed", operation));
    } else {
      LOGGER.debug(
          "Operation {} succeded on source index {}. Response: {}",
          operation,
          sourceIndexName,
          response.toString());
      return response.getTotal();
    }
  }
}
