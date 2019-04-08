/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.es.schema.type.AlertType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ALERT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;


@Component
public class AlertWriter {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;

  @Autowired
  public AlertWriter(RestHighLevelClient esClient,
                     ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public AlertDefinitionDto createAlert(AlertDefinitionDto alertDefinitionDto) {
    logger.debug("Writing new alert to Elasticsearch");

    String id = IdGenerator.getNextId();
    alertDefinitionDto.setId(id);
    try {
      IndexRequest request = new IndexRequest(getOptimizeIndexAliasForType(ALERT_TYPE), ALERT_TYPE, id)
        .source(objectMapper.writeValueAsString(alertDefinitionDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write alert to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create alert.";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    logger.debug("alert with [{}] saved to elasticsearch", id);

    return alertDefinitionDto;
  }

  public void updateAlert(AlertDefinitionDto alertUpdate) {
    logger.debug("Updating alert with id [{}] in Elasticsearch", alertUpdate.getId());
    try {
      UpdateRequest request =
        new UpdateRequest(getOptimizeIndexAliasForType(ALERT_TYPE), ALERT_TYPE, alertUpdate.getId())
        .doc(objectMapper.writeValueAsString(alertUpdate), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      UpdateResponse updateResponse = esClient.update(request, RequestOptions.DEFAULT);

      if (updateResponse.getShardInfo().getFailed() > 0) {
        String errorMessage = String.format(
          "Was not able to update alert with id [%s] and name [%s]. " +
            "Error during the update in Elasticsearch.", alertUpdate.getId(), alertUpdate.getName());
        logger.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
      }
    } catch (IOException e) {
      String errorMessage = String.format(
        "Was not able to update alert with id [%s] and name [%s].",
        alertUpdate.getId(),
        alertUpdate.getName()
      );
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (DocumentMissingException e) {
      String errorMessage = String.format(
        "Was not able to update alert with id [%s] and name [%s]. Alert does not exist!",
        alertUpdate.getId(),
        alertUpdate.getName()
      );
      logger.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  public void deleteAlert(String alertId) {
    logger.debug("Deleting alert with id [{}]", alertId);
    DeleteRequest request =
      new DeleteRequest(getOptimizeIndexAliasForType(ALERT_TYPE), ALERT_TYPE, alertId)
      .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete alert with id [%s]. " +
                        "Maybe Optimize is not connected to Elasticsearch?", alertId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
      String message =
        String.format("Could not delete alert with id [%s]. Alert does not exist." +
                        "Maybe it was already deleted by someone else?", alertId);
      logger.error(message);
      throw new NotFoundException(message);
    }
  }

  public void writeAlertStatus(boolean alertStatus, String alertId) {
    logger.debug("Writing alert status for alert with id [{}] to Elasticsearch", alertId);
    try {
      XContentBuilder docFieldToUpdate =
        jsonBuilder()
          .startObject()
          .field(AlertType.TRIGGERED, alertStatus)
          .endObject();
      UpdateRequest request =
        new UpdateRequest(getOptimizeIndexAliasForType(ALERT_TYPE), ALERT_TYPE, alertId)
          .doc(docFieldToUpdate)
          .setRefreshPolicy(IMMEDIATE)
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      esClient.update(request, RequestOptions.DEFAULT);
    } catch (Exception e) {
      logger.error("Can't update status of alert [{}]", alertId, e);
    }
  }

  /**
   * Delete all alerts that are associated with following report ID
   */
  public void deleteAlertsForReport(String reportId) {
    logger.debug("Deleting all alerts for report with id [{}]", reportId);

    TermQueryBuilder query = QueryBuilders.termQuery(AlertType.REPORT_ID, reportId);
    DeleteByQueryRequest request = new DeleteByQueryRequest(getOptimizeIndexAliasForType(ALERT_TYPE))
      .setQuery(query)
      .setRefresh(true);

    BulkByScrollResponse bulkByScrollResponse;
    try {
      bulkByScrollResponse = esClient.deleteByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not delete all alerts for report with id [%s].", reportId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!bulkByScrollResponse.getBulkFailures().isEmpty()) {
      String errorMessage =
        String.format(
          "Could not remove alerts for report id [%s] ! Error response: %s",
          reportId,
          bulkByScrollResponse.getBulkFailures()
        );
      logger.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
    long deleted = bulkByScrollResponse.getDeleted();
    logger.debug("deleted [{}] alerts related to report [{}]", deleted, reportId);
  }
}
