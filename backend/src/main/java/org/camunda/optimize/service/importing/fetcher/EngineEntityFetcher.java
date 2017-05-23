package org.camunda.optimize.service.importing.fetcher;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.util.EngineConstantsUtil.INCLUDE_PROCESS_INSTANCE_IDS;
import static org.camunda.optimize.service.util.EngineConstantsUtil.INCLUDE_PROCESS_INSTANCE_ID_IN;

@Component
public class EngineEntityFetcher {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private Client client;

  public List<HistoricProcessInstanceDto> fetchHistoricProcessInstances(Set<String> processInstanceIds) throws OptimizeException {
    List<HistoricProcessInstanceDto> entries;
    long requestStart = System.currentTimeMillis();
    Map<String, Set<String>> pids = new HashMap<>();
    pids.put(INCLUDE_PROCESS_INSTANCE_IDS, processInstanceIds);
    try {
      entries = client
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
          .path(configurationService.getHistoricProcessInstanceEndpoint())
          .request(MediaType.APPLICATION_JSON)
          .post(Entity.entity(pids, MediaType.APPLICATION_JSON))
          .readEntity(new GenericType<List<HistoricProcessInstanceDto>>() {
          });
      long requestEnd = System.currentTimeMillis();
      logger.debug("Fetch of [HPI] took [{}] ms", requestEnd - requestStart);
    } catch (RuntimeException e) {
      logError("Could not fetch historic process instances from engine. Please check the connection!", e);
      throw new OptimizeException();
    }
    return entries;
  }

  public List<HistoricVariableInstanceDto> fetchHistoricVariableInstances(Set<String> processInstanceIds) throws OptimizeException {
    List<HistoricVariableInstanceDto> entries;
    long requestStart = System.currentTimeMillis();
    Map<String, Set<String>> pids = new HashMap<>();
    pids.put(INCLUDE_PROCESS_INSTANCE_ID_IN, processInstanceIds);
    try {
      entries = client
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
          .queryParam("deserializeValues", "false")
          .path(configurationService.getHistoricVariableInstanceEndpoint())
          .request(MediaType.APPLICATION_JSON)
          .post(Entity.entity(pids, MediaType.APPLICATION_JSON))
          .readEntity(new GenericType<List<HistoricVariableInstanceDto>>() {
          });
      long requestEnd = System.currentTimeMillis();
      logger.debug("Fetch of [HVI] took [{}] ms", requestEnd - requestStart);
    } catch (RuntimeException e) {
      logError("Could not fetch historic variable instances from engine. Please check the connection!", e);
      throw new OptimizeException();
    }
    return entries;
  }

  protected void logError(String message, Exception e) {
    if (logger.isDebugEnabled()) {
      logger.error(message, e);
    } else {
      logger.error(message);
    }
  }

}
