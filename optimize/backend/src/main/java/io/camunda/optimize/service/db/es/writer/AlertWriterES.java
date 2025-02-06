/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static co.elastic.clients.elasticsearch._types.Result.Created;
import static co.elastic.clients.elasticsearch._types.Result.Deleted;
import static io.camunda.optimize.service.db.DatabaseConstants.ALERT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.db.schema.index.AlertIndex;
import io.camunda.optimize.service.db.writer.AlertWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AlertWriterES implements AlertWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AlertWriterES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final TaskRepositoryES taskRepositoryES;

  public AlertWriterES(
      final OptimizeElasticsearchClient esClient,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper,
      final TaskRepositoryES taskRepositoryES) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.taskRepositoryES = taskRepositoryES;
  }

  @Override
  public AlertDefinitionDto createAlert(final AlertDefinitionDto alertDefinitionDto) {
    LOG.debug("Writing new alert to Elasticsearch");

    final String id = IdGenerator.getNextId();
    alertDefinitionDto.setId(id);
    try {
      final IndexResponse indexResponse =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  i ->
                      i.optimizeIndex(esClient, ALERT_INDEX_NAME)
                          .id(id)
                          .document(alertDefinitionDto)
                          .refresh(Refresh.True)));

      if (!indexResponse.result().equals(Created)) {
        final String message =
            "Could not write alert to Elasticsearch. "
                + "Maybe the connection to Elasticsearch got lost?";
        LOG.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (final IOException e) {
      final String errorMessage = "Could not create alert.";
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    LOG.debug("alert with [{}] saved to elasticsearch", id);

    return alertDefinitionDto;
  }

  @Override
  public void updateAlert(final AlertDefinitionDto alertUpdate) {
    LOG.debug("Updating alert with id [{}] in Elasticsearch", alertUpdate.getId());
    try {

      final UpdateResponse<AlertDefinitionDto> updateResponse =
          esClient.update(
              new OptimizeUpdateRequestBuilderES<AlertDefinitionDto, AlertDefinitionDto>()
                  .optimizeIndex(esClient, ALERT_INDEX_NAME)
                  .id(alertUpdate.getId())
                  .doc(alertUpdate)
                  .refresh(Refresh.True)
                  .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                  .build(),
              AlertDefinitionDto.class);

      if (!updateResponse.shards().failures().isEmpty()) {
        final String errorMessage =
            String.format(
                "Was not able to update alert with id [%s] and name [%s]. "
                    + "Error during the update in Elasticsearch.",
                alertUpdate.getId(), alertUpdate.getName());
        LOG.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
      }
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Was not able to update alert with id [%s] and name [%s].",
              alertUpdate.getId(), alertUpdate.getName());
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final ElasticsearchException e) {
      if (e.response().status() == BAD_REQUEST.code()) {
        final String errorMessage =
            String.format(
                "Was not able to update alert with id [%s] and name [%s]. Alert does not exist!",
                alertUpdate.getId(), alertUpdate.getName());
        LOG.error(errorMessage, e);
        throw new NotFoundException(errorMessage, e);
      } else {
        throw e;
      }
    }
  }

  @Override
  public void deleteAlert(final String alertId) {
    LOG.debug("Deleting alert with id [{}]", alertId);
    final DeleteResponse deleteResponse;
    try {
      deleteResponse =
          esClient.delete(
              OptimizeDeleteRequestBuilderES.of(
                  d ->
                      d.optimizeIndex(esClient, ALERT_INDEX_NAME)
                          .id(alertId)
                          .refresh(Refresh.True)));
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Could not delete alert with id [%s]. "
                  + "Maybe Optimize is not connected to Elasticsearch?",
              alertId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.result().equals(Deleted)) {
      final String message =
          String.format(
              "Could not delete alert with id [%s]. Alert does not exist. "
                  + "Maybe it was already deleted by someone else?",
              alertId);
      LOG.error(message);
      throw new NotFoundException(message);
    }
  }

  @Override
  public void deleteAlerts(final List<String> alertIds) {
    LOG.debug("Deleting alerts with ids: {}", alertIds);
    taskRepositoryES.tryDeleteByQueryRequest(
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.must(
                            m ->
                                m.terms(
                                    t ->
                                        t.field(AlertIndex.ID)
                                            .terms(
                                                tt ->
                                                    tt.value(
                                                        alertIds.stream()
                                                            .map(FieldValue::of)
                                                            .toList())))))),
        "alerts with Ids" + alertIds,
        true,
        ALERT_INDEX_NAME);
  }

  /** Delete all alerts that are associated with following report ID */
  @Override
  public void deleteAlertsForReport(final String reportId) {
    taskRepositoryES.tryDeleteByQueryRequest(
        Query.of(q -> q.term(t -> t.field(AlertIndex.REPORT_ID).value(reportId))),
        String.format("all alerts for report with ID [%s]", reportId),
        true,
        ALERT_INDEX_NAME);
  }

  @Override
  public void writeAlertTriggeredStatus(final boolean alertStatus, final String alertId) {
    LOG.debug("Writing alert status for alert with id [{}] to Elasticsearch", alertId);
    try {
      esClient.update(
          new OptimizeUpdateRequestBuilderES<JsonObject, JsonObject>()
              .optimizeIndex(esClient, ALERT_INDEX_NAME)
              .id(alertId)
              .doc(Json.createObjectBuilder().add(AlertIndex.TRIGGERED, alertStatus).build())
              .refresh(Refresh.True)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
              .build(),
          JsonObject.class);
    } catch (final Exception e) {
      LOG.error("Can't update status of alert [{}]", alertId, e);
    }
  }
}
