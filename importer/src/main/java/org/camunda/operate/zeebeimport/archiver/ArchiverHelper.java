/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.archiver;

import java.io.IOException;
import java.util.List;
import org.camunda.operate.Metrics;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.ReindexException;
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
public class ArchiverHelper {

  private static final String INDEX_NAME_PATTERN = "%s%s";
  private static final Logger logger = LoggerFactory.getLogger(ArchiverHelper.class);

  @Autowired
  private RestHighLevelClient esClient;

  public void moveDocuments(String sourceIndexName, String idFieldName, String finishDate, List<Long> workflowInstanceKeys) throws ReindexException {

    String destinationIndexName = getDestinationIndexName(sourceIndexName, finishDate);

    reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, workflowInstanceKeys);

    forceMerge(destinationIndexName);

    deleteDocuments(sourceIndexName, idFieldName, workflowInstanceKeys);

  }

  private void forceMerge(String destinationIndexName) {
    ForceMergeRequest request = new ForceMergeRequest(destinationIndexName);
    try {
      esClient.indices().forcemerge(request, RequestOptions.DEFAULT);
    } catch (Exception e) {
      logger.warn("Force merge request failed for index {} with the message {}", destinationIndexName, e.getMessage());
      //ignoring an exception, as it's not crucial for archiving process
    }
  }

  public String getDestinationIndexName(String sourceIndexName, String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }

  private void deleteDocuments(String sourceIndexName, String idFieldName, List<Long> workflowInstanceKeys) throws ReindexException {
    final DeleteByQueryRequest request = new DeleteByQueryRequest(sourceIndexName)
      .setQuery(termsQuery(idFieldName, workflowInstanceKeys))
      .setRefresh(true);
    try {
      final BulkByScrollResponse response = esClient.deleteByQuery(request, RequestOptions.DEFAULT);
      checkResponse(response, sourceIndexName, "delete");
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while deleting the documents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private void reindexDocuments(String sourceIndexName, String destinationIndexName, String idFieldName, List<Long> workflowInstanceKeys)
    throws ReindexException {

    final ReindexRequest reindexRequest = new ReindexRequest()
      .setSourceIndices(sourceIndexName)
      .setDestIndex(destinationIndexName)
      .setSourceQuery(termsQuery(idFieldName, workflowInstanceKeys));

    try {
      BulkByScrollResponse response = esClient.reindex(reindexRequest, RequestOptions.DEFAULT);

      checkResponse(response, sourceIndexName, "reindex");
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while reindexing the documents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private void checkResponse(BulkByScrollResponse response, String sourceIndexName, String operation) throws ReindexException {
    final List<BulkItemResponse.Failure> bulkFailures = response.getBulkFailures();
    if (bulkFailures.size() > 0) {
      logger.error("Failures occurred when performing operation: {} on source index {}. See details below.", operation, sourceIndexName);
      bulkFailures.stream().forEach(f -> logger.error(f.toString()));
      throw new ReindexException(String.format("Operation %s failed", operation));
    } else {
      logger.debug("Operation {} succeded on source index {}. Response: {}", operation, sourceIndexName, response.toString());
    }
  }

}
