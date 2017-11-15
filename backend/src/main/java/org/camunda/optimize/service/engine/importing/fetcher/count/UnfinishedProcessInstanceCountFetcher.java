package org.camunda.optimize.service.engine.importing.fetcher.count;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;

import static org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher.UTF8;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_ONLY_UNFINISHED_INSTANCES;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.TRUE;

@Component
public class UnfinishedProcessInstanceCountFetcher {

  @Autowired
  private Client engineClient;

  @Autowired
  private ConfigurationService configurationService;

  public Long fetchUnfinishedHistoricProcessInstanceCount() {
    long totalCount = 0;
    CountDto newCount = engineClient
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getHistoricProcessInstanceCountEndpoint())
      .queryParam(INCLUDE_ONLY_UNFINISHED_INSTANCES, TRUE)
      .request()
      .acceptEncoding(UTF8)
      .get(CountDto.class);
    totalCount += newCount.getCount();
    return totalCount;
  }

  private String getEngineAlias() {
    return configurationService.getConfiguredEngines().keySet().iterator().next();
  }
}
