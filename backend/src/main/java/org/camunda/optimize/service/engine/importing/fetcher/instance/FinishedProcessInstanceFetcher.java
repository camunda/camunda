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

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.COMPLETED_PROCESS_INSTANCE_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.FINISHED_AFTER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.FINISHED_AT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FinishedProcessInstanceFetcher extends
  RetryBackoffEngineEntityFetcher<HistoricProcessInstanceDto> {

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  public FinishedProcessInstanceFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  public List<HistoricProcessInstanceDto> fetchHistoricFinishedProcessInstances(TimestampBasedImportPage page) {
    return fetchHistoricFinishedProcessInstances(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportActivityInstanceMaxPageSize()
    );
  }

  private List<HistoricProcessInstanceDto> fetchHistoricFinishedProcessInstances(OffsetDateTime timeStamp,
                                                                                 long pageSize) {
    logger.debug("Fetching completed historic process instances...");
    long requestStart = System.currentTimeMillis();
    List<HistoricProcessInstanceDto> entries =
      fetchWithRetry(() -> performFinishedHistoricProcessInstanceRequest(timeStamp, pageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] completed historic process instances which ended after " +
        "set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );
    return entries;
  }

  private List<HistoricProcessInstanceDto> performFinishedHistoricProcessInstanceRequest(OffsetDateTime timeStamp, long pageSize) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(COMPLETED_PROCESS_INSTANCE_ENDPOINT)
      .queryParam(FINISHED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricProcessInstanceDto>>() {
      });
  }

  public List<HistoricProcessInstanceDto> fetchHistoricFinishedProcessInstances(OffsetDateTime endTimeOfLastInstance) {
    logger.debug("Fetching completed historic process instances...");
    long requestStart = System.currentTimeMillis();
    List<HistoricProcessInstanceDto> secondEntries =
      fetchWithRetry(() -> performFinishedHistoricProcessInstanceRequest(endTimeOfLastInstance));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] completed historic process instances for set end time within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<HistoricProcessInstanceDto> performFinishedHistoricProcessInstanceRequest(OffsetDateTime endTimeOfLastInstance) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(COMPLETED_PROCESS_INSTANCE_ENDPOINT)
      .queryParam(FINISHED_AT, dateTimeFormatter.format(endTimeOfLastInstance))
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricProcessInstanceDto>>() {
      });
  }
}
