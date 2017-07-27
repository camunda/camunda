package org.camunda.optimize.service.importing.fetcher;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.util.EngineConstantsUtil.INCLUDE_PROCESS_INSTANCE_IDS;
import static org.camunda.optimize.service.util.EngineConstantsUtil.INCLUDE_PROCESS_INSTANCE_ID_IN;
import static org.camunda.optimize.service.util.EngineConstantsUtil.INCLUDE_VARIABLE_TYPE_IN;
import static org.camunda.optimize.service.util.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.EngineConstantsUtil.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_ORDER_TYPE_ASCENDING;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_ORDER_TYPE_DESCENDING;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_TYPE_ID;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_TYPE_VERSION_TAG;
import static org.camunda.optimize.service.util.VariableHelper.ALL_SUPPORTED_VARIABLE_TYPES;

@Component
public class EngineEntityFetcher {

  private Logger logger = LoggerFactory.getLogger(getClass());

  protected static final String UTF8 = "UTF-8";

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
          .acceptEncoding(UTF8)
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
    Set<String> supportedVariableTypes = new HashSet<>(Arrays.asList(ALL_SUPPORTED_VARIABLE_TYPES));
    pids.put(INCLUDE_VARIABLE_TYPE_IN, supportedVariableTypes);
    try {
      entries = client
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
          .queryParam("deserializeValues", "false")
          .path(configurationService.getHistoricVariableInstanceEndpoint())
          .request(MediaType.APPLICATION_JSON)
          .acceptEncoding(UTF8)
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

  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions(int indexOfFirstResult,
                                                                  int maxPageSize) {
    List<ProcessDefinitionEngineDto> entries;
    try {
      entries = client
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
          .path(configurationService.getProcessDefinitionEndpoint())
          .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
          .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
          .queryParam(SORT_BY, SORT_TYPE_ID)
          .queryParam(SORT_ORDER, SORT_ORDER_TYPE_DESCENDING)
          .request(MediaType.APPLICATION_JSON)
          .acceptEncoding(UTF8)
          .get(new GenericType<List<ProcessDefinitionEngineDto>>() {
          });
    } catch (RuntimeException e) {
      logError("Could not fetch process definitions from engine. Please check the connection!", e);
      entries = Collections.emptyList();
    }
    return entries;
  }
}
