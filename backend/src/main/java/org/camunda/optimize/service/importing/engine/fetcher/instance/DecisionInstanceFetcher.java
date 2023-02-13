/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricDecisionInstanceDto;
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

import static org.camunda.optimize.service.util.importing.EngineConstants.DECISION_INSTANCE_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionInstanceFetcher extends RetryBackoffEngineEntityFetcher {
  private static final String EVALUATED_AFTER = "evaluatedAfter";
  private static final String EVALUATED_AT = "evaluatedAt";

  private DateTimeFormatter dateTimeFormatter;

  public DecisionInstanceFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricDecisionInstanceDto> fetchHistoricDecisionInstances(final TimestampBasedImportPage page) {
    return fetchHistoricDecisionInstances(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportDecisionInstanceMaxPageSize()
    );
  }

  public List<HistoricDecisionInstanceDto> fetchHistoricDecisionInstances(final OffsetDateTime endTimeOfLastInstance) {
    logger.debug("Fetching historic decision instances...");

    final long requestStart = System.currentTimeMillis();
    final List<HistoricDecisionInstanceDto> secondEntries =
      fetchWithRetry(() -> performGetHistoricDecisionInstancesRequest(endTimeOfLastInstance));
    final long requestEnd = System.currentTimeMillis();

    logger.debug(
      "Fetched [{}] historic decision instances for set end time within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  public DateTimeFormatter getDateTimeFormatter() {
    return dateTimeFormatter;
  }

  public void setDateTimeFormatter(final DateTimeFormatter dateTimeFormatter) {
    this.dateTimeFormatter = dateTimeFormatter;
  }

  private List<HistoricDecisionInstanceDto> fetchHistoricDecisionInstances(final OffsetDateTime timeStamp,
                                                                           final long pageSize) {
    logger.debug("Fetching historic decision instances...");
    final long requestStart = System.currentTimeMillis();
    final List<HistoricDecisionInstanceDto> entries = fetchWithRetry(
      () -> performGetHistoricDecisionInstancesRequest(timeStamp, pageSize)
    );
    final long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] historic decision instances which ended after set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );
    return entries;
  }

  private List<HistoricDecisionInstanceDto> performGetHistoricDecisionInstancesRequest(final OffsetDateTime timeStamp,
                                                                                       final long pageSize) {
    // @formatter:off
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(DECISION_INSTANCE_ENDPOINT)
      .queryParam(EVALUATED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricDecisionInstanceDto>>() {});
    // @formatter:on
  }

  private List<HistoricDecisionInstanceDto> performGetHistoricDecisionInstancesRequest(
    final OffsetDateTime endTimeOfLastInstance) {
    // @formatter:off
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(DECISION_INSTANCE_ENDPOINT)
      .queryParam(EVALUATED_AT, dateTimeFormatter.format(endTimeOfLastInstance))
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricDecisionInstanceDto>>() {});
    // @formatter:on
  }
}
