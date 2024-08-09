/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.fetcher.instance;

import static io.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;
import static io.camunda.optimize.service.util.importing.EngineConstants.RUNNING_ACTIVITY_INSTANCE_ENDPOINT;
import static io.camunda.optimize.service.util.importing.EngineConstants.STARTED_AFTER;
import static io.camunda.optimize.service.util.importing.EngineConstants.STARTED_AT;

import io.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.importing.page.TimestampBasedImportPage;
import jakarta.annotation.PostConstruct;
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
public class RunningActivityInstanceFetcher extends RetryBackoffEngineEntityFetcher {

  private DateTimeFormatter dateTimeFormatter;

  public RunningActivityInstanceFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricActivityInstanceEngineDto> fetchRunningActivityInstances(
      TimestampBasedImportPage page) {
    return fetchRunningActivityInstances(
        page.getTimestampOfLastEntity(),
        configurationService.getEngineImportActivityInstanceMaxPageSize());
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
        requestEnd - requestStart);
    return secondEntries;
  }

  private List<HistoricActivityInstanceEngineDto> fetchRunningActivityInstances(
      OffsetDateTime timeStamp, long pageSize) {
    logger.debug("Fetching historic activity instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricActivityInstanceEngineDto> entries =
        fetchWithRetry(() -> performRunningActivityInstanceRequest(timeStamp, pageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
        "Fetched [{}] running activity instances which started after set timestamp with page size [{}] within [{}] ms",
        entries.size(),
        pageSize,
        requestEnd - requestStart);

    return entries;
  }

  private List<HistoricActivityInstanceEngineDto> performRunningActivityInstanceRequest(
      OffsetDateTime timeStamp, long pageSize) {
    return getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(RUNNING_ACTIVITY_INSTANCE_ENDPOINT)
        .queryParam(STARTED_AFTER, dateTimeFormatter.format(timeStamp))
        .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
        .request(MediaType.APPLICATION_JSON)
        .acceptEncoding(UTF8)
        .get(new GenericType<>() {});
  }

  private List<HistoricActivityInstanceEngineDto> performRunningActivityInstanceRequest(
      OffsetDateTime startTimeOfLastInstance) {
    return getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(RUNNING_ACTIVITY_INSTANCE_ENDPOINT)
        .queryParam(STARTED_AT, dateTimeFormatter.format(startTimeOfLastInstance))
        .queryParam(
            MAX_RESULTS_TO_RETURN,
            configurationService.getEngineImportActivityInstanceMaxPageSize())
        .request(MediaType.APPLICATION_JSON)
        .acceptEncoding(UTF8)
        .get(new GenericType<>() {});
  }
}
