package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_ONLY_FINISHED_INSTANCES;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER_TYPE_ASCENDING;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_TYPE_END_TIME;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.TRUE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinishedProcessInstanceFetcher extends
  RetryBackoffEngineEntityFetcher<HistoricProcessInstanceDto, DefinitionBasedImportPage> {

  public FinishedProcessInstanceFetcher(String engineAlias) {
    super(engineAlias);
  }

  @Override
  public List<HistoricProcessInstanceDto> fetchEntities(DefinitionBasedImportPage page) {
    return fetchHistoricFinishedProcessInstances(
      page.getIndexOfFirstResult(),
      page.getPageSize(),
      page.getCurrentProcessDefinitionId());
  }

  public List<HistoricProcessInstanceDto> fetchHistoricFinishedProcessInstances(long indexOfFirstResult,
                                                                                long maxPageSize,
                                                                                String processDefinitionId) {
    long requestStart = System.currentTimeMillis();
    List<HistoricProcessInstanceDto> entries = client
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getHistoricProcessInstanceEndpoint())
      .queryParam(SORT_BY, SORT_TYPE_END_TIME)
      .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
      .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
      .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
      .queryParam(PROCESS_DEFINITION_ID, processDefinitionId)
      .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricProcessInstanceDto>>() {
      });
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] historic process instances within [{}] ms",
      entries.size(),
      requestEnd - requestStart
    );

    return entries;
  }
}
