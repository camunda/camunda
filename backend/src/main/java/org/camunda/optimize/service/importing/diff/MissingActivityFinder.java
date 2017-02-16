package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Component
public class MissingActivityFinder extends MissingEntriesFinder<HistoricActivityInstanceEngineDto> {

  @Override
  protected List<HistoricActivityInstanceEngineDto> queryEngineRestPoint(int indexOfFirstResult, int maxPageSize) {
    List<HistoricActivityInstanceEngineDto> entries = client
      .target(configurationService.getEngineRestApiEndpoint() + configurationService.getEngineName())
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

  @Override
  protected String elasticSearchType() {
    return configurationService.getEventType();
  }
}
