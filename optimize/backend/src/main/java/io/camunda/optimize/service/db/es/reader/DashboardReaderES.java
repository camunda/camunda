/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.COLLECTION_ID;

import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeCountRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.schema.index.DashboardIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DashboardReaderES implements DashboardReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DashboardReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public DashboardReaderES(
      final OptimizeElasticsearchClient esClient, final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public long getDashboardCount() {
    final CountRequest countRequest =
        OptimizeCountRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, DASHBOARD_INDEX_NAME)
                    .query(
                        q ->
                            q.term(
                                t -> t.field(DashboardIndex.MANAGEMENT_DASHBOARD).value(false))));
    try {
      return esClient.count(countRequest).count();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve dashboard count!", e);
    }
  }

  @Override
  public Optional<DashboardDefinitionRestDto> getDashboard(final String dashboardId) {
    LOG.debug("Fetching dashboard with id [{}]", dashboardId);
    final GetRequest getRequest =
        OptimizeGetRequestBuilderES.of(
            b -> b.optimizeIndex(esClient, DASHBOARD_INDEX_NAME).id(dashboardId));

    try {
      return Optional.ofNullable(
          esClient.get(getRequest, DashboardDefinitionRestDto.class).source());
    } catch (final IOException e) {
      final String reason = String.format("Could not fetch dashboard with id [%s]", dashboardId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboards(final Set<String> dashboardIds) {
    LOG.debug("Fetching dashboards with IDs {}", dashboardIds);
    final String[] dashboardIdsAsArray = dashboardIds.toArray(new String[0]);

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, DASHBOARD_INDEX_NAME)
                    .size(LIST_FETCH_LIMIT)
                    .query(q -> q.ids(i -> i.values(Arrays.stream(dashboardIdsAsArray).toList()))));

    final SearchResponse<DashboardDefinitionRestDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, DashboardDefinitionRestDto.class);
    } catch (final IOException e) {
      final String reason =
          String.format("Was not able to fetch dashboards for IDs [%s]", dashboardIds);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
        searchResponse.hits(), DashboardDefinitionRestDto.class, objectMapper);
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboardsForCollection(final String collectionId) {
    LOG.debug("Fetching dashboards using collection with id {}", collectionId);
    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, DASHBOARD_INDEX_NAME)
                    .query(q -> q.term(t -> t.field(COLLECTION_ID).value(collectionId)))
                    .size(LIST_FETCH_LIMIT));

    final SearchResponse<DashboardDefinitionRestDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, DashboardDefinitionRestDto.class);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch dashboards for collection with id [%s]", collectionId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
        searchResponse.hits(), DashboardDefinitionRestDto.class, objectMapper);
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboardsForReport(final String reportId) {
    LOG.debug("Fetching dashboards using report with id {}", reportId);

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, DASHBOARD_INDEX_NAME)
                    .size(LIST_FETCH_LIMIT)
                    .query(
                        q ->
                            q.bool(
                                bb ->
                                    bb.filter(
                                        f ->
                                            f.nested(
                                                n ->
                                                    n.path(DashboardIndex.TILES)
                                                        .scoreMode(ChildScoreMode.None)
                                                        .query(
                                                            qq ->
                                                                qq.term(
                                                                    tt ->
                                                                        tt.field(
                                                                                DashboardIndex.TILES
                                                                                    + "."
                                                                                    + DashboardIndex
                                                                                        .REPORT_ID)
                                                                            .value(reportId))))))));

    final SearchResponse<DashboardDefinitionRestDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, DashboardDefinitionRestDto.class);
    } catch (final IOException e) {
      final String reason =
          String.format("Was not able to fetch dashboards for report with id [%s]", reportId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
        searchResponse.hits(), DashboardDefinitionRestDto.class, objectMapper);
  }
}
