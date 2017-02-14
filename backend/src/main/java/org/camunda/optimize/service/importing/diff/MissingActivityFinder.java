package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Component
public class MissingActivityFinder extends MissingEntriesFinder<HistoricActivityInstanceEngineDto> {

  @Override
  protected List<HistoricActivityInstanceEngineDto> queryEngineRestPoint() {
    List<HistoricActivityInstanceEngineDto> entries = client
      .target(configurationService.getEngineRestApiEndpoint() + configurationService.getEngineName())
      .path(configurationService.getHistoricActivityInstanceEndpoint())
      .request(MediaType.APPLICATION_JSON)
      .get(new GenericType<List<HistoricActivityInstanceEngineDto>>() {
      });

    return entries;
  }

  @Override
  protected String elasticSearchType() {
    return configurationService.getEventType();
  }
}
