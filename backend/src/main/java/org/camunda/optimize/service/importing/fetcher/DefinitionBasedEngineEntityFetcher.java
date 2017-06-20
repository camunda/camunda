package org.camunda.optimize.service.importing.fetcher;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.EngineConstantsUtil.INCLUDE_ONLY_FINISHED_INSTANCES;
import static org.camunda.optimize.service.util.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.EngineConstantsUtil.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_ORDER_TYPE_ASCENDING;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_TYPE_END_TIME;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_TYPE_ID;
import static org.camunda.optimize.service.util.EngineConstantsUtil.TRUE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DefinitionBasedEngineEntityFetcher extends EngineEntityFetcher {

  private Logger logger = LoggerFactory.getLogger(DefinitionBasedEngineEntityFetcher.class);

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private Client client;

  public List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstances(int indexOfFirstResult,
                                                                                int maxPageSize,
                                                                                String processDefinitionId) {
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

  public Integer fetchHistoricActivityInstanceCount(List<String> processDefinitionIds) throws OptimizeException {
    int totalCount = 0;
    for (String processDefinitionId : processDefinitionIds) {
      try {
        CountDto newCount = client
            .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
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

  public List<ProcessDefinitionXmlEngineDto> fetchProcessDefinitionXml(int indexOfFirstResult,
                                                                       int maxPageSize,
                                                                       String processDefinitionId) {
    List<ProcessDefinitionEngineDto> procDefs = fetchProcessDefinitions(indexOfFirstResult, maxPageSize, processDefinitionId);
    List<ProcessDefinitionXmlEngineDto> xmls = new ArrayList<>();
    for (ProcessDefinitionEngineDto procDef : procDefs) {
      ProcessDefinitionXmlEngineDto xml;
      try {
        xml = client
            .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
            .path(configurationService.getProcessDefinitionXmlEndpoint(processDefinitionId))
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
                                                                  int maxPageSize,
                                                                  String processDefinitionId) {
    List<ProcessDefinitionEngineDto> entries;
    try {
      entries = client
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
          .path(configurationService.getProcessDefinitionEndpoint())
          .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
          .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
          .queryParam(SORT_BY, SORT_TYPE_ID)
          .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
          .queryParam(PROCESS_DEFINITION_ID, processDefinitionId)
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

  public Integer fetchProcessDefinitionCount(List<String> processDefinitionIds) throws OptimizeException {
    int totalCount = 0;
    for (String processDefinitionId : processDefinitionIds) {
      try {
        CountDto newCount = client
            .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
            .path(configurationService.getProcessDefinitionCountEndpoint())
            .queryParam(PROCESS_DEFINITION_ID, processDefinitionId)
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


}
