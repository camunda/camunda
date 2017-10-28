package org.camunda.optimize.service.importing.fetcher;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EngineConstantsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_ONLY_FINISHED_INSTANCES;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER_TYPE_ASCENDING;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_TYPE_END_TIME;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_TYPE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.TRUE;

@Component
public class DefinitionBasedEngineEntityFetcher extends AbstractEntityFetcher {

  private Logger logger = LoggerFactory.getLogger(DefinitionBasedEngineEntityFetcher.class);

  @Autowired
  private ConfigurationService configurationService;

  public List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstances(int indexOfFirstResult,
                                                                                String processDefinitionId,
                                                                                String engineAlias) {
    List<HistoricActivityInstanceEngineDto> entries;
    long requestStart = System.currentTimeMillis();
    try {
      entries = getEngineClient(engineAlias)
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
          .path(configurationService.getHistoricActivityInstanceEndpoint())
          .queryParam(SORT_BY, SORT_TYPE_END_TIME)
          .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
          .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
          .queryParam(MAX_RESULTS_TO_RETURN, configurationService.getEngineImportActivityInstanceMaxPageSize())
          .queryParam(PROCESS_DEFINITION_ID, processDefinitionId)
          .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
          .request(MediaType.APPLICATION_JSON)
          .acceptEncoding(UTF8)
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

  public Integer fetchHistoricActivityInstanceCount(List<String> processDefinitionIds, String engineAlias) throws OptimizeException {
    int totalCount = 0;
    for (String processDefinitionId : processDefinitionIds) {
      try {
        CountDto newCount = getEngineClient(engineAlias)
            .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
            .path(configurationService.getHistoricActivityInstanceCountEndpoint())
            .queryParam(PROCESS_DEFINITION_ID, processDefinitionId)
            .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
            .request()
            .acceptEncoding(UTF8)
            .get(CountDto.class);
        totalCount += newCount.getCount();
      } catch (RuntimeException e) {
        throw new OptimizeException("Could not fetch historic activity instance count from engine. Please check the connection!", e);
      }
    }
    return totalCount;
  }

  public List<HistoricProcessInstanceDto> fetchHistoricFinishedProcessInstances(int indexOfFirstResult,
                                                                                int maxPageSize,
                                                                                String processDefinitionId,
                                                                                String engineAlias) {
    List<HistoricProcessInstanceDto> entries;
    long requestStart = System.currentTimeMillis();
    try {
      entries = getEngineClient(engineAlias)
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
          .path(configurationService.getHistoricProcessInstanceEndpoint())
          .queryParam(SORT_BY, SORT_TYPE_END_TIME)
          .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
          .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
          .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
          .queryParam(PROCESS_DEFINITION_ID, processDefinitionId)
          .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
          .request(MediaType.APPLICATION_JSON)
          .acceptEncoding(UTF8)
          .get(new GenericType<List<HistoricProcessInstanceDto>>() {
          });
      long requestEnd = System.currentTimeMillis();
      logger.debug("Fetch of [HPI] took [{}] ms", requestEnd - requestStart);
    } catch (RuntimeException e) {
      logError("Could not fetch historic process instances from engine. Please check the connection!", e);
      entries = Collections.emptyList();
    }

    return entries;
  }

  public Integer fetchHistoricProcessInstanceCount(List<String> processDefinitionIds, String engineAlias) throws OptimizeException {
    int totalCount = 0;
    for (String processDefinitionId : processDefinitionIds) {
      try {
        CountDto newCount = getEngineClient(engineAlias)
            .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
            .path(configurationService.getHistoricProcessInstanceCountEndpoint())
            .queryParam(PROCESS_DEFINITION_ID, processDefinitionId)
            .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
            .request()
            .acceptEncoding(UTF8)
            .get(CountDto.class);
        totalCount += newCount.getCount();
      } catch (RuntimeException e) {
        throw new OptimizeException("Could not fetch historic process instance count from engine. Please check the connection!", e);
      }
    }
    return totalCount;
  }

  public List<ProcessDefinitionXmlEngineDto> fetchProcessDefinitionXmls(int indexOfFirstResult,
                                                                        String processDefinitionId,
                                                                        String engineAlias) {
    List<ProcessDefinitionEngineDto> procDefs =
      fetchProcessDefinitions(indexOfFirstResult,
        configurationService.getEngineImportProcessDefinitionXmlMaxPageSize(),
        processDefinitionId,
        engineAlias);
    List<ProcessDefinitionXmlEngineDto> xmls = new ArrayList<>();
    for (ProcessDefinitionEngineDto procDef : procDefs) {
      ProcessDefinitionXmlEngineDto xml;
      try {
        xml = getEngineClient(engineAlias)
            .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
            .path(configurationService.getProcessDefinitionXmlEndpoint(procDef.getId()))
            .request(MediaType.APPLICATION_JSON)
            .acceptEncoding(UTF8)
            .get(ProcessDefinitionXmlEngineDto.class);
        xmls.add(xml);
      } catch (RuntimeException e) {
        logError("Could not fetch process definition xmls from engine. Please check the connection!", e);
        xml = null;
      }
    }
    return xmls;
  }

  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions(int indexOfFirstResult,
                                                                  String processDefinitionId,
                                                                  String engineAlias) {
    return fetchProcessDefinitions(
      indexOfFirstResult,
      configurationService.getEngineImportProcessDefinitionMaxPageSize(),
      processDefinitionId,
      engineAlias
    );
  }

  private List<ProcessDefinitionEngineDto> fetchProcessDefinitions(int indexOfFirstResult,
                                                                  int maxPageSize,
                                                                  String processDefinitionId,
                                                                  String engineAlias) {
    List<ProcessDefinitionEngineDto> entries;
    try {
      entries = getEngineClient(engineAlias)
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
          .path(configurationService.getProcessDefinitionEndpoint())
          .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
          .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
          .queryParam(SORT_BY, SORT_TYPE_ID)
          .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
          .queryParam(EngineConstantsUtil.PROCESS_DEFINITION_ID, processDefinitionId)
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

  public Integer fetchProcessDefinitionCount(List<String> processDefinitionIds, String engineAlias) throws OptimizeException {
    int totalCount = 0;
    for (String processDefinitionId : processDefinitionIds) {
      try {
        CountDto newCount = getEngineClient(engineAlias)
            .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineAlias))
            .path(configurationService.getProcessDefinitionCountEndpoint())
            .queryParam(EngineConstantsUtil.PROCESS_DEFINITION_ID, processDefinitionId)
            .request()
            .acceptEncoding(UTF8)
            .get(CountDto.class);
        totalCount += newCount.getCount();
      } catch (RuntimeException e) {
        throw new OptimizeException("Could not fetch process definition count from engine. Please check the connection!", e);
      }
    }
    return totalCount;
  }

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


}
