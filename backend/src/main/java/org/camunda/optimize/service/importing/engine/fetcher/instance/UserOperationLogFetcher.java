/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricUserOperationLogDto;
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
import static org.camunda.optimize.service.util.importing.EngineConstants.OCCURRED_AFTER;
import static org.camunda.optimize.service.util.importing.EngineConstants.OCCURRED_AT;
import static org.camunda.optimize.service.util.importing.EngineConstants.USER_OPERATION_LOG_ENDPOINT;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UserOperationLogFetcher extends RetryBackoffEngineEntityFetcher {
  private DateTimeFormatter dateTimeFormatter;

  public UserOperationLogFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricUserOperationLogDto> fetchUserOperationLogs(final TimestampBasedImportPage page) {
    return fetchUserOperationLogs(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportIdentityLinkLogsMaxPageSize()
    );
  }

  public List<HistoricUserOperationLogDto> fetchUserOperationLogsForTimestamp(
    final OffsetDateTime endTimeOfLastInstance) {
    logger.debug("Fetching user operations logs...");

    final long requestStart = System.currentTimeMillis();
    final List<HistoricUserOperationLogDto> secondEntries = fetchWithRetry(
      () -> performUserOperationLogsRequest(endTimeOfLastInstance)
    );
    final long requestEnd = System.currentTimeMillis();

    logger.debug(
      "Fetched [{}] user operation logs within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<HistoricUserOperationLogDto> fetchUserOperationLogs(final OffsetDateTime timeStamp,
                                                                   final long pageSize) {
    logger.debug("Fetching user operations logs...");

    final long requestStart = System.currentTimeMillis();
    final List<HistoricUserOperationLogDto> entries = fetchWithRetry(
      () -> performUserOperationLogsRequest(timeStamp, pageSize)
    );
    final long requestEnd = System.currentTimeMillis();

    logger.debug(
      "Fetched [{}] user operations which occurred after set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );

    return entries;
  }

  private List<HistoricUserOperationLogDto> performUserOperationLogsRequest(final OffsetDateTime timeStamp,
                                                                            final long pageSize) {
    // @formatter:off
    return createUserOperationLogWebTarget()
      .queryParam(OCCURRED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricUserOperationLogDto>>() {});
    // @formatter:on
  }

  private List<HistoricUserOperationLogDto> performUserOperationLogsRequest(
    final OffsetDateTime endTimeOfLastInstance) {
    // @formatter:off
    return createUserOperationLogWebTarget()
      .queryParam(OCCURRED_AT, dateTimeFormatter.format(endTimeOfLastInstance))
      .queryParam(MAX_RESULTS_TO_RETURN, configurationService.getEngineImportUserOperationLogsMaxPageSize())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<HistoricUserOperationLogDto>>() {});
    // @formatter:on
  }

  private WebTarget createUserOperationLogWebTarget() {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(USER_OPERATION_LOG_ENDPOINT);
  }

}