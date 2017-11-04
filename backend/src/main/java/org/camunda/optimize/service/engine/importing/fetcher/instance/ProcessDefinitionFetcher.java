package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER_TYPE_DESCENDING;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_TYPE_ID;

@Component
public class ProcessDefinitionFetcher
  extends RetryBackoffEngineEntityFetcher<ProcessDefinitionEngineDto, AllEntitiesBasedImportPage> {

  @Override
  public List<ProcessDefinitionEngineDto> fetchEntities(AllEntitiesBasedImportPage page) {
    return fetchProcessDefinitions(
      page.getIndexOfFirstResult(),
      page.getPageSize()
    );
  }

  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions(long indexOfFirstResult,
                                                                  long maxPageSize) {
    long requestStart = System.currentTimeMillis();
    List<ProcessDefinitionEngineDto> entries = client
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine("1"))
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

    return entries;
  }
}
