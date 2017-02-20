package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.springframework.stereotype.Component;

@Component
public class MissingActivityFinder extends MissingEntitiesFinder<HistoricActivityInstanceEngineDto> {

  @Override
  protected String elasticSearchType() {
    return configurationService.getEventType();
  }
}
