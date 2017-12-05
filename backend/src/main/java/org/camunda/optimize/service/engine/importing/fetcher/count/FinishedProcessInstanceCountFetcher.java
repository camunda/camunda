package org.camunda.optimize.service.engine.importing.fetcher.count;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.AbstractEngineAwareFetcher;
import org.camunda.optimize.service.util.configuration.EngineConstantsUtil;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher.UTF8;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_ONLY_FINISHED_INSTANCES;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.TRUE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinishedProcessInstanceCountFetcher extends AbstractEngineAwareFetcher {

  public FinishedProcessInstanceCountFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  public Long fetchFinishedHistoricProcessInstanceCount(List<String> processDefinitionIds) {
    long totalCount = 0;

    for (String processDefinitionId : processDefinitionIds) {
      CountDto newCount = getEngineClient()
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
          .path(configurationService.getHistoricProcessInstanceCountEndpoint())
          .queryParam(EngineConstantsUtil.PROCESS_DEFINITION_ID, processDefinitionId)
          .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
          .request()
          .acceptEncoding(UTF8)
          .get(CountDto.class);
      totalCount += newCount.getCount();
    }


    return totalCount;
  }

  public long fetchAllFinishedHistoricProcessInstanceCount() {
    CountDto newCount = getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(configurationService.getHistoricProcessInstanceCountEndpoint())
        .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
        .request()
        .acceptEncoding(UTF8)
        .get(CountDto.class);

    return newCount.getCount();
  }
}
