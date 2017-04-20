package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.engine.HistoricVariableInstanceDto;
import org.springframework.stereotype.Component;

@Component
public class MissingVariablesFinder extends MissingEntitiesFinder<HistoricVariableInstanceDto> {

  @Override
  protected String elasticSearchType() {
    return configurationService.getVariableType();
  }
}
