package org.camunda.optimize.service.engine.importing.fetcher.count;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;

@Component
public class ProcessDefinitionCountFetcher {

  @Autowired
  private Client engineClient;

  @Autowired
  private ConfigurationService configurationService;

  public Long fetchProcessDefinitionCount() {
    CountDto count = engineClient
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getProcessDefinitionCountEndpoint())
      .request()
      .get(CountDto.class);
    return count.getCount();
  }

  private String getEngineAlias() {
    return configurationService.getConfiguredEngines().keySet().iterator().next();
  }
}
