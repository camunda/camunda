/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.fetcher.instance;

import static io.camunda.optimize.service.util.importing.EngineConstants.CREATED_AFTER;
import static io.camunda.optimize.service.util.importing.EngineConstants.CREATED_AT;
import static io.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;
import static io.camunda.optimize.service.util.importing.EngineConstants.OPEN_INCIDENT_ENDPOINT;

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
public class OpenIncidentFetcher extends RetryBackoffEngineEntityFetcher {

  private DateTimeFormatter dateTimeFormatter;

  public OpenIncidentFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricIncidentEngineDto> fetchOpenIncidents(TimestampBasedImportPage page) {
    return fetchOpenIncidents(
        page.getTimestampOfLastEntity(), configurationService.getEngineImportIncidentMaxPageSize());
  }

  public List<HistoricIncidentEngineDto> fetchOpenIncidentsForTimestamp(
      OffsetDateTime startTimeOfLastIncident) {
    logger.debug("Fetching open incidents ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricIncidentEngineDto> secondEntries =
        fetchWithRetry(() -> performOpenIncidentsCreatedAtRequest(startTimeOfLastIncident));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
        "Fetched [{}] open incidents which were created at set timestamp within [{}] ms",
        secondEntries.size(),
        requestEnd - requestStart);
    return secondEntries;
  }

  private List<HistoricIncidentEngineDto> fetchOpenIncidents(
      OffsetDateTime timeStamp, long pageSize) {
    logger.debug("Fetching open incidents ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricIncidentEngineDto> entries =
        fetchWithRetry(() -> performOpenIncidentsCreatedAfterRequest(timeStamp, pageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
        "Fetched [{}] open incidents which started after set timestamp with page size [{}] within [{}] ms",
        entries.size(),
        pageSize,
        requestEnd - requestStart);

    return entries;
  }

  private List<HistoricIncidentEngineDto> performOpenIncidentsCreatedAfterRequest(
      OffsetDateTime createdAfterTimestamp, long pageSize) {
    return getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(OPEN_INCIDENT_ENDPOINT)
        .queryParam(CREATED_AFTER, dateTimeFormatter.format(createdAfterTimestamp))
        .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
        .request(MediaType.APPLICATION_JSON)
        .acceptEncoding(UTF8)
        .get(new GenericType<>() {});
  }

  private List<HistoricIncidentEngineDto> performOpenIncidentsCreatedAtRequest(
      OffsetDateTime createdAtTimestamp) {
    return getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(OPEN_INCIDENT_ENDPOINT)
        .queryParam(CREATED_AT, dateTimeFormatter.format(createdAtTimestamp))
        .request(MediaType.APPLICATION_JSON)
        .acceptEncoding(UTF8)
        .get(new GenericType<>() {});
  }
}
