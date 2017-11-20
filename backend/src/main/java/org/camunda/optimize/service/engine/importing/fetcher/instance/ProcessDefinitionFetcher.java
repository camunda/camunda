package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER_TYPE_DESCENDING;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_TYPE_ID;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionFetcher
  extends RetryBackoffEngineEntityFetcher<ProcessDefinitionEngineDto, AllEntitiesBasedImportPage> {

  public ProcessDefinitionFetcher(String engineAlias) {
    super(engineAlias);
  }

  @Override
  public List<ProcessDefinitionEngineDto> fetchEntities(AllEntitiesBasedImportPage page) {
    return fetchProcessDefinitions(
      page.getIndexOfFirstResult(),
      page.getPageSize()
    );
  }

  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions(long indexOfFirstResult,
                                                                  long maxPageSize) {

    List<ProcessDefinitionEngineDto> entries = new ArrayList<>();
    try {
      long requestStart = System.currentTimeMillis();
      entries = getEngineClient()
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
          .path(configurationService.getProcessDefinitionEndpoint())
          .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
          .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
          .queryParam(SORT_BY, SORT_TYPE_ID)
          .queryParam(SORT_ORDER, SORT_ORDER_TYPE_DESCENDING)
          .request(MediaType.APPLICATION_JSON)
          .acceptEncoding(UTF8)
          .get(new GenericType<List<ProcessDefinitionEngineDto>>() {
          });

      long requestEnd = System.currentTimeMillis();
      logger.debug(
          "Fetched [{}] process definitions within [{}] ms",
          entries.size(),
          requestEnd - requestStart
      );
    } catch (Exception e) {
      logger.error("can't fetch PD from [{}]", this.engineAlias, e);
    }


    return entries;
  }
}
