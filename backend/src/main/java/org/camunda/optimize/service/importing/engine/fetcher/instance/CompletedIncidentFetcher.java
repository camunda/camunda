/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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

import static org.camunda.optimize.service.util.importing.EngineConstants.COMPLETED_INCIDENT_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.FINISHED_AFTER;
import static org.camunda.optimize.service.util.importing.EngineConstants.FINISHED_AT;
import static org.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompletedIncidentFetcher
  extends RetryBackoffEngineEntityFetcher {

  private DateTimeFormatter dateTimeFormatter;

  public CompletedIncidentFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricIncidentEngineDto> fetchCompletedIncidents(TimestampBasedImportPage page) {
    return fetchCompletedIncidents(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportIncidentMaxPageSize()
    );
  }

  public List<HistoricIncidentEngineDto> fetchCompletedIncidentsForTimestamp(OffsetDateTime endTimeOfLastIncident) {
    logger.debug("Fetching completed incidents ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricIncidentEngineDto> secondEntries =
      fetchWithRetry(() -> performCompletedIncidentFinishedAtRequest(endTimeOfLastIncident));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] historic incidents for set end time within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<HistoricIncidentEngineDto> fetchCompletedIncidents(OffsetDateTime timeStamp,
                                                                  long pageSize) {
    logger.debug("Fetching historic incidents ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricIncidentEngineDto> entries =
      fetchWithRetry(() -> performCompletedIncidentFinishedAfterRequest(timeStamp, pageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] historic incidents which ended after set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );

    return entries;
  }

  private List<HistoricIncidentEngineDto> performCompletedIncidentFinishedAfterRequest(OffsetDateTime finishedAfterTimestamp,
                                                                                       long pageSize) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(COMPLETED_INCIDENT_ENDPOINT)
      .queryParam(FINISHED_AFTER, dateTimeFormatter.format(finishedAfterTimestamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricIncidentEngineDto>>() {
      });
  }

  private List<HistoricIncidentEngineDto> performCompletedIncidentFinishedAtRequest(OffsetDateTime finishedAtTimestamp) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(COMPLETED_INCIDENT_ENDPOINT)
      .queryParam(FINISHED_AT, dateTimeFormatter.format(finishedAtTimestamp))
      .queryParam(MAX_RESULTS_TO_RETURN, configurationService.getEngineImportIncidentMaxPageSize())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricIncidentEngineDto>>() {
      });
  }

}
