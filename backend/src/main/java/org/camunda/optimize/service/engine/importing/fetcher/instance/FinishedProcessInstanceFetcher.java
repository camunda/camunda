package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.FINISHED_AFTER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.FINISHED_BEFORE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INCLUDE_ONLY_FINISHED_INSTANCES;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER_TYPE_ASCENDING;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_TYPE_END_TIME;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.TRUE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinishedProcessInstanceFetcher extends
  RetryBackoffEngineEntityFetcher<HistoricProcessInstanceDto, TimestampBasedImportPage> {

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  public FinishedProcessInstanceFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  public List<HistoricProcessInstanceDto> fetchEntities(TimestampBasedImportPage page) {
    return fetchHistoricFinishedProcessInstances(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportActivityInstanceMaxPageSize()
    );
  }

  private List<HistoricProcessInstanceDto> fetchHistoricFinishedProcessInstances(OffsetDateTime timeStamp,
                                                                                 long pageSize) {
    long requestStart = System.currentTimeMillis();
    List<HistoricProcessInstanceDto> entries = getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getHistoricProcessInstanceEndpoint())
      .queryParam(SORT_BY, SORT_TYPE_END_TIME)
      .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
      .queryParam(FINISHED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricProcessInstanceDto>>() {
      });
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] historic process instances which ended after set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );

    if (!entries.isEmpty()) {
      OffsetDateTime endTimeOfLastInstance = entries.get(entries.size() - 1).getEndTime();
      List<HistoricProcessInstanceDto> secondEntries =
        fetchFinishedProcessInstancesForTimestamp(endTimeOfLastInstance);
      entries.addAll(secondEntries);
    }
    return entries;
  }

  private List<HistoricProcessInstanceDto> fetchFinishedProcessInstancesForTimestamp(OffsetDateTime endTimeOfLastInstance) {
    long requestStart = System.currentTimeMillis();
    List<HistoricProcessInstanceDto> secondEntries = getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getHistoricProcessInstanceEndpoint())
      .queryParam(FINISHED_AFTER, dateTimeFormatter.format(endTimeOfLastInstance))
      .queryParam(FINISHED_BEFORE, dateTimeFormatter.format(endTimeOfLastInstance))
      .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricProcessInstanceDto>>() {
      });
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] historic process instances for set end time within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }
}
