/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.UserOperationLogEntryEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
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

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OCCURRED_AFTER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OCCURRED_AT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.USER_OPERATIONS_LOG_ENDPOINT;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UserOperationLogEntryFetcher extends RetryBackoffEngineEntityFetcher<UserOperationLogEntryEngineDto> {

  private DateTimeFormatter dateTimeFormatter;

  public UserOperationLogEntryFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<UserOperationLogEntryEngineDto> fetchUserOperationLogEntries(final TimestampBasedImportPage page) {
    return fetchUserOperationLogEntries(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportUserOperationLogEntryMaxPageSize()
    );
  }

  private List<UserOperationLogEntryEngineDto> fetchUserOperationLogEntries(final OffsetDateTime timeStamp,
                                                                            final long pageSize) {
    logger.debug("Fetching user operation entries ...");

    final long requestStart = System.currentTimeMillis();
    final List<UserOperationLogEntryEngineDto> entries = fetchWithRetry(
      () -> performCompletedUserTaskInstanceRequest(timeStamp, pageSize)
    );
    final long requestEnd = System.currentTimeMillis();

    logger.debug(
      "Fetched [{}] user operation entries which occurred after set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );

    return entries;
  }

  public List<UserOperationLogEntryEngineDto> fetchUserOperationLogEntriesForTimestamp(
    final OffsetDateTime endTimeOfLastInstance) {
    logger.debug("Fetching user operation entries ...");

    final long requestStart = System.currentTimeMillis();
    final List<UserOperationLogEntryEngineDto> secondEntries = fetchWithRetry(
      () -> performCompletedUserTaskInstanceRequest(endTimeOfLastInstance)
    );
    final long requestEnd = System.currentTimeMillis();

    logger.debug(
      "Fetched [{}] user operation log entries for set timestamp within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<UserOperationLogEntryEngineDto> performCompletedUserTaskInstanceRequest(final OffsetDateTime timeStamp,
                                                                                       final long pageSize) {
    // @formatter:off
    return createUserTaskInstanceWebTarget()
      .queryParam(OCCURRED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<UserOperationLogEntryEngineDto>>() {});
    // @formatter:on
  }

  private List<UserOperationLogEntryEngineDto> performCompletedUserTaskInstanceRequest(
    final OffsetDateTime timestampOfLastEntry) {
    // @formatter:off
    return createUserTaskInstanceWebTarget()
      .queryParam(OCCURRED_AT, dateTimeFormatter.format(timestampOfLastEntry))
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<UserOperationLogEntryEngineDto>>() {});
    // @formatter:on
  }

  private WebTarget createUserTaskInstanceWebTarget() {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(USER_OPERATIONS_LOG_ENDPOINT);
  }

}
