package org.camunda.optimize.service.engine.importing.fetcher.count;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.dto.optimize.importing.DefinitionImportInformation;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.AbstractEngineAwareFetcher;
import org.camunda.optimize.service.engine.importing.fetcher.count.cache.InstanceCountCache;
import org.camunda.optimize.service.engine.importing.index.ProcessDefinitionManager;
import org.camunda.optimize.service.util.configuration.EngineConstantsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher.UTF8;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_ONLY_FINISHED_INSTANCES;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.TRUE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinishedProcessInstanceCountFetcher extends AbstractEngineAwareFetcher {

  @Autowired
  private ProcessDefinitionManager processDefinitionManager;

  public FinishedProcessInstanceCountFetcher(EngineContext engineContext) {
    super(engineContext);
  }

   private long fetchFinishedHistoricProcessInstanceCount(List<String> processDefinitionIds) {
    long totalCount = 0;
    for (String processDefinitionId : processDefinitionIds) {
      totalCount += fetchCountForDefinition(processDefinitionId);
    }
    return totalCount;
  }

  public Long fetchCountForDefinition(String processDefinitionId) {
    CountDto newCount = getEngineClient()
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
          .path(configurationService.getHistoricProcessInstanceCountEndpoint())
          .queryParam(EngineConstantsUtil.PROCESS_DEFINITION_ID, processDefinitionId)
          .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
          .request()
          .acceptEncoding(UTF8)
          .get(CountDto.class);
    cache.addCount(processDefinitionId, newCount.getCount());
    return newCount.getCount();
  }

  public long fetchAllFinishedHistoricProcessInstanceCount() {
    if(cache.getEntrySize() < processDefinitionManager.getAvailableProcessDefinitionCount(engineContext)) {
      List<String> definitionIds =
        processDefinitionManager.getAvailableProcessDefinitions(engineContext)
          .stream()
          .map(DefinitionImportInformation::getProcessDefinitionId)
          .collect(Collectors.toList());
      fetchFinishedHistoricProcessInstanceCount(definitionIds);
    }
    return cache.getTotalCount();
  }
}
