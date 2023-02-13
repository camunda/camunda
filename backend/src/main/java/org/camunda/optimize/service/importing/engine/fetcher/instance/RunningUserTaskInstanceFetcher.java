/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.page.TimestampBasedImportPage;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.importing.EngineConstants.RUNNING_USER_TASK_INSTANCE_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.STARTED_AFTER;
import static org.camunda.optimize.service.util.importing.EngineConstants.STARTED_AT;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningUserTaskInstanceFetcher extends RetryBackoffEngineEntityFetcher {

  private DateTimeFormatter dateTimeFormatter;

  public RunningUserTaskInstanceFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricUserTaskInstanceDto> fetchRunningUserTaskInstances(final TimestampBasedImportPage page) {
    return fetchRunningUserTaskInstances(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportUserTaskInstanceMaxPageSize()
    );
  }

  public List<HistoricUserTaskInstanceDto> fetchRunningUserTaskInstancesForTimestamp(
    final OffsetDateTime startTimeOfLastInstance) {
    logger.debug("Fetching running user task instances ...");

    final long requestStart = System.currentTimeMillis();
    final List<HistoricUserTaskInstanceDto> secondEntries = fetchWithRetry(
      () -> performRunningUserTaskInstanceRequest(startTimeOfLastInstance)
    );
    final long requestEnd = System.currentTimeMillis();

    logger.debug(
      "Fetched [{}] running user task instances for set start time within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<HistoricUserTaskInstanceDto> fetchRunningUserTaskInstances(final OffsetDateTime timeStamp,
                                                                          final long pageSize) {
    logger.debug("Fetching running user task instances ...");

    final long requestStart = System.currentTimeMillis();
    final List<HistoricUserTaskInstanceDto> entries = fetchWithRetry(
      () -> performRunningUserTaskInstanceRequest(timeStamp, pageSize)
    );
    final long requestEnd = System.currentTimeMillis();

    logger.debug(
      "Fetched [{}] running user task instances which started after set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );

    return entries;
  }

  private List<HistoricUserTaskInstanceDto> performRunningUserTaskInstanceRequest(final OffsetDateTime timeStamp,
                                                                                  final long pageSize) {
    // @formatter:off
    return createUserTaskInstanceWebTarget()
      .queryParam(STARTED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricUserTaskInstanceDto>>() {});
    // @formatter:on
  }

  private List<HistoricUserTaskInstanceDto> performRunningUserTaskInstanceRequest(
    final OffsetDateTime startTimeOfLastInstance) {
    // @formatter:off
    return createUserTaskInstanceWebTarget()
      .queryParam(STARTED_AT, dateTimeFormatter.format(startTimeOfLastInstance))
      .queryParam(MAX_RESULTS_TO_RETURN, configurationService.getEngineImportUserTaskInstanceMaxPageSize())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricUserTaskInstanceDto>>() {});
    // @formatter:on
  }

  private WebTarget createUserTaskInstanceWebTarget() {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(RUNNING_USER_TASK_INSTANCE_ENDPOINT);
  }

}
