/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricIdentityLinkLogDto;
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

import static org.camunda.optimize.service.util.importing.EngineConstants.IDENTITY_LINK_LOG_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.importing.EngineConstants.OCCURRED_AFTER;
import static org.camunda.optimize.service.util.importing.EngineConstants.OCCURRED_AT;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class IdentityLinkLogInstanceFetcher extends RetryBackoffEngineEntityFetcher {

  private DateTimeFormatter dateTimeFormatter;

  public IdentityLinkLogInstanceFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricIdentityLinkLogDto> fetchIdentityLinkLogs(final TimestampBasedImportPage page) {
    return fetchIdentityLinkLogs(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportIdentityLinkLogsMaxPageSize()
    );
  }

  public List<HistoricIdentityLinkLogDto> fetchIdentityLinkLogsForTimestamp(
    final OffsetDateTime endTimeOfLastInstance) {
    logger.debug("Fetching identity link logs ...");

    final long requestStart = System.currentTimeMillis();
    final List<HistoricIdentityLinkLogDto> secondEntries = fetchWithRetry(
      () -> performIdentityLinkLogRequest(endTimeOfLastInstance)
    );
    final long requestEnd = System.currentTimeMillis();

    logger.debug(
      "Fetched [{}] identity link logs within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<HistoricIdentityLinkLogDto> fetchIdentityLinkLogs(final OffsetDateTime timeStamp,
                                                                 final long pageSize) {
    logger.debug("Fetching identity link logs ...");

    final long requestStart = System.currentTimeMillis();
    final List<HistoricIdentityLinkLogDto> entries = fetchWithRetry(
      () -> performIdentityLinkLogRequest(timeStamp, pageSize)
    );
    final long requestEnd = System.currentTimeMillis();

    logger.debug(
      "Fetched [{}] identity links which occurred after set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );

    return entries;
  }

  private List<HistoricIdentityLinkLogDto> performIdentityLinkLogRequest(final OffsetDateTime timeStamp,
                                                                         final long pageSize) {
    // @formatter:off
    return createIdentityLinkLogWebTarget()
      .queryParam(OCCURRED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricIdentityLinkLogDto>>() {});
    // @formatter:on
  }

  private List<HistoricIdentityLinkLogDto> performIdentityLinkLogRequest(
    final OffsetDateTime endTimeOfLastInstance) {
    // @formatter:off
    return createIdentityLinkLogWebTarget()
      .queryParam(OCCURRED_AT, dateTimeFormatter.format(endTimeOfLastInstance))
      .queryParam(MAX_RESULTS_TO_RETURN, configurationService.getEngineImportIdentityLinkLogsMaxPageSize())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricIdentityLinkLogDto>>() {});
    // @formatter:on
  }

  private WebTarget createIdentityLinkLogWebTarget() {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(IDENTITY_LINK_LOG_ENDPOINT);
  }

}
