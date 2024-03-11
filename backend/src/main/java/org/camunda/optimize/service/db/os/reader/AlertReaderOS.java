/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import static java.lang.String.format;
import static org.camunda.optimize.service.db.DatabaseConstants.ALERT_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.matchAll;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.stringTerms;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.term;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL.searchRequestBuilder;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.reader.AlertReader;
import org.camunda.optimize.service.db.schema.index.AlertIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class AlertReaderOS implements AlertReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  @Override
  public long getAlertCount() {
    return osClient.count(ALERT_INDEX_NAME, "Was not able to retrieve alert count!");
  }

  @Override
  public List<AlertDefinitionDto> getStoredAlerts() {
    log.debug("getting all stored alerts");

    SearchRequest.Builder requestBuilder =
        searchRequestBuilder(ALERT_INDEX_NAME).query(matchAll()).size(LIST_FETCH_LIMIT);

    return osClient.scrollValues(requestBuilder, AlertDefinitionDto.class);
  }

  @Override
  public Optional<AlertDefinitionDto> getAlert(String alertId) {
    log.debug("Fetching alert with id [{}]", alertId);

    String errorMsg = format("Could not fetch alert with id [%s]", alertId);
    GetResponse<AlertDefinitionDto> result =
        osClient.get(ALERT_INDEX_NAME, alertId, AlertDefinitionDto.class, errorMsg);

    return result.found() ? Optional.ofNullable(result.source()) : Optional.empty();
  }

  @Override
  public List<AlertDefinitionDto> getAlertsForReport(String reportId) {
    log.debug("Fetching first {} alerts using report with id {}", LIST_FETCH_LIMIT, reportId);

    SearchRequest.Builder requestBuilder =
        searchRequestBuilder(ALERT_INDEX_NAME)
            .query(term(AlertIndex.REPORT_ID, reportId))
            .size(LIST_FETCH_LIMIT);

    return osClient.searchValues(requestBuilder, AlertDefinitionDto.class);
  }

  @Override
  public List<AlertDefinitionDto> getAlertsForReports(List<String> reportIds) {
    log.debug("Fetching first {} alerts using reports with ids {}", LIST_FETCH_LIMIT, reportIds);

    SearchRequest.Builder requestBuilder =
        searchRequestBuilder(ALERT_INDEX_NAME)
            .query(stringTerms(AlertIndex.REPORT_ID, reportIds))
            .size(LIST_FETCH_LIMIT);

    return osClient.searchValues(requestBuilder, AlertDefinitionDto.class);
  }
}
