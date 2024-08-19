/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.ALERT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.schema.index.AlertIndex;
import io.camunda.optimize.service.db.writer.AlertWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.DocWriteResponse;
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
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AlertWriterES implements AlertWriter {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(AlertWriterES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public AlertWriterES(
      final OptimizeElasticsearchClient esClient, final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public AlertDefinitionDto createAlert(final AlertDefinitionDto alertDefinitionDto) {
    log.debug("Writing new alert to Elasticsearch");

    final String id = IdGenerator.getNextId();
    alertDefinitionDto.setId(id);
    try {
      final IndexRequest request =
          new IndexRequest(ALERT_INDEX_NAME)
              .id(id)
              .source(objectMapper.writeValueAsString(alertDefinitionDto), XContentType.JSON)
              .setRefreshPolicy(IMMEDIATE);

      final IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(DocWriteResponse.Result.CREATED)) {
        final String message =
            "Could not write alert to Elasticsearch. "
                + "Maybe the connection to Elasticsearch got lost?";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (final IOException e) {
      final String errorMessage = "Could not create alert.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    log.debug("alert with [{}] saved to elasticsearch", id);

    return alertDefinitionDto;
  }

  @Override
  public void updateAlert(final AlertDefinitionDto alertUpdate) {
    log.debug("Updating alert with id [{}] in Elasticsearch", alertUpdate.getId());
    try {
      final UpdateRequest request =
          new UpdateRequest()
              .index(ALERT_INDEX_NAME)
              .id(alertUpdate.getId())
              .doc(objectMapper.writeValueAsString(alertUpdate), XContentType.JSON)
              .setRefreshPolicy(IMMEDIATE)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      final UpdateResponse updateResponse = esClient.update(request);

      if (updateResponse.getShardInfo().getFailed() > 0) {
        final String errorMessage =
            String.format(
                "Was not able to update alert with id [%s] and name [%s]. "
                    + "Error during the update in Elasticsearch.",
                alertUpdate.getId(), alertUpdate.getName());
        log.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
      }
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Was not able to update alert with id [%s] and name [%s].",
              alertUpdate.getId(), alertUpdate.getName());
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final DocumentMissingException e) {
      final String errorMessage =
          String.format(
              "Was not able to update alert with id [%s] and name [%s]. Alert does not exist!",
              alertUpdate.getId(), alertUpdate.getName());
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  @Override
  public void deleteAlert(final String alertId) {
    log.debug("Deleting alert with id [{}]", alertId);
    final DeleteRequest request =
        new DeleteRequest(ALERT_INDEX_NAME).id(alertId).setRefreshPolicy(IMMEDIATE);

    final DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Could not delete alert with id [%s]. "
                  + "Maybe Optimize is not connected to Elasticsearch?",
              alertId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DocWriteResponse.Result.DELETED)) {
      final String message =
          String.format(
              "Could not delete alert with id [%s]. Alert does not exist. "
                  + "Maybe it was already deleted by someone else?",
              alertId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

  @Override
  public void deleteAlerts(final List<String> alertIds) {
    log.debug("Deleting alerts with ids: {}", alertIds);
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        boolQuery().must(termsQuery(AlertIndex.ID, alertIds)),
        "alerts with Ids" + alertIds,
        true,
        ALERT_INDEX_NAME);
  }

  /** Delete all alerts that are associated with following report ID */
  @Override
  public void deleteAlertsForReport(final String reportId) {
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        QueryBuilders.termQuery(AlertIndex.REPORT_ID, reportId),
        String.format("all alerts for report with ID [%s]", reportId),
        true,
        ALERT_INDEX_NAME);
  }

  @Override
  public void writeAlertTriggeredStatus(final boolean alertStatus, final String alertId) {
    log.debug("Writing alert status for alert with id [{}] to Elasticsearch", alertId);
    try {
      final XContentBuilder docFieldToUpdate =
          jsonBuilder().startObject().field(AlertIndex.TRIGGERED, alertStatus).endObject();
      final UpdateRequest request =
          new UpdateRequest()
              .index(ALERT_INDEX_NAME)
              .id(alertId)
              .doc(docFieldToUpdate)
              .setRefreshPolicy(IMMEDIATE)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      esClient.update(request);
    } catch (final Exception e) {
      log.error("Can't update status of alert [{}]", alertId, e);
    }
  }
}
