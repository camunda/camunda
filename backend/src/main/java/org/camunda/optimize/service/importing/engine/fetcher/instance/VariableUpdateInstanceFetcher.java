/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.HistoricVariableUpdateInstanceDto;
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

import static org.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.importing.EngineConstants.OCCURRED_AFTER;
import static org.camunda.optimize.service.util.importing.EngineConstants.OCCURRED_AT;
import static org.camunda.optimize.service.util.importing.EngineConstants.VARIABLE_UPDATE_ENDPOINT;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VariableUpdateInstanceFetcher extends RetryBackoffEngineEntityFetcher {

  private static final String PARAM_EXCLUDE_OBJECT_VALUES = "excludeObjectValues";
  // @formatter:off
  private static final GenericType<List<HistoricVariableUpdateInstanceDto>> RESULT_TYPE_VARIABLE_LIST =
    new GenericType<List<HistoricVariableUpdateInstanceDto>>() {};
  // @formatter:on

  private DateTimeFormatter dateTimeFormatter;

  public VariableUpdateInstanceFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  @PostConstruct
  public void init() {
    dateTimeFormatter = DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat());
  }

  public List<HistoricVariableUpdateInstanceDto> fetchVariableInstanceUpdates(TimestampBasedImportPage page) {
    return fetchVariableInstanceUpdates(
      page.getTimestampOfLastEntity(),
      configurationService.getEngineImportVariableInstanceMaxPageSize()
    );
  }

  public List<HistoricVariableUpdateInstanceDto> fetchVariableInstanceUpdates(OffsetDateTime endTimeOfLastInstance) {
    logger.debug("Fetching historic variable instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricVariableUpdateInstanceDto> secondEntries =
      fetchWithRetry(() -> performGetVariableInstanceUpdateRequest(endTimeOfLastInstance));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] running historic variable instances for set start time within [{}] ms",
      secondEntries.size(),
      requestEnd - requestStart
    );
    return secondEntries;
  }

  private List<HistoricVariableUpdateInstanceDto> fetchVariableInstanceUpdates(OffsetDateTime timeStamp,
                                                                               long pageSize) {
    logger.debug("Fetching historic variable instances ...");
    long requestStart = System.currentTimeMillis();
    List<HistoricVariableUpdateInstanceDto> entries =
      fetchWithRetry(() -> performGetVariableInstanceUpdateRequest(timeStamp, pageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] running historic variable instances which started after " +
        "set timestamp with page size [{}] within [{}] ms",
      entries.size(),
      pageSize,
      requestEnd - requestStart
    );
    return entries;
  }

  private List<HistoricVariableUpdateInstanceDto> performGetVariableInstanceUpdateRequest(OffsetDateTime timeStamp,
                                                                                          long pageSize) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(VARIABLE_UPDATE_ENDPOINT)
      .queryParam(OCCURRED_AFTER, dateTimeFormatter.format(timeStamp))
      .queryParam(MAX_RESULTS_TO_RETURN, pageSize)
      .queryParam(PARAM_EXCLUDE_OBJECT_VALUES, !configurationService.getEngineImportVariableIncludeObjectVariableValue())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(RESULT_TYPE_VARIABLE_LIST);
  }

  private List<HistoricVariableUpdateInstanceDto> performGetVariableInstanceUpdateRequest(OffsetDateTime endTimeOfLastInstance) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(VARIABLE_UPDATE_ENDPOINT)
      .queryParam(OCCURRED_AT, dateTimeFormatter.format(endTimeOfLastInstance))
      .queryParam(MAX_RESULTS_TO_RETURN, configurationService.getEngineImportVariableInstanceMaxPageSize())
      .queryParam(PARAM_EXCLUDE_OBJECT_VALUES, !configurationService.getEngineImportVariableIncludeObjectVariableValue())
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(RESULT_TYPE_VARIABLE_LIST);
  }

}
