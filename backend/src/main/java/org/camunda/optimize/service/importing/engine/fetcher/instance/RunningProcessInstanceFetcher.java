/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.page.TimestampBasedImportPage;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.importing.EngineConstants.RUNNING_PROCESS_INSTANCE_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.STARTED_AFTER;
import static org.camunda.optimize.service.util.importing.EngineConstants.STARTED_AT;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningProcessInstanceFetcher extends RetryBackoffEngineEntityFetcher {

  private DateTimeFormatter dateTimeFormatter;

  public RunningProcessInstanceFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricProcessInstanceDto> fetchRunningProcessInstances(TimestampBasedImportPage page) {
    return fetchRunningProcessInstances(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportProcessInstanceMaxPageSize()
    );
  }

  public List<HistoricProcessInstanceDto> fetchRunningProcessInstances(OffsetDateTime startTimeOfLastInstance) {
    logger.debug("Fetching running historic process instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricProcessInstanceDto> secondEntries =
      fetchWithRetry(() -> performRunningProcessInstanceRequest(startTimeOfLastInstance));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] running historic process instances for set start time within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<HistoricProcessInstanceDto> fetchRunningProcessInstances(OffsetDateTime timeStamp,
                                                                        long pageSize) {
    logger.debug("Fetching running historic process instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricProcessInstanceDto> entries =
      fetchWithRetry(() -> performRunningProcessInstanceRequest(timeStamp, pageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] running historic process instances which started after set timestamp with page size [{}] within " +
        "[{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );
    return entries;
  }

  private List<HistoricProcessInstanceDto> performRunningProcessInstanceRequest(OffsetDateTime timeStamp,
                                                                                long pageSize) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(RUNNING_PROCESS_INSTANCE_ENDPOINT)
      .queryParam(STARTED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricProcessInstanceDto>>() {
      });
  }

  private List<HistoricProcessInstanceDto> performRunningProcessInstanceRequest(OffsetDateTime startTimeOfLastInstance) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(RUNNING_PROCESS_INSTANCE_ENDPOINT)
      .queryParam(STARTED_AT, dateTimeFormatter.format(startTimeOfLastInstance))
      .queryParam(MAX_RESULTS_TO_RETURN, configurationService.getEngineImportProcessInstanceMaxPageSize())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricProcessInstanceDto>>() {
      });
  }
}
