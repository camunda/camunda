package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
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
public class ActivityInstanceFetcher
  extends RetryBackoffEngineEntityFetcher<HistoricActivityInstanceEngineDto, DefinitionBasedImportPage> {

  public ActivityInstanceFetcher(String engineAlias) {
    super(engineAlias);
  }

  @Override
  public List<HistoricActivityInstanceEngineDto> fetchEntities(DefinitionBasedImportPage page) {
    return fetchHistoricActivityInstances(
      page.getIndexOfFirstResult(),
      page.getPageSize(),
      page.getCurrentProcessDefinitionId()
    );
  }

  public List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstances(long indexOfFirstResult,
                                                                                long pageSize,
                                                                                String processDefinitionId) {
    long requestStart = System.currentTimeMillis();
    List<HistoricActivityInstanceEngineDto> entries = getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getHistoricActivityInstanceEndpoint())
      .queryParam(SORT_BY, SORT_TYPE_END_TIME)
      .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
      .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .queryParam(PROCESS_DEFINITION_ID, processDefinitionId)
      .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricActivityInstanceEngineDto>>() {
      });
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] historic activity instances within [{}] ms",
      entries.size(),
      requestEnd - requestStart
    );

    return entries;
  }
}
