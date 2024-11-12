/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.ALERT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.ids;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static java.lang.String.format;

import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.schema.index.AlertIndex;
import io.camunda.optimize.service.db.writer.AlertWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class AlertWriterOS implements AlertWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AlertWriterOS.class);
  private final OptimizeOpenSearchClient osClient;

  public AlertWriterOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public AlertDefinitionDto createAlert(final AlertDefinitionDto alertDefinitionDto) {
    LOG.debug("Writing new alert to OpenSearch");

    final String id = IdGenerator.getNextId();
    alertDefinitionDto.setId(id);

    final IndexRequest.Builder<AlertDefinitionDto> request =
        new IndexRequest.Builder<AlertDefinitionDto>()
            .index(ALERT_INDEX_NAME)
            .id(id)
            .document(alertDefinitionDto)
            .refresh(Refresh.True);

    final IndexResponse indexResponse = osClient.index(request);

    if (!indexResponse.result().equals(Result.Created)) {
      final String message =
          "Could not write alert to OpenSearch. Maybe the connection to OpenSearch got lost?";
      LOG.error(message);
      throw new OptimizeRuntimeException(message);
    }

    LOG.debug("alert with [{}] saved to opensearch", id);

    return alertDefinitionDto;
  }

  @Override
  public void updateAlert(final AlertDefinitionDto alertUpdate) {
    LOG.debug("Updating alert with id [{}] in OpenSearch", alertUpdate.getId());

    final UpdateRequest.Builder<Void, AlertDefinitionDto> requestBuilder =
        RequestDSL.<Void, AlertDefinitionDto>updateRequestBuilder(ALERT_INDEX_NAME)
            .id(alertUpdate.getId())
            .doc(alertUpdate)
            .refresh(Refresh.True)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    final UpdateResponse<Void> response =
        osClient.update(
            requestBuilder,
            e -> {
              final String errorMessage = "There were errors while updating alerts to OS.";
              LOG.error(errorMessage, e);
              return errorMessage + e.getMessage();
            });

    if (response.shards().failed().intValue() > 0) {
      final String errorMessage =
          format(
              "Was not able to update alert with id [%s] and name [%s]. Error during the update in Opensearch.",
              alertUpdate.getId(), alertUpdate.getName());
      LOG.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  @Override
  public void deleteAlert(final String alertId) {
    LOG.debug("Deleting alert with id [{}]", alertId);
    final DeleteRequest.Builder request =
        new DeleteRequest.Builder().index(ALERT_INDEX_NAME).id(alertId).refresh(Refresh.True);

    final DeleteResponse deleteResponse =
        osClient.delete(
            request,
            e -> {
              final String error =
                  format(
                      "Could not delete alert with id [%s]. Maybe Optimize is not connected to OpenSearch?",
                      alertId);
              LOG.error(error, e);
              return "There were errors while deleting alerts to OS." + e.getMessage();
            });

    if (!deleteResponse.result().equals(Result.Deleted)) {
      final String error =
          format(
              "Could not delete alert with id [%s]. Alert does not exist. Maybe it was already deleted by someone else?",
              alertId);
      LOG.error(error);
      throw new NotFoundException(error);
    }
  }

  @Override
  public void deleteAlerts(final List<String> alertIds) {
    LOG.debug("Deleting alerts with ids: {}", alertIds);
    osClient.deleteByQuery(ids(alertIds), true, ALERT_INDEX_NAME);
  }

  @Override
  public void writeAlertTriggeredStatus(final boolean alertStatus, final String alertId) {
    record AlertTriggered(boolean triggered) {}

    try {
      LOG.debug("Writing alert status for alert with id [{}] to OpenSearch", alertId);
      final UpdateRequest.Builder<Void, AlertTriggered> request =
          new UpdateRequest.Builder<Void, AlertTriggered>()
              .index(ALERT_INDEX_NAME)
              .id(alertId)
              .doc(new AlertTriggered(alertStatus))
              .refresh(Refresh.True)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      osClient.update(
          request,
          e -> {
            final String errorMessage =
                String.format(
                    "Could not update status of alert with id [%s]. "
                        + "Maybe Optimize is not connected to OpenSearch?",
                    alertId);
            LOG.error(errorMessage, e);
            return "There were errors while updating status alerts to OS." + e.getMessage();
          });
    } catch (final Exception e) {
      LOG.error("Can't update status of alert [{}]", alertId, e);
    }
  }

  /** Delete all alerts that are associated with following report ID */
  @Override
  public void deleteAlertsForReport(final String reportId) {
    osClient.deleteByQuery(term(AlertIndex.REPORT_ID, reportId), true, ALERT_INDEX_NAME);
  }
}
