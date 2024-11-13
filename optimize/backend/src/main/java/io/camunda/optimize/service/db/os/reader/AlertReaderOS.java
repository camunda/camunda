/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.ALERT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.stringTerms;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.client.dsl.RequestDSL.searchRequestBuilder;
import static java.lang.String.format;

import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.reader.AlertReader;
import io.camunda.optimize.service.db.schema.index.AlertIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class AlertReaderOS implements AlertReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AlertReaderOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  public AlertReaderOS(
      final OptimizeOpenSearchClient osClient, final ConfigurationService configurationService) {
    this.osClient = osClient;
    this.configurationService = configurationService;
  }

  @Override
  public long getAlertCount() {
    return osClient.count(ALERT_INDEX_NAME, "Was not able to retrieve alert count!");
  }

  @Override
  public List<AlertDefinitionDto> getStoredAlerts() {
    LOG.debug("getting all stored alerts");

    final SearchRequest.Builder requestBuilder =
        searchRequestBuilder(ALERT_INDEX_NAME).query(matchAll()).size(LIST_FETCH_LIMIT);

    return osClient.scrollValues(requestBuilder, AlertDefinitionDto.class);
  }

  @Override
  public Optional<AlertDefinitionDto> getAlert(final String alertId) {
    LOG.debug("Fetching alert with id [{}]", alertId);

    final String errorMsg = format("Could not fetch alert with id [%s]", alertId);
    final GetResponse<AlertDefinitionDto> result =
        osClient.get(ALERT_INDEX_NAME, alertId, AlertDefinitionDto.class, errorMsg);

    return result.found() ? Optional.ofNullable(result.source()) : Optional.empty();
  }

  @Override
  public List<AlertDefinitionDto> getAlertsForReport(final String reportId) {
    LOG.debug("Fetching first {} alerts using report with id {}", LIST_FETCH_LIMIT, reportId);

    final SearchRequest.Builder requestBuilder =
        searchRequestBuilder(ALERT_INDEX_NAME)
            .query(term(AlertIndex.REPORT_ID, reportId))
            .size(LIST_FETCH_LIMIT);

    return osClient.searchValues(requestBuilder, AlertDefinitionDto.class);
  }

  @Override
  public List<AlertDefinitionDto> getAlertsForReports(final List<String> reportIds) {
    LOG.debug("Fetching first {} alerts using reports with ids {}", LIST_FETCH_LIMIT, reportIds);

    final SearchRequest.Builder requestBuilder =
        searchRequestBuilder(ALERT_INDEX_NAME)
            .query(stringTerms(AlertIndex.REPORT_ID, reportIds))
            .size(LIST_FETCH_LIMIT);

    return osClient.searchValues(requestBuilder, AlertDefinitionDto.class);
  }
}
