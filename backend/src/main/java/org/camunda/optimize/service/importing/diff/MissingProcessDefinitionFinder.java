package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.springframework.stereotype.Component;

@Component
public class MissingProcessDefinitionFinder extends MissingEntitiesFinder<ProcessDefinitionEngineDto> {

  @Override
  protected String elasticSearchType() {
    return configurationService.getProcessDefinitionType();
  }
}
