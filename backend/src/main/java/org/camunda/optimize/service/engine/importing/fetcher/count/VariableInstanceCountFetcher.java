package org.camunda.optimize.service.engine.importing.fetcher.count;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.service.engine.importing.fetcher.AbstractEngineAwareFetcher;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;

import static org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher.UTF8;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VariableInstanceCountFetcher extends AbstractEngineAwareFetcher {


  public VariableInstanceCountFetcher(String engineAlias) {
    super(engineAlias);
  }

  public Long fetchVariableInstanceCount() {
    long totalCount = 0;
    try {
      CountDto newCount = getEngineClient()
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
          .queryParam("deserializeValues", "false")
          .path(configurationService.getHistoricVariableInstanceCountEndpoint())
          .request(MediaType.APPLICATION_JSON)
          .acceptEncoding(UTF8)
          .get()
          .readEntity(CountDto.class);
      totalCount += newCount.getCount();
    } catch (Exception e) {
      logger.error("cant fetch entity count from [{}]", engineAlias, e);
    }
    return totalCount;
  }

}
