/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.AlertIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ALERT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;


@AllArgsConstructor
@Component
@Slf4j
public class AlertWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public AlertDefinitionDto createAlert(AlertDefinitionDto alertDefinitionDto) {
    log.debug("Writing new alert to Elasticsearch");

    String id = IdGenerator.getNextId();
    alertDefinitionDto.setId(id);
    try {
      IndexRequest request = new IndexRequest(ALERT_INDEX_NAME)
        .id(id)
        .source(objectMapper.writeValueAsString(alertDefinitionDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write alert to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create alert.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    log.debug("alert with [{}] saved to elasticsearch", id);

    return alertDefinitionDto;
  }

  public void updateAlert(AlertDefinitionDto alertUpdate) {
    log.debug("Updating alert with id [{}] in Elasticsearch", alertUpdate.getId());
    try {
      UpdateRequest request =
        new UpdateRequest()
          .index(ALERT_INDEX_NAME)
          .id(alertUpdate.getId())
          .doc(objectMapper.writeValueAsString(alertUpdate), XContentType.JSON)
          .setRefreshPolicy(IMMEDIATE)
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      UpdateResponse updateResponse = esClient.update(request);

      if (updateResponse.getShardInfo().getFailed() > 0) {
        String errorMessage = String.format(
          "Was not able to update alert with id [%s] and name [%s]. " +
            "Error during the update in Elasticsearch.", alertUpdate.getId(), alertUpdate.getName());
        log.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
      }
    } catch (IOException e) {
      String errorMessage = String.format(
        "Was not able to update alert with id [%s] and name [%s].",
        alertUpdate.getId(),
        alertUpdate.getName()
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (DocumentMissingException e) {
      String errorMessage = String.format(
        "Was not able to update alert with id [%s] and name [%s]. Alert does not exist!",
        alertUpdate.getId(),
        alertUpdate.getName()
      );
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  public void deleteAlert(String alertId) {
    log.debug("Deleting alert with id [{}]", alertId);
    DeleteRequest request =
      new DeleteRequest(ALERT_INDEX_NAME)
        .id(alertId)
        .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete alert with id [%s]. " +
                        "Maybe Optimize is not connected to Elasticsearch?", alertId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
      String message =
        String.format("Could not delete alert with id [%s]. Alert does not exist." +
                        "Maybe it was already deleted by someone else?", alertId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

  public void deleteAlerts(List<String> alertIds) {
    log.debug("Deleting alerts with ids: {}", alertIds);
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
      esClient,
      boolQuery().must(termsQuery(AlertIndex.ID, alertIds)),
      "alerts with Ids" + alertIds,
      true,
      ALERT_INDEX_NAME
    );
  }

  public void writeAlertTriggeredStatus(boolean alertStatus, String alertId) {
    log.debug("Writing alert status for alert with id [{}] to Elasticsearch", alertId);
    try {
      XContentBuilder docFieldToUpdate =
        jsonBuilder()
          .startObject()
          .field(AlertIndex.TRIGGERED, alertStatus)
          .endObject();
      UpdateRequest request =
        new UpdateRequest()
          .index(ALERT_INDEX_NAME)
          .id(alertId)
          .doc(docFieldToUpdate)
          .setRefreshPolicy(IMMEDIATE)
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      esClient.update(request);
    } catch (Exception e) {
      log.error("Can't update status of alert [{}]", alertId, e);
    }
  }

  /**
   * Delete all alerts that are associated with following report ID
   */
  public void deleteAlertsForReport(String reportId) {
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
      esClient,
      QueryBuilders.termQuery(AlertIndex.REPORT_ID, reportId),
      String.format("all alerts for report with ID [%s]", reportId),
      true,
      ALERT_INDEX_NAME
    );
  }
}
