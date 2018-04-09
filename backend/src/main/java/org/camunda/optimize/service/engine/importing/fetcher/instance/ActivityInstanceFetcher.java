package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.FINISHED_AFTER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_ONLY_FINISHED_INSTANCES;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER_TYPE_ASCENDING;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER_TYPE_DESCENDING;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_TYPE_END_TIME;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.TRUE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ActivityInstanceFetcher
  extends RetryBackoffEngineEntityFetcher<HistoricActivityInstanceEngineDto, DefinitionBasedImportPage> {

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  public ActivityInstanceFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  public List<HistoricActivityInstanceEngineDto> fetchEntities(DefinitionBasedImportPage page) {
    return fetchHistoricActivityInstances(
      page.getTimestampOfLastEntity(),
      page.getProcessDefinitionId(),
      configurationService.getEngineImportActivityInstanceMaxPageSize()
    );
  }

  private List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstances(OffsetDateTime timeStamp,
                                                                                 String processDefinitionId,
                                                                                 long pageSize) {
    long requestStart = System.currentTimeMillis();
    List<HistoricActivityInstanceEngineDto> entries = getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getHistoricActivityInstanceEndpoint())
      .queryParam(SORT_BY, SORT_TYPE_END_TIME)
      .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
      .queryParam(FINISHED_AFTER, dateTimeFormatter.format(timeStamp))
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

  public Optional<OffsetDateTime> fetchLatestEndTimeOfHistoricActivityInstances(String processDefinitionId) {
    List<HistoricActivityInstanceEngineDto> entries = getEngineClient()
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
          .path(configurationService.getHistoricActivityInstanceEndpoint())
          .queryParam(SORT_BY, SORT_TYPE_END_TIME)
          .queryParam(SORT_ORDER, SORT_ORDER_TYPE_DESCENDING)
          .queryParam(MAX_RESULTS_TO_RETURN, 1)
          .queryParam(PROCESS_DEFINITION_ID, processDefinitionId)
          .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
          .request()
          .acceptEncoding(UTF8)
          .get(new GenericType<List<HistoricActivityInstanceEngineDto>>() {
      });
    if (entries.size() > 0) {
      return Optional.of(entries.get(0).getEndTime());
    } else {
      return Optional.empty();
    }
  }
}
