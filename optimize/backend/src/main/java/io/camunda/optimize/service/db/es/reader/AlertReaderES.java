/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.ALERT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeCountRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.AlertReader;
import io.camunda.optimize.service.db.schema.index.AlertIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AlertReaderES implements AlertReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(AlertReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public AlertReaderES(
      OptimizeElasticsearchClient esClient,
      ConfigurationService configurationService,
      ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public long getAlertCount() {
    final CountRequest countRequest =
        OptimizeCountRequestBuilderES.of(b -> b.optimizeIndex(esClient, ALERT_INDEX_NAME));
    try {
      return esClient.count(countRequest).count();
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve alert count!", e);
    }
  }

  @Override
  public List<AlertDefinitionDto> getStoredAlerts() {
    log.debug("getting all stored alerts");

    SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, ALERT_INDEX_NAME)
                    .size(LIST_FETCH_LIMIT)
                    .query(q -> q.matchAll(MatchAllQuery.of(m -> m)))
                    .scroll(
                        Time.of(
                            t ->
                                t.time(
                                    configurationService
                                            .getElasticSearchConfiguration()
                                            .getScrollTimeoutInSeconds()
                                        + "s"))));

    SearchResponse<AlertDefinitionDto> scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, AlertDefinitionDto.class);
    } catch (IOException e) {
      log.error("Was not able to retrieve stored alerts!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve stored alerts!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        scrollResp,
        AlertDefinitionDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  @Override
  public Optional<AlertDefinitionDto> getAlert(String alertId) {
    log.debug("Fetching alert with id [{}]", alertId);

    GetRequest getRequest =
        OptimizeGetRequestBuilderES.of(
            b -> b.optimizeIndex(esClient, ALERT_INDEX_NAME).id(alertId));

    try {
      return Optional.ofNullable(esClient.get(getRequest, AlertDefinitionDto.class).source());
    } catch (IOException e) {
      String reason = String.format("Could not fetch alert with id [%s]", alertId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  public List<AlertDefinitionDto> getAlertsForReport(String reportId) {
    log.debug("Fetching first {} alerts using report with id {}", LIST_FETCH_LIMIT, reportId);

    SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, ALERT_INDEX_NAME)
                    .query(
                        q ->
                            q.term(
                                t -> t.field(AlertIndex.REPORT_ID).value(FieldValue.of(reportId))))
                    .size(LIST_FETCH_LIMIT));

    SearchResponse<AlertDefinitionDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, AlertDefinitionDto.class);
    } catch (IOException e) {
      String reason =
          String.format("Was not able to fetch alerts for report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
        searchResponse.hits(), AlertDefinitionDto.class, objectMapper);
  }

  @Override
  public List<AlertDefinitionDto> getAlertsForReports(List<String> reportIds) {
    log.debug("Fetching first {} alerts using reports with ids {}", LIST_FETCH_LIMIT, reportIds);

    SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, ALERT_INDEX_NAME)
                    .query(
                        q ->
                            q.terms(
                                t ->
                                    t.field(AlertIndex.REPORT_ID)
                                        .terms(
                                            tt ->
                                                tt.value(
                                                    reportIds.stream()
                                                        .map(FieldValue::of)
                                                        .toList()))))
                    .size(LIST_FETCH_LIMIT));

    SearchResponse<AlertDefinitionDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, AlertDefinitionDto.class);
    } catch (IOException e) {
      String reason =
          String.format("Was not able to fetch alerts for reports with ids [%s]", reportIds);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
        searchResponse.hits(), AlertDefinitionDto.class, objectMapper);
  }

  private void logError(String alertId) {
    log.error("Was not able to retrieve alert with id [{}] from Elasticsearch.", alertId);
  }
}
