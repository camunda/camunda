/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.fetcher.instance;

import static io.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;
import static io.camunda.optimize.service.util.importing.EngineConstants.OCCURRED_AFTER;
import static io.camunda.optimize.service.util.importing.EngineConstants.OCCURRED_AT;
import static io.camunda.optimize.service.util.importing.EngineConstants.VARIABLE_UPDATE_ENDPOINT;

import io.camunda.optimize.dto.engine.HistoricVariableUpdateInstanceDto;
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
public class VariableUpdateInstanceFetcher extends RetryBackoffEngineEntityFetcher {

  private static final String PARAM_EXCLUDE_OBJECT_VALUES = "excludeObjectValues";
  // @formatter:off
  private static final GenericType<List<HistoricVariableUpdateInstanceDto>>
      RESULT_TYPE_VARIABLE_LIST = new GenericType<>() {};
  // @formatter:on

  private DateTimeFormatter dateTimeFormatter;

  public VariableUpdateInstanceFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricVariableUpdateInstanceDto> fetchVariableInstanceUpdates(
      final TimestampBasedImportPage page) {
    return fetchVariableInstanceUpdates(
        page.getTimestampOfLastEntity(),
        configurationService.getEngineImportVariableInstanceMaxPageSize());
  }

  public List<HistoricVariableUpdateInstanceDto> fetchVariableInstanceUpdates(
      final OffsetDateTime endTimeOfLastInstance) {
    logger.debug("Fetching historic variable instances ...");
    final long requestStart = System.currentTimeMillis();
    final List<HistoricVariableUpdateInstanceDto> secondEntries =
        fetchWithRetry(() -> performGetVariableInstanceUpdateRequest(endTimeOfLastInstance));
    final long requestEnd = System.currentTimeMillis();
    logger.debug(
        "Fetched [{}] running historic variable instances for set start time within [{}] ms",
        secondEntries.size(),
        requestEnd - requestStart);
    return secondEntries;
  }

  private List<HistoricVariableUpdateInstanceDto> fetchVariableInstanceUpdates(
      final OffsetDateTime timeStamp, final long pageSize) {
    logger.debug("Fetching historic variable instances ...");
    final long requestStart = System.currentTimeMillis();
    final List<HistoricVariableUpdateInstanceDto> entries =
        fetchWithRetry(() -> performGetVariableInstanceUpdateRequest(timeStamp, pageSize));
    final long requestEnd = System.currentTimeMillis();
    logger.debug(
        "Fetched [{}] running historic variable instances which started after "
            + "set timestamp with page size [{}] within [{}] ms",
        entries.size(),
        pageSize,
        requestEnd - requestStart);
    return entries;
  }

  private List<HistoricVariableUpdateInstanceDto> performGetVariableInstanceUpdateRequest(
      final OffsetDateTime timeStamp, final long pageSize) {
    return getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(VARIABLE_UPDATE_ENDPOINT)
        .queryParam(OCCURRED_AFTER, dateTimeFormatter.format(timeStamp))
        .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
        .queryParam(
            PARAM_EXCLUDE_OBJECT_VALUES,
            !configurationService.getEngineImportVariableIncludeObjectVariableValue())
        .request(MediaType.APPLICATION_JSON)
        .acceptEncoding(UTF8)
        .get(RESULT_TYPE_VARIABLE_LIST);
  }

  private List<HistoricVariableUpdateInstanceDto> performGetVariableInstanceUpdateRequest(
      final OffsetDateTime endTimeOfLastInstance) {
    return getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(VARIABLE_UPDATE_ENDPOINT)
        .queryParam(OCCURRED_AT, dateTimeFormatter.format(endTimeOfLastInstance))
        .queryParam(
            MAX_RESULTS_TO_RETURN,
            configurationService.getEngineImportVariableInstanceMaxPageSize())
        .queryParam(
            PARAM_EXCLUDE_OBJECT_VALUES,
            !configurationService.getEngineImportVariableIncludeObjectVariableValue())
        .request(MediaType.APPLICATION_JSON)
        .acceptEncoding(UTF8)
        .get(RESULT_TYPE_VARIABLE_LIST);
  }
}
