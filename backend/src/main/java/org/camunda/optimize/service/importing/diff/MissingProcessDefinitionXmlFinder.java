package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.springframework.stereotype.Component;

@Component
public class MissingProcessDefinitionXmlFinder extends MissingEntitiesFinder<ProcessDefinitionXmlEngineDto> {

  @Override
  protected String elasticSearchType() {
    return configurationService.getProcessDefinitionXmlType();
  }
}
