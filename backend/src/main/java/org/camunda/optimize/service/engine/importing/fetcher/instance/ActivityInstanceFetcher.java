package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
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

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ACTIVITY_INSTANCE_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.FINISHED_AFTER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.FINISHED_AT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ActivityInstanceFetcher
  extends RetryBackoffEngineEntityFetcher<HistoricActivityInstanceEngineDto> {

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  public ActivityInstanceFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  public List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstances(TimestampBasedImportPage page) {
    return fetchHistoricActivityInstances(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportActivityInstanceMaxPageSize()
    );
  }

  private List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstances(OffsetDateTime timeStamp,
                                                                                 long pageSize) {
    logger.debug("Fetching historic activity instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricActivityInstanceEngineDto> entries =
      fetchWithRetry(() -> performHistoricActivityInstanceRequest(timeStamp, pageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] historic activity instances which ended after set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );

    return entries;
  }

  private List<HistoricActivityInstanceEngineDto> performHistoricActivityInstanceRequest(OffsetDateTime timeStamp, long pageSize) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(ACTIVITY_INSTANCE_ENDPOINT)
      .queryParam(FINISHED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricActivityInstanceEngineDto>>() {
      });
  }

  public List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstancesForTimestamp(
    OffsetDateTime endTimeOfLastInstance) {
    logger.debug("Fetching historic activity instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricActivityInstanceEngineDto> secondEntries =
      fetchWithRetry(() -> performHistoricActivityInstanceRequest(endTimeOfLastInstance));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] historic activity instances for set end time within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<HistoricActivityInstanceEngineDto> performHistoricActivityInstanceRequest(OffsetDateTime endTimeOfLastInstance) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(ACTIVITY_INSTANCE_ENDPOINT)
      .queryParam(FINISHED_AT, dateTimeFormatter.format(endTimeOfLastInstance))
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricActivityInstanceEngineDto>>() {
      });
  }

}
