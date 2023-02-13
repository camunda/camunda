/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
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
import static org.camunda.optimize.service.util.importing.EngineConstants.RUNNING_ACTIVITY_INSTANCE_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.STARTED_AFTER;
import static org.camunda.optimize.service.util.importing.EngineConstants.STARTED_AT;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningActivityInstanceFetcher extends RetryBackoffEngineEntityFetcher {

  private DateTimeFormatter dateTimeFormatter;

  public RunningActivityInstanceFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricActivityInstanceEngineDto> fetchRunningActivityInstances(TimestampBasedImportPage page) {
    return fetchRunningActivityInstances(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportActivityInstanceMaxPageSize()
    );
  }

  public List<HistoricActivityInstanceEngineDto> fetchRunningActivityInstancesForTimestamp(
    OffsetDateTime startTimeOfLastInstance) {
    logger.debug("Fetching running activity instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricActivityInstanceEngineDto> secondEntries =
      fetchWithRetry(() -> performRunningActivityInstanceRequest(startTimeOfLastInstance));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] running activity instances for set end time within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<HistoricActivityInstanceEngineDto> fetchRunningActivityInstances(OffsetDateTime timeStamp,
                                                                                long pageSize) {
    logger.debug("Fetching historic activity instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricActivityInstanceEngineDto> entries =
      fetchWithRetry(() -> performRunningActivityInstanceRequest(timeStamp, pageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] running activity instances which started after set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );

    return entries;
  }

  private List<HistoricActivityInstanceEngineDto> performRunningActivityInstanceRequest(OffsetDateTime timeStamp,
                                                                                        long pageSize) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(RUNNING_ACTIVITY_INSTANCE_ENDPOINT)
      .queryParam(STARTED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricActivityInstanceEngineDto>>() {
      });
  }

  private List<HistoricActivityInstanceEngineDto> performRunningActivityInstanceRequest(OffsetDateTime startTimeOfLastInstance) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(RUNNING_ACTIVITY_INSTANCE_ENDPOINT)
      .queryParam(STARTED_AT, dateTimeFormatter.format(startTimeOfLastInstance))
      .queryParam(MAX_RESULTS_TO_RETURN, configurationService.getEngineImportActivityInstanceMaxPageSize())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricActivityInstanceEngineDto>>() {
      });
  }

}
