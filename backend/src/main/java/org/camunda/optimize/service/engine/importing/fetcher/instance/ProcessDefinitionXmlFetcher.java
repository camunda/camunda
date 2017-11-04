package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessDefinitionXmlFetcher
  extends RetryBackoffEngineEntityFetcher<ProcessDefinitionXmlEngineDto, AllEntitiesBasedImportPage> {

  @Autowired
  private ProcessDefinitionFetcher processDefinitionFetcher;

  @Override
  public List<ProcessDefinitionXmlEngineDto> fetchEntities(AllEntitiesBasedImportPage page) {
    return fetchProcessDefinitionXmls(page);
  }

  public List<ProcessDefinitionXmlEngineDto> fetchProcessDefinitionXmls(AllEntitiesBasedImportPage importIndex) {
    List<ProcessDefinitionEngineDto> entries =
      processDefinitionFetcher.fetchEngineEntities(importIndex);
    return fetchAllXmls(entries);
  }

  private List<ProcessDefinitionXmlEngineDto> fetchAllXmls(List<ProcessDefinitionEngineDto> entries) {
    List<ProcessDefinitionXmlEngineDto> xmls = new ArrayList<>(entries.size());
    long requestStart = System.currentTimeMillis();
    for (ProcessDefinitionEngineDto engineDto : entries) {
      ProcessDefinitionXmlEngineDto xml = client
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine("1"))
        .path(configurationService.getProcessDefinitionXmlEndpoint(engineDto.getId()))
        .request(MediaType.APPLICATION_JSON)
        .get(ProcessDefinitionXmlEngineDto.class);
      xmls.add(xml);
    }
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] process definition xmls within [{}] ms",
      entries.size(),
      requestEnd - requestStart
    );


    return xmls;
  }
}
