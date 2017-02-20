package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@Component
public class EngineEntityFetcher {

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private Client client;

  public List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstances(int indexOfFirstResult, int maxPageSize) {
    List<HistoricActivityInstanceEngineDto> entries = client
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
      .path(configurationService.getHistoricActivityInstanceEndpoint())
      .queryParam("sortBy", "startTime")
      .queryParam("sortOrder", "asc")
      .queryParam("firstResult", indexOfFirstResult)
      .queryParam("maxResults", maxPageSize)
      .request(MediaType.APPLICATION_JSON)
      .get(new GenericType<List<HistoricActivityInstanceEngineDto>>() {
      });

    return entries;
  }

  public List<ProcessDefinitionXmlEngineDto> fetchProcessDefinitionXmls(int indexOfFirstResult, int maxPageSize) {
    List<ProcessDefinitionEngineDto> entries = fetchProcessDefinitions(indexOfFirstResult, maxPageSize);
    return fetchAllXmls(entries);
  }

  private List<ProcessDefinitionXmlEngineDto> fetchAllXmls(List<ProcessDefinitionEngineDto> entries) {

    List<ProcessDefinitionXmlEngineDto> xmls = new ArrayList<>(entries.size());
    for (ProcessDefinitionEngineDto engineDto : entries) {
      ProcessDefinitionXmlEngineDto xml = client
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
        .path(configurationService.getProcessDefinitionXmlEndpoint(engineDto.getId()))
        .request(MediaType.APPLICATION_JSON)
        .get(new GenericType<ProcessDefinitionXmlEngineDto>() {
        });
      xmls.add(xml);
    }

    return xmls;
  }

  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions(int indexOfFirstResult, int maxPageSize) {
    List<ProcessDefinitionEngineDto> entries = client
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
      .path(configurationService.getProcessDefinitionEndpoint())
      .queryParam("firstResult", indexOfFirstResult)
      .queryParam("maxResults", maxPageSize)
      .request(MediaType.APPLICATION_JSON)
      .get(new GenericType<List<ProcessDefinitionEngineDto>>() {
      });

    return entries;
  }

}
