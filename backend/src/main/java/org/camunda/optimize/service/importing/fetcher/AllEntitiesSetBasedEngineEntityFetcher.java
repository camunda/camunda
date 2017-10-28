package org.camunda.optimize.service.importing.fetcher;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.util.VariableHelper.ALL_SUPPORTED_VARIABLE_TYPES;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_PROCESS_INSTANCE_IDS;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_PROCESS_INSTANCE_ID_IN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_VARIABLE_TYPE_IN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER_TYPE_DESCENDING;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_TYPE_ID;

@Component
public class AllEntitiesSetBasedEngineEntityFetcher extends AbstractEntityFetcher {

  private Logger logger = LoggerFactory.getLogger(AllEntitiesSetBasedEngineEntityFetcher.class);

  @Autowired
  private ConfigurationService configurationService;


  public Integer fetchProcessDefinitionCount(String engineAlias) throws OptimizeException {
    CountDto count;
    try {
      count = getEngineClient(engineAlias)
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
        .path(configurationService.getProcessDefinitionCountEndpoint())
        .request()
        .get(CountDto.class);
    } catch (RuntimeException e) {
      throw new OptimizeException("Could not fetch process definition count from engine. Please check the connection!", e);
    }
    return count.getCount();
  }

  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions(int indexOfFirstResult,
                                                                  int maxPageSize,
                                                                  String engineAlias) {
    List<ProcessDefinitionEngineDto> entries;
    try {
      entries = getEngineClient(engineAlias)
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
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

  public List<HistoricProcessInstanceDto> fetchHistoricProcessInstances(Set<String> processInstanceIds, String engineAlias) throws OptimizeException {
    List<HistoricProcessInstanceDto> entries;
    long requestStart = System.currentTimeMillis();
    Map<String, Set<String>> pids = new HashMap<>();
    pids.put(INCLUDE_PROCESS_INSTANCE_IDS, processInstanceIds);
    try {
      entries = getEngineClient(engineAlias)
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
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

  public List<HistoricVariableInstanceDto> fetchHistoricVariableInstances(Set<String> processInstanceIds, String engineAlias) throws OptimizeException {
    List<HistoricVariableInstanceDto> entries;
    long requestStart = System.currentTimeMillis();
    Map<String, Set<String>> pids = new HashMap<>();
    pids.put(INCLUDE_PROCESS_INSTANCE_ID_IN, processInstanceIds);
    Set<String> supportedVariableTypes = new HashSet<>(Arrays.asList(ALL_SUPPORTED_VARIABLE_TYPES));
    pids.put(INCLUDE_VARIABLE_TYPE_IN, supportedVariableTypes);
    try {
      entries = getEngineClient(engineAlias)
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
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

  public Integer fetchHistoricVariableInstancesCount(String engineAlias) throws OptimizeException {
    Integer result = null;
    long requestStart = System.currentTimeMillis();
    try {
      CountDto count = getEngineClient(engineAlias)
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
          .queryParam("deserializeValues", "false")
          .path(configurationService.getHistoricVariableInstanceCountEndpoint())
          .request(MediaType.APPLICATION_JSON)
          .acceptEncoding(UTF8)
          .get()
          .readEntity(CountDto.class);
      long requestEnd = System.currentTimeMillis();
      logger.debug("Fetch of [HVI] count took [{}] ms", requestEnd - requestStart);

      result = count.getCount();
    } catch (RuntimeException e) {
      logError("Could not fetch historic variable instances from engine. Please check the connection!", e);
      throw new OptimizeException();
    }
    return result;
  }

  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions(int indexOfFirstResult,
                                                                  String engineAlias) {
    List<ProcessDefinitionEngineDto> entries;
    try {
      entries = getEngineClient(engineAlias)
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
        .path(configurationService.getProcessDefinitionEndpoint())
        .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
        .queryParam(MAX_RESULTS_TO_RETURN, configurationService.getEngineImportProcessDefinitionMaxPageSize())
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

  public List<ProcessDefinitionXmlEngineDto> fetchProcessDefinitionXmls(int indexOfFirstResult, String engineAlias) {
    List<ProcessDefinitionEngineDto> entries =
      fetchProcessDefinitions(indexOfFirstResult,
        configurationService.getEngineImportProcessDefinitionXmlMaxPageSize(),
        engineAlias)
      ;
    return fetchAllXmls(entries, engineAlias);
  }

  private List<ProcessDefinitionXmlEngineDto> fetchAllXmls(List<ProcessDefinitionEngineDto> entries, String engineAlias) {
    List<ProcessDefinitionXmlEngineDto> xmls;
    try {
      xmls = new ArrayList<>(entries.size());
      for (ProcessDefinitionEngineDto engineDto : entries) {
        ProcessDefinitionXmlEngineDto xml = getEngineClient(engineAlias)
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
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


}
