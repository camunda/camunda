package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricVariableUpdateInstanceDto;
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
import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.DESERIALIZE_VALUES;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.HISTORY_DETAIL_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OCCURRED_AFTER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OCCURRED_BEFORE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_BY_TIME;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.SORT_ORDER_TYPE_ASCENDING;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.VARIABLE_UPDATES;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VariableUpdateInstanceFetcher extends
  RetryBackoffEngineEntityFetcher<HistoricVariableUpdateInstanceDto> {

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  public VariableUpdateInstanceFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  public List<HistoricVariableUpdateInstanceDto> fetchVariableInstanceUpdates(TimestampBasedImportPage page) {
    return fetchVariableInstanceUpdates(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportVariableInstanceMaxPageSize()
    );
  }

  private List<HistoricVariableUpdateInstanceDto> fetchVariableInstanceUpdates(OffsetDateTime timeStamp,
                                                                               long pageSize) {
    logger.debug("Fetching historic variable instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricVariableUpdateInstanceDto> entries =
      fetchWithRetry(() -> performGetVariableInstanceUpdateRequest(timeStamp, pageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] running historic variable instances which started after " +
        "set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );
    return entries;
  }

  private List<HistoricVariableUpdateInstanceDto> performGetVariableInstanceUpdateRequest(OffsetDateTime timeStamp, long pageSize) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(HISTORY_DETAIL_ENDPOINT)
      .queryParam(SORT_BY, SORT_BY_TIME)
      .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
      .queryParam(DESERIALIZE_VALUES, false)
      .queryParam(VARIABLE_UPDATES, true)
      .queryParam(OCCURRED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricVariableUpdateInstanceDto>>() {
      });
  }

  public List<HistoricVariableUpdateInstanceDto> fetchVariableInstanceUpdates(OffsetDateTime endTimeOfLastInstance) {
    logger.debug("Fetching historic variable instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricVariableUpdateInstanceDto> secondEntries =
      fetchWithRetry(() -> performGetVariableInstanceUpdateRequest(endTimeOfLastInstance));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] running historic variable instances for set start time within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<HistoricVariableUpdateInstanceDto> performGetVariableInstanceUpdateRequest(OffsetDateTime endTimeOfLastInstance) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(HISTORY_DETAIL_ENDPOINT)
      .queryParam(OCCURRED_AFTER, dateTimeFormatter.format(endTimeOfLastInstance))
      .queryParam(OCCURRED_BEFORE, dateTimeFormatter.format(endTimeOfLastInstance))
      .queryParam(DESERIALIZE_VALUES, false)
      .queryParam(VARIABLE_UPDATES, true)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricVariableUpdateInstanceDto>>() {
      });
  }

}
