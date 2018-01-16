package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author Askar Akhmerov
 */
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

    AlertDefinitionDto result = alertDefinitionDto;
    String id = IdGenerator.getNextId();
    result.setId(id);
    Map map = objectMapper.convertValue(result, Map.class);

    logger.debug("Writing alert [{}] to elasticsearch", id);

    esclient
        .prepareIndex(
            configurationService.getOptimizeIndex(configurationService.getAlertType()),
            configurationService.getAlertType(),
            id
        )
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setSource(map)
        .get();
    return result;
  }

  public void updateAlert(AlertDefinitionDto toUpdate) {
    UpdateResponse updateResponse = null;
    try {
      updateResponse = esclient
          .prepareUpdate(
              configurationService.getOptimizeIndex(configurationService.getAlertType()),
              configurationService.getAlertType(),
              toUpdate.getId()
          )
          .setDoc(objectMapper.writeValueAsString(toUpdate), XContentType.JSON)
          .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
          .get();
    } catch (JsonProcessingException e) {
      logError(toUpdate);
    }

    if (updateResponse.getShardInfo().getFailed() > 0) {
      logError(toUpdate);
      throw new OptimizeRuntimeException("Was not able to store alert!");
    }
  }

  private void logError(AlertDefinitionDto toUpdate) {
    logger.error("Was not able to store alert with id [{}] and name [{}]. Exception: {} \n Stacktrace: {}",
        toUpdate.getId(),
        toUpdate.getName());
  }

  public void deleteAlert(String alertId) {
    logger.debug("Deleting alert with id [{}]", alertId);
    esclient.prepareDelete(
        configurationService.getOptimizeIndex(configurationService.getAlertType()),
        configurationService.getAlertType(),
        alertId
    )
    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
    .get();
  }
}
