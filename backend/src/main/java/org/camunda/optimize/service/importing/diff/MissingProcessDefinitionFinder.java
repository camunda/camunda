package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Component
public class MissingProcessDefinitionFinder extends MissingEntriesFinder<ProcessDefinitionEngineDto> {

  @Override
  protected List<ProcessDefinitionEngineDto> queryEngineRestPoint() {
    List<ProcessDefinitionEngineDto> entries = client
      .target(configurationService.getEngineRestApiEndpoint() + configurationService.getEngineName())
      .path(configurationService. getProcessDefinitionEndpoint())
      .request(MediaType.APPLICATION_JSON)
      .get(new GenericType<List<ProcessDefinitionEngineDto>>() {
      });

    return entries;
  }

  @Override
  protected String elasticSearchType() {
    return configurationService.getProcessDefinitionType();
  }
}
