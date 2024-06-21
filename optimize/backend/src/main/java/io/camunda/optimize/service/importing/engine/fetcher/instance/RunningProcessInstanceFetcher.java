/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.fetcher.instance;

import static io.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;
import static io.camunda.optimize.service.util.importing.EngineConstants.RUNNING_PROCESS_INSTANCE_ENDPOINT;
import static io.camunda.optimize.service.util.importing.EngineConstants.STARTED_AFTER;
import static io.camunda.optimize.service.util.importing.EngineConstants.STARTED_AT;

import io.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
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
public class RunningProcessInstanceFetcher extends RetryBackoffEngineEntityFetcher {

  private DateTimeFormatter dateTimeFormatter;

  public RunningProcessInstanceFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricProcessInstanceDto> fetchRunningProcessInstances(
      final TimestampBasedImportPage page) {
    return fetchRunningProcessInstances(
        page.getTimestampOfLastEntity(),
        configurationService.getEngineImportProcessInstanceMaxPageSize());
  }

  public List<HistoricProcessInstanceDto> fetchRunningProcessInstances(
      final OffsetDateTime startTimeOfLastInstance) {
    logger.debug("Fetching running historic process instances ...");
    final long requestStart = System.currentTimeMillis();
    final List<HistoricProcessInstanceDto> secondEntries =
        fetchWithRetry(() -> performRunningProcessInstanceRequest(startTimeOfLastInstance));
    final long requestEnd = System.currentTimeMillis();
    logger.debug(
        "Fetched [{}] running historic process instances for set start time within [{}] ms",
        secondEntries.size(),
        requestEnd - requestStart);
    return secondEntries;
  }

  private List<HistoricProcessInstanceDto> fetchRunningProcessInstances(
      final OffsetDateTime timeStamp, final long pageSize) {
    logger.debug("Fetching running historic process instances ...");
    final long requestStart = System.currentTimeMillis();
    final List<HistoricProcessInstanceDto> entries =
        fetchWithRetry(() -> performRunningProcessInstanceRequest(timeStamp, pageSize));
    final long requestEnd = System.currentTimeMillis();
    logger.debug(
        "Fetched [{}] running historic process instances which started after set timestamp with page size [{}] within "
            + "[{}] ms",
        entries.size(),
        pageSize,
        requestEnd - requestStart);
    return entries;
  }

  private List<HistoricProcessInstanceDto> performRunningProcessInstanceRequest(
      final OffsetDateTime timeStamp, final long pageSize) {
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

  private List<HistoricProcessInstanceDto> performRunningProcessInstanceRequest(
      final OffsetDateTime startTimeOfLastInstance) {
    return getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(RUNNING_PROCESS_INSTANCE_ENDPOINT)
        .queryParam(STARTED_AT, dateTimeFormatter.format(startTimeOfLastInstance))
        .queryParam(
            MAX_RESULTS_TO_RETURN, configurationService.getEngineImportProcessInstanceMaxPageSize())
        .request(MediaType.APPLICATION_JSON)
        .acceptEncoding(UTF8)
        .get(new GenericType<List<HistoricProcessInstanceDto>>() {
        });
  }
}
