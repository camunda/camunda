package org.camunda.optimize.service.engine.importing.fetcher.count;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;

import static org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher.UTF8;

@Component
public class VariableInstanceCountFetcher {

  @Autowired
  private Client engineClient;

  @Autowired
  private ConfigurationService configurationService;

  public Long fetchVariableInstanceCount() {
    long totalCount = 0;
    CountDto newCount = engineClient
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine("1"))
      .queryParam("deserializeValues", "false")
      .path(configurationService.getHistoricVariableInstanceCountEndpoint())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get()
      .readEntity(CountDto.class);
    totalCount += newCount.getCount();
    return totalCount;
  }
}
