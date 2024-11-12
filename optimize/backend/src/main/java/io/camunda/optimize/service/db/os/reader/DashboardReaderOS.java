/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.COLLECTION_ID;

import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.schema.index.DashboardIndex;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DashboardReaderOS implements DashboardReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DashboardReaderOS.class);
  private final OptimizeOpenSearchClient osClient;

  public DashboardReaderOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public long getDashboardCount() {
    final String errorMessage = "Was not able to retrieve dashboard count!";
    return osClient.count(
        new String[] {DASHBOARD_INDEX_NAME},
        QueryDSL.term(DashboardIndex.MANAGEMENT_DASHBOARD, false),
        errorMessage);
  }

  @Override
  public Optional<DashboardDefinitionRestDto> getDashboard(final String dashboardId) {
    LOG.debug("Fetching dashboard with id [{}]", dashboardId);
    final GetRequest.Builder getRequest =
        new GetRequest.Builder().index(DASHBOARD_INDEX_NAME).id(dashboardId);

    final String errorMessage =
        String.format("Could not fetch dashboard with id [%s]", dashboardId);

    final GetResponse<DashboardDefinitionRestDto> getResponse =
        osClient.get(getRequest, DashboardDefinitionRestDto.class, errorMessage);

    if (!getResponse.found()) {
      return Optional.empty();
    }
    return Optional.ofNullable(getResponse.source());
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboards(final Set<String> dashboardIds) {
    LOG.debug("Fetching dashboards with IDs {}", dashboardIds);
    final String[] dashboardIdsAsArray = dashboardIds.toArray(new String[0]);

    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(DASHBOARD_INDEX_NAME)
            .query(QueryDSL.ids(dashboardIdsAsArray))
            .size(LIST_FETCH_LIMIT);

    final String errorMessage =
        String.format("Was not able to fetch dashboards for IDs [%s]", dashboardIds);

    final SearchResponse<DashboardDefinitionRestDto> searchResponse =
        osClient.search(requestBuilder, DashboardDefinitionRestDto.class, errorMessage);

    return OpensearchReaderUtil.extractResponseValues(searchResponse);
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboardsForCollection(final String collectionId) {
    LOG.debug("Fetching dashboards using collection with id {}", collectionId);

    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(DASHBOARD_INDEX_NAME)
            .query(QueryDSL.term(COLLECTION_ID, collectionId))
            .size(LIST_FETCH_LIMIT);

    final String errorMessage =
        String.format("Was not able to fetch dashboards for collection with id [%s]", collectionId);

    final SearchResponse<DashboardDefinitionRestDto> searchResponse =
        osClient.search(requestBuilder, DashboardDefinitionRestDto.class, errorMessage);
    return OpensearchReaderUtil.extractResponseValues(searchResponse);
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboardsForReport(final String reportId) {
    LOG.debug("Fetching dashboards using report with id {}", reportId);

    final Query getCombinedReportsBySimpleReportIdQuery =
        new BoolQuery.Builder()
            .filter(
                new NestedQuery.Builder()
                    .path(DashboardIndex.TILES)
                    .query(
                        QueryDSL.term(
                            DashboardIndex.TILES + "." + DashboardIndex.REPORT_ID, reportId))
                    .scoreMode(ChildScoreMode.None)
                    .build()
                    .toQuery())
            .build()
            .toQuery();

    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(DASHBOARD_INDEX_NAME)
            .query(getCombinedReportsBySimpleReportIdQuery)
            .size(LIST_FETCH_LIMIT);

    final String errorMessage =
        String.format("Was not able to fetch dashboards for report with id [%s]", reportId);
    final SearchResponse<DashboardDefinitionRestDto> searchResponse =
        osClient.search(requestBuilder, DashboardDefinitionRestDto.class, errorMessage);

    return OpensearchReaderUtil.extractResponseValues(searchResponse);
  }
}
