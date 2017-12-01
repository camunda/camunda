package org.camunda.optimize.service.engine.importing.fetcher.count;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.AbstractEngineAwareFetcher;
import org.elasticsearch.client.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;

import static org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher.UTF8;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VariableInstanceCountFetcher extends AbstractEngineAwareFetcher {

  public VariableInstanceCountFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  public Long fetchTotalProcessInstanceCountIfVariablesAreAvailable() {
    long totalCount = 0;

    CountDto newCount = getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(configurationService.getHistoricProcessInstanceCountEndpoint())
        .request()
        .acceptEncoding(UTF8)
        .get(CountDto.class);
    totalCount += newCount.getCount();

    long totalVariableInstances = fetchTotalVariableInstanceCount();
    if (totalVariableInstances == 0L) {
      totalCount = 0L;
    }

    return totalCount;
  }

  private long fetchTotalVariableInstanceCount() {
    long totalCount = 0;

    CountDto newCount = getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
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
