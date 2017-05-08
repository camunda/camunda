package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.util.EngineConstantsUtil.INCLUDE_ONLY_FINISHED_INSTANCES;
import static org.camunda.optimize.service.util.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_ORDER_TYPE_ASCENDING;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_TYPE_END_TIME;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_TYPE_ID;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_TYPE_INSTANCE_ID;
import static org.camunda.optimize.service.util.EngineConstantsUtil.TRUE;

@Component
public class EngineEntityFetcher {

  private Logger logger = LoggerFactory.getLogger(EngineEntityFetcher.class);

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private Client client;

  public List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstances(int indexOfFirstResult, int maxPageSize) {
    List<HistoricActivityInstanceEngineDto> entries;
    long requestStart = System.currentTimeMillis();
    try {
      entries = client
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
        .path(configurationService.getHistoricActivityInstanceEndpoint())
        .queryParam(SORT_BY, SORT_TYPE_END_TIME)
        .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
        .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
        .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
        .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
        .request(MediaType.APPLICATION_JSON)
        .get(new GenericType<List<HistoricActivityInstanceEngineDto>>() {
        });
      long requestEnd = System.currentTimeMillis();
      logger.debug("Fetch of [HAI] took [{}] ms", requestEnd - requestStart);
    } catch (RuntimeException e) {
      logError("Could not fetch historic activity instances from engine. Please check the connection!", e);
      entries = Collections.emptyList();
    }

    return entries;
  }

  public Integer fetchHistoricActivityInstanceCount() throws OptimizeException {
    CountDto count;
    try {
      count = client
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
        .path(configurationService.getHistoricActivityInstanceCountEndpoint())
        .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
        .request()
        .get(CountDto.class);
    } catch (RuntimeException e) {
      throw new OptimizeException("Could not fetch historic activity instance count from engine. Please check the connection!", e);
    }

    return count.getCount();
  }

  public List<ProcessDefinitionXmlEngineDto> fetchProcessDefinitionXmls(int indexOfFirstResult, int maxPageSize) {
    List<ProcessDefinitionEngineDto> entries = fetchProcessDefinitions(indexOfFirstResult, maxPageSize);
    return fetchAllXmls(entries);
  }

  private List<ProcessDefinitionXmlEngineDto> fetchAllXmls(List<ProcessDefinitionEngineDto> entries) {
    List<ProcessDefinitionXmlEngineDto> xmls;
    try {
      xmls = new ArrayList<>(entries.size());
      for (ProcessDefinitionEngineDto engineDto : entries) {
        ProcessDefinitionXmlEngineDto xml = client
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
          .path(configurationService.getProcessDefinitionXmlEndpoint(engineDto.getId()))
          .request(MediaType.APPLICATION_JSON)
          .get(ProcessDefinitionXmlEngineDto.class);
        xmls.add(xml);
      }
    } catch (RuntimeException e) {
      logError("Could not fetch process definition xmls from engine. Please check the connection!", e);
      xmls = Collections.emptyList();
    }

    return xmls;
  }

  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions(int indexOfFirstResult, int maxPageSize) {
    List<ProcessDefinitionEngineDto> entries;
    try {
      entries = client
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
        .path(configurationService.getProcessDefinitionEndpoint())
        .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
        .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
        .queryParam(SORT_BY, SORT_TYPE_ID)
        .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
        .request(MediaType.APPLICATION_JSON)
        .get(new GenericType<List<ProcessDefinitionEngineDto>>() {
        });
    } catch (RuntimeException e) {
      logError("Could not fetch process definitions from engine. Please check the connection!", e);
      entries = Collections.emptyList();
    }

    return entries;
  }

  public Integer fetchProcessDefinitionCount() throws OptimizeException {
    CountDto count;
    try {
      count = client
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
        .path(configurationService.getProcessDefinitionCountEndpoint())
        .request()
        .get(CountDto.class);
    } catch (RuntimeException e) {
      throw new OptimizeException("Could not fetch process definition count from engine. Please check the connection!", e);
    }

    return count.getCount();
  }

  public List<HistoricProcessInstanceDto> fetchHistoricProcessInstances(int indexOfFirstResult, int maxPageSize) throws OptimizeException {
    List<HistoricProcessInstanceDto> entries;
    long requestStart = System.currentTimeMillis();
    try {
      entries = client
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
        .path(configurationService.getHistoricProcessInstanceEndpoint())
        .queryParam(SORT_BY, SORT_TYPE_END_TIME)
        .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
        .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
        .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
        .request(MediaType.APPLICATION_JSON)
        .get(new GenericType<List<HistoricProcessInstanceDto>>() {
        });
      long requestEnd = System.currentTimeMillis();
      logger.debug("Fetch of [HPI] took [{}] ms", requestEnd - requestStart);
    } catch (RuntimeException e) {
      logError("Could not fetch historic process instances from engine. Please check the connection!", e);
      throw new OptimizeException();
    }
    return entries;
  }

  public Integer fetchHistoricProcessInstanceCount() throws OptimizeException {
    CountDto count;
    try {
      count = client
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
        .path(configurationService.getHistoricProcessInstanceCountEndpoint())
        .request()
        .get(CountDto.class);
    } catch (RuntimeException e) {
      throw new OptimizeException("Could not fetch process instance count from engine. Please check the connection!", e);
    }

    return count.getCount();
  }

  public List<HistoricVariableInstanceDto> fetchHistoricVariableInstances(int indexOfFirstResult, int maxPageSize) {
    List<HistoricVariableInstanceDto> entries;
    try {
      entries = client
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
        .path(configurationService.getHistoricVariableInstanceEndpoint())
        .queryParam(SORT_BY, SORT_TYPE_INSTANCE_ID)
        .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
        .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
        .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
        .queryParam("deserializeValues", "false")
        .request(MediaType.APPLICATION_JSON)
        .get(new GenericType<List<HistoricVariableInstanceDto>>() {
        });
    } catch (RuntimeException e) {
      logError("Could not fetch historic activity instances from engine. Please check the connection!", e);
      entries = Collections.emptyList();
    }

    return entries;
  }

  public Integer fetchHistoricVariableInstanceCount() throws OptimizeException {
    CountDto count;
    try {
      count = client
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
        .path(configurationService.getHistoricVariableInstanceCountEndpoint())
        .request()
        .get(CountDto.class);
    } catch (RuntimeException e) {
      throw new OptimizeException("Could not fetch variable count from engine. Please check the connection!", e);
    }

    return count.getCount();
  }

  private void logError(String message, Exception e) {
    if (logger.isDebugEnabled()) {
      logger.error(message, e);
    } else {
      logger.error(message);
    }
  }

}
