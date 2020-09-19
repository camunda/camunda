/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricIncidentEngineDto;
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

import static org.camunda.optimize.service.util.importing.EngineConstants.CREATED_AFTER;
import static org.camunda.optimize.service.util.importing.EngineConstants.CREATED_AT;
import static org.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.importing.EngineConstants.OPEN_INCIDENT_ENDPOINT;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class OpenIncidentFetcher extends RetryBackoffEngineEntityFetcher<HistoricIncidentEngineDto> {

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
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportIncidentMaxPageSize()
    );
  }

  public List<HistoricIncidentEngineDto> fetchOpenIncidentsForTimestamp(
    OffsetDateTime startTimeOfLastInstance) {
    logger.debug("Fetching open incidents ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricIncidentEngineDto> secondEntries =
      fetchWithRetry(() -> performOpenIncidentRequest(startTimeOfLastInstance));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] open incidents for set end time within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<HistoricIncidentEngineDto> fetchOpenIncidents(OffsetDateTime timeStamp,
                                                             long pageSize) {
    logger.debug("Fetching open incidents ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricIncidentEngineDto> entries =
      fetchWithRetry(() -> performOpenIncidentRequest(timeStamp, pageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] open incidents which started after set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );

    return entries;
  }

  private List<HistoricIncidentEngineDto> performOpenIncidentRequest(OffsetDateTime timeStamp,
                                                                     long pageSize) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(OPEN_INCIDENT_ENDPOINT)
      .queryParam(CREATED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricIncidentEngineDto>>() {
      });
  }

  private List<HistoricIncidentEngineDto> performOpenIncidentRequest(OffsetDateTime startTimeOfLastInstance) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(OPEN_INCIDENT_ENDPOINT)
      .queryParam(CREATED_AT, dateTimeFormatter.format(startTimeOfLastInstance))
      .queryParam(MAX_RESULTS_TO_RETURN, configurationService.getEngineImportIncidentMaxPageSize())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricIncidentEngineDto>>() {
      });
  }

}
