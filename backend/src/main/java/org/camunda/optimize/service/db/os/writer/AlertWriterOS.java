/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.db.writer.AlertWriter;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class AlertWriterOS implements AlertWriter {

  private final OptimizeOpenSearchClient osClient;

  public AlertDefinitionDto createAlert(AlertDefinitionDto alertDefinitionDto) {
//        log.debug("Writing new alert to OpenSearch");
//
//        String id = IdGenerator.getNextId();
//        alertDefinitionDto.setId(id);
//
//        IndexRequest.Builder<AlertDefinitionDto> request = new IndexRequest.Builder<AlertDefinitionDto>()
//                .index(ALERT_INDEX_NAME)
//                .id(id)
//                .document(alertDefinitionDto)
//                .refresh(Refresh.True);
//
//        IndexResponse indexResponse = osClient.index(request);
//
//        if (!indexResponse.result().equals(Result.Created)) {
//            String message = "Could not write alert to OpenSearch. " +
//                    "Maybe the connection to OpenSearch got lost?";
//            log.error(message);
//            throw new OptimizeRuntimeException(message);
//        }
//
//        log.debug("alert with [{}] saved to elasticsearch", id);
//
//        return alertDefinitionDto;
    //todo will be handled in the OPT-7376
    throw new NotImplementedException();
  }

  public void updateAlert(AlertDefinitionDto alertUpdate) {
//        log.debug("Updating alert with id [{}] in OpenSearch", alertUpdate.getId());
//        // try {
//        UpdateRequest.Builder<Void, AlertDefinitionDto> request =
//                new UpdateRequest.Builder<Void, AlertDefinitionDto>()
//                        .index(ALERT_INDEX_NAME)
//                        .id(alertUpdate.getId())
//                        .doc(alertUpdate)
//                        .refresh(Refresh.True)
//                        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
//
//        UpdateResponse<Void> updateResponse = osClient.update(request, e -> {
//            final String errorMessage = "There were errors while updating alerts to OS.";
//            log.error(errorMessage, e);
//            return "There were errors while updating alerts to OS." + e.getMessage();
//        });
//
//        //todo test coverage
//        if (updateResponse.shards().failed().intValue() > 0) {
//            String errorMessage = String.format(
//                    "Was not able to update alert with id [%s] and name [%s]. " +
//                            "Error during the update in Elasticsearch.", alertUpdate.getId(), alertUpdate.getName());
//            log.error(errorMessage);
//            throw new OptimizeRuntimeException(errorMessage);
//        }
    //todo will be handled in the OPT-7376
  }

  public void deleteAlert(String alertId) {
//        log.debug("Deleting alert with id [{}]", alertId);
//        DeleteRequest.Builder request =
//                new DeleteRequest.Builder().index(ALERT_INDEX_NAME)
//                        .id(alertId)
//                        .refresh(Refresh.True);
//
//        DeleteResponse deleteResponse = osClient.delete(request, e -> {
//            final String errorMessage = String.format("Could not delete alert with id [%s]. " +
//                    "Maybe Optimize is not connected to OpenSearch?", alertId);
//            log.error(errorMessage, e);
//            return "There were errors while deleting alerts to OS." + e.getMessage();
//        });
//
//        if (!deleteResponse.result().equals(Result.Deleted)) {
//            String message =
//                    String.format("Could not delete alert with id [%s]. Alert does not exist." +
//                            "Maybe it was already deleted by someone else?", alertId);
//            log.error(message);
//            throw new NotFoundException(message);
//        }
    //todo will be handled in the OPT-7376
  }

  public void deleteAlerts(List<String> alertIds) {
//        log.debug("Deleting alerts with ids: {}", alertIds);
//
//        //todo check if cannot point the AlertIndex.ID
//        var query = QueryDSL.ids(alertIds);
//
//        DeleteByQueryRequest.Builder deleteByQueryRequest = new DeleteByQueryRequest.Builder()
//                .index(Collections.singletonList(ALERT_INDEX_NAME))
//                .query(query);
//
//        osClient.delete(deleteByQueryRequest, e -> {
//            final String errorMessage = String.format("Could not delete alert with id [%s]. " +
//                    "Maybe Optimize is not connected to OpenSearch?", alertIds);
//            log.error(errorMessage, e);
//            return "There were errors while deleting alerts to OS." + e.getMessage();
//        });

    //todo will be handled in the OPT-7376

  }

  public void writeAlertTriggeredStatus(boolean alertStatus, String alertId) {
//        log.debug("Writing alert status for alert with id [{}] to OpenSearch", alertId);
//        try {
//
//            //todo elasticsearch obj, change it to custom entity
//            XContentBuilder docFieldToUpdate =
//                    jsonBuilder()
//                            .startObject()
//                            .field(AlertIndex.TRIGGERED, alertStatus)
//                            .endObject();
//
//
//            UpdateRequest.Builder<Void, XContentBuilder> request =
//                    new UpdateRequest.Builder<Void, XContentBuilder>()
//                            .index(ALERT_INDEX_NAME)
//                            .id(alertId)
//                            .doc(docFieldToUpdate)
//                            .refresh(Refresh.True)
//                            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
//
//            osClient.update(request, e -> {
//                final String errorMessage = String.format("Could not update status of alert with id [%s]. " +
//                        "Maybe Optimize is not connected to OpenSearch?", alertId);
//                log.error(errorMessage, e);
//                return "There were errors while updating status alerts to OS." + e.getMessage();
//            });
//
//
//        } catch (Exception e) {
//            log.error("Can't update status of alert [{}]", alertId, e);
//        }

    //todo will be handled in the OPT-7376
  }

  /**
   * Delete all alerts that are associated with following report ID
   */
  @Override
  public void deleteAlertsForReport(String reportId) {
//        var query = QueryDSL.term(AlertIndex.REPORT_ID, reportId);
//
//        DeleteByQueryRequest.Builder deleteByQueryRequest = new DeleteByQueryRequest.Builder()
//                .index(Collections.singletonList(ALERT_INDEX_NAME))
//                .query(query);
//
//        osClient.delete(deleteByQueryRequest, e -> {
//            final String errorMessage = String.format("Could not delete all alerts for report with ID [%s]. " +
//                    "Maybe Optimize is not connected to OpenSearch?", reportId);
//            log.error(errorMessage, e);
//            return "There were errors while deleting alerts by the reports to OS." + e.getMessage();
//        });

    //todo will be handled in the OPT-7376
  }
}
