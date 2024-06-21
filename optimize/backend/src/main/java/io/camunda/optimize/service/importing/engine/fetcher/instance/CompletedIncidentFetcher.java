/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.fetcher.instance;

import static io.camunda.optimize.service.util.importing.EngineConstants.COMPLETED_INCIDENT_ENDPOINT;
import static io.camunda.optimize.service.util.importing.EngineConstants.FINISHED_AFTER;
import static io.camunda.optimize.service.util.importing.EngineConstants.FINISHED_AT;
import static io.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;

import io.camunda.optimize.dto.engine.HistoricIncidentEngineDto;
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
public class CompletedIncidentFetcher extends RetryBackoffEngineEntityFetcher {

  private DateTimeFormatter dateTimeFormatter;

  public CompletedIncidentFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricIncidentEngineDto> fetchCompletedIncidents(
      final TimestampBasedImportPage page) {
    return fetchCompletedIncidents(
        page.getTimestampOfLastEntity(), configurationService.getEngineImportIncidentMaxPageSize());
  }

  public List<HistoricIncidentEngineDto> fetchCompletedIncidentsForTimestamp(
      final OffsetDateTime endTimeOfLastIncident) {
    logger.debug("Fetching completed incidents ...");
    final long requestStart = System.currentTimeMillis();
    final List<HistoricIncidentEngineDto> secondEntries =
        fetchWithRetry(() -> performCompletedIncidentFinishedAtRequest(endTimeOfLastIncident));
    final long requestEnd = System.currentTimeMillis();
    logger.debug(
        "Fetched [{}] historic incidents for set end time within [{}] ms",
        secondEntries.size(),
        requestEnd - requestStart);
    return secondEntries;
  }

  private List<HistoricIncidentEngineDto> fetchCompletedIncidents(
      final OffsetDateTime timeStamp, final long pageSize) {
    logger.debug("Fetching historic incidents ...");
    final long requestStart = System.currentTimeMillis();
    final List<HistoricIncidentEngineDto> entries =
        fetchWithRetry(() -> performCompletedIncidentFinishedAfterRequest(timeStamp, pageSize));
    final long requestEnd = System.currentTimeMillis();
    logger.debug(
        "Fetched [{}] historic incidents which ended after set timestamp with page size [{}] within [{}] ms",
        entries.size(),
        pageSize,
        requestEnd - requestStart);

    return entries;
  }

  private List<HistoricIncidentEngineDto> performCompletedIncidentFinishedAfterRequest(
      final OffsetDateTime finishedAfterTimestamp, final long pageSize) {
    return getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(COMPLETED_INCIDENT_ENDPOINT)
        .queryParam(FINISHED_AFTER, dateTimeFormatter.format(finishedAfterTimestamp))
        .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
        .request(MediaType.APPLICATION_JSON)
        .acceptEncoding(UTF8)
        .get(new GenericType<>() {
        });
  }

  private List<HistoricIncidentEngineDto> performCompletedIncidentFinishedAtRequest(
      final OffsetDateTime finishedAtTimestamp) {
    return getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(COMPLETED_INCIDENT_ENDPOINT)
        .queryParam(FINISHED_AT, dateTimeFormatter.format(finishedAtTimestamp))
        .queryParam(
            MAX_RESULTS_TO_RETURN, configurationService.getEngineImportIncidentMaxPageSize())
        .request(MediaType.APPLICATION_JSON)
        .acceptEncoding(UTF8)
        .get(new GenericType<>() {
        });
  }
}
