package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.springframework.stereotype.Component;

@Component
public class MissingProcessInstanceFinder extends MissingEntitiesFinder<HistoricProcessInstanceDto> {

  @Override
  protected String elasticSearchType() {
    return configurationService.getProcessInstanceType();
  }
}
