package org.camunda.optimize.service.engine.importing.fetcher.count;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.service.engine.importing.fetcher.AbstractEngineAwareFetcher;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionCountFetcher extends AbstractEngineAwareFetcher {


  public ProcessDefinitionCountFetcher(String engineAlias) {
    super(engineAlias);
  }

  public Long fetchProcessDefinitionCount() {
    long result = 0;

    CountDto count = getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(configurationService.getProcessDefinitionCountEndpoint())
        .request()
        .get(CountDto.class);
    result = count.getCount();

    return result;
  }

}
