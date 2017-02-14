package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@Component
public class MissingProcessDefinitionXmlFinder extends MissingEntriesFinder<ProcessDefinitionXmlEngineDto> {

  @Override
  protected List<ProcessDefinitionXmlEngineDto> queryEngineRestPoint() {
    List<ProcessDefinitionEngineDto> entries = client
      .target(configurationService.getEngineRestApiEndpoint() + configurationService.getEngineName())
      .path(configurationService.getProcessDefinitionEndpoint())
      .request(MediaType.APPLICATION_JSON)
      .get(new GenericType<List<ProcessDefinitionEngineDto>>() {
      });
    return retrieveAllXmls(entries);
  }

  private List<ProcessDefinitionXmlEngineDto> retrieveAllXmls(List<ProcessDefinitionEngineDto> entries) {

    List<ProcessDefinitionXmlEngineDto> xmls = new ArrayList<>(entries.size());
    for (ProcessDefinitionEngineDto engineDto : entries) {
      ProcessDefinitionXmlEngineDto xml = client
      .target(configurationService.getEngineRestApiEndpoint() + configurationService.getEngineName())
      .path(configurationService.getProcessDefinitionXmlEndpoint(engineDto.getId()))
      .request(MediaType.APPLICATION_JSON)
      .get(new GenericType<ProcessDefinitionXmlEngineDto>() {
      });
      xmls.add(xml);
    }

    return xmls;
  }

  @Override
  protected String elasticSearchType() {
    return configurationService.getProcessDefinitionXmlType();
  }
}
