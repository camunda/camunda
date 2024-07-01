/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.fetcher.instance;

import static io.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;
import static io.camunda.optimize.service.util.importing.EngineConstants.RUNNING_USER_TASK_INSTANCE_ENDPOINT;
import static io.camunda.optimize.service.util.importing.EngineConstants.STARTED_AFTER;
import static io.camunda.optimize.service.util.importing.EngineConstants.STARTED_AT;

import io.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.importing.page.TimestampBasedImportPage;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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

  public List<HistoricUserTaskInstanceDto> fetchRunningUserTaskInstances(
      final TimestampBasedImportPage page) {
    return fetchRunningUserTaskInstances(
        page.getTimestampOfLastEntity(),
        configurationService.getEngineImportUserTaskInstanceMaxPageSize());
  }

  public List<HistoricUserTaskInstanceDto> fetchRunningUserTaskInstancesForTimestamp(
      final OffsetDateTime startTimeOfLastInstance) {
    logger.debug("Fetching running user task instances ...");

    final long requestStart = System.currentTimeMillis();
    final List<HistoricUserTaskInstanceDto> secondEntries =
        fetchWithRetry(() -> performRunningUserTaskInstanceRequest(startTimeOfLastInstance));
    final long requestEnd = System.currentTimeMillis();

    logger.debug(
        "Fetched [{}] running user task instances for set start time within [{}] ms",
        secondEntries.size(),
        requestEnd - requestStart);
    return secondEntries;
  }

  private List<HistoricUserTaskInstanceDto> fetchRunningUserTaskInstances(
      final OffsetDateTime timeStamp, final long pageSize) {
    logger.debug("Fetching running user task instances ...");

    final long requestStart = System.currentTimeMillis();
    final List<HistoricUserTaskInstanceDto> entries =
        fetchWithRetry(() -> performRunningUserTaskInstanceRequest(timeStamp, pageSize));
    final long requestEnd = System.currentTimeMillis();

    logger.debug(
        "Fetched [{}] running user task instances which started after set timestamp with page size [{}] within [{}] ms",
        entries.size(),
        pageSize,
        requestEnd - requestStart);

    return entries;
  }

  private List<HistoricUserTaskInstanceDto> performRunningUserTaskInstanceRequest(
      final OffsetDateTime timeStamp, final long pageSize) {
    // @formatter:off
    return createUserTaskInstanceWebTarget()
        .queryParam(STARTED_AFTER, dateTimeFormatter.format(timeStamp))
        .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
        .request(MediaType.APPLICATION_JSON)
        .acceptEncoding(UTF8)
        .get(new GenericType<>() {});
    // @formatter:on
  }

  private List<HistoricUserTaskInstanceDto> performRunningUserTaskInstanceRequest(
      final OffsetDateTime startTimeOfLastInstance) {
    // @formatter:off
    return createUserTaskInstanceWebTarget()
        .queryParam(STARTED_AT, dateTimeFormatter.format(startTimeOfLastInstance))
        .queryParam(
            MAX_RESULTS_TO_RETURN,
            configurationService.getEngineImportUserTaskInstanceMaxPageSize())
        .request(MediaType.APPLICATION_JSON)
        .acceptEncoding(UTF8)
        .get(new GenericType<>() {});
    // @formatter:on
  }

  private WebTarget createUserTaskInstanceWebTarget() {
    return getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(RUNNING_USER_TASK_INSTANCE_ENDPOINT);
  }
}
