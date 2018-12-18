package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.es.schema.type.AlertType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CREATE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DELETE_SUCCESSFUL_RESPONSE_RESULT;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;


@Component
public class AlertWriter {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public AlertDefinitionDto createAlert(AlertDefinitionDto alertDefinitionDto) {
    logger.debug("Writing new alert to Elasticsearch");

    String id = IdGenerator.getNextId();
    alertDefinitionDto.setId(id);
    try {
      IndexResponse indexResponse = esclient
        .prepareIndex(
          getOptimizeIndexAliasForType(ElasticsearchConstants.ALERT_TYPE),
          ElasticsearchConstants.ALERT_TYPE,
          id
        )
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setSource(objectMapper.writeValueAsString(alertDefinitionDto), XContentType.JSON)
        .get();

      if (!indexResponse.getResult().getLowercase().equals(CREATE_SUCCESSFUL_RESPONSE_RESULT)) {
        String message = "Could not write alert to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (JsonProcessingException e) {
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
      UpdateResponse updateResponse = esclient
        .prepareUpdate(
          getOptimizeIndexAliasForType(ElasticsearchConstants.ALERT_TYPE),
          ElasticsearchConstants.ALERT_TYPE,
          alertUpdate.getId()
        )
        .setDoc(objectMapper.writeValueAsString(alertUpdate), XContentType.JSON)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
        .get();

      if (updateResponse.getShardInfo().getFailed() > 0) {
        String errorMessage = String.format(
          "Was not able to update alert with id [%s] and name [%s]. " +
            "Error during the update in Elasticsearch.", alertUpdate.getId(), alertUpdate.getName());
        logger.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
      }
    } catch (JsonProcessingException e) {
      String errorMessage = String.format(
        "Was not able to update alert with id [%s] and name [%s]. Could not serialize alert update!",
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
    DeleteResponse deleteResponse = esclient.prepareDelete(
      getOptimizeIndexAliasForType(ElasticsearchConstants.ALERT_TYPE),
      ElasticsearchConstants.ALERT_TYPE,
      alertId
    )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .get();

    if (!deleteResponse.getResult().getLowercase().equals(DELETE_SUCCESSFUL_RESPONSE_RESULT)) {
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
      esclient
        .prepareUpdate(
          getOptimizeIndexAliasForType(ElasticsearchConstants.ALERT_TYPE),
          ElasticsearchConstants.ALERT_TYPE,
          alertId
        )
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
        .setDoc(
          jsonBuilder()
            .startObject()
            .field(AlertType.TRIGGERED, alertStatus)
            .endObject()
        )
        .get();
    } catch (Exception e) {
      logger.error("can't update status of alert [{}]", alertId, e);
    }
  }

  /**
   * Delete all alerts that are associated with following report ID
   */
  public void deleteAlertsForReport(String reportId) {
    logger.debug("Deleting all alerts for report with id [{}]", reportId);
    BulkByScrollResponse bulkByScrollResponse = DeleteByQueryAction.INSTANCE.newRequestBuilder(esclient)
      .filter(QueryBuilders.matchQuery(AlertType.REPORT_ID, reportId))
      .source(getOptimizeIndexAliasForType(ElasticsearchConstants.ALERT_TYPE))
      .refresh(true)
      .get();

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
