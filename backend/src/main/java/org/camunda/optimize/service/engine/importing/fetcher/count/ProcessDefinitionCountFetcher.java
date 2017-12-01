package org.camunda.optimize.service.engine.importing.fetcher.count;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.AbstractEngineAwareFetcher;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.PROCESS_DEFINITION_ID_IN;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionCountFetcher extends AbstractEngineAwareFetcher {


  public ProcessDefinitionCountFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  public Long fetchProcessDefinitionCount(List<String> allProcessDefinitions) {
    long result = 0;

    for (String id : allProcessDefinitions) {
      CountDto count = getEngineClient()
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
          .path(configurationService.getProcessDefinitionCountEndpoint())
          .queryParam(PROCESS_DEFINITION_ID, id)
          .request()
          .get(CountDto.class);
      result = result + count.getCount();
    }

    return result;
  }

  public long fetchProcessDefinitionCount(String processDefinitionId) {
    List<String> listWrapped = new ArrayList<>();
    listWrapped.add(processDefinitionId);
    return fetchProcessDefinitionCount(listWrapped);
  }
}
