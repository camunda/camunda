/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.COLLECTION_ID;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.MANAGEMENT_DASHBOARD;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.TILES;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.schema.index.DashboardIndex;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Collections;
import java.util.Optional;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DashboardWriterOS implements DashboardWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DashboardWriterOS.class);
  private final OptimizeOpenSearchClient osClient;

  public DashboardWriterOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public IdResponseDto createNewDashboard(
      final String userId, final DashboardDefinitionRestDto dashboardDefinitionDto) {
    if (userId == null) {
      throw new OptimizeRuntimeException("userId cannot be null");
    }
    if (dashboardDefinitionDto == null) {
      throw new OptimizeRuntimeException("dashboardDefinitionDto cannot be null");
    }
    return createNewDashboard(userId, dashboardDefinitionDto, IdGenerator.getNextId());
  }

  @Override
  public IdResponseDto createNewDashboard(
      final String userId,
      final DashboardDefinitionRestDto dashboardDefinitionDto,
      final String id) {
    if (userId == null) {
      throw new OptimizeRuntimeException("userId cannot be null");
    }
    if (dashboardDefinitionDto == null) {
      throw new OptimizeRuntimeException("dashboardDefinitionDto cannot be null");
    }
    if (id == null) {
      throw new OptimizeRuntimeException("id cannot be null");
    }

    LOG.debug("Writing new dashboard to OpenSearch");
    dashboardDefinitionDto.setOwner(userId);
    dashboardDefinitionDto.setName(
        Optional.ofNullable(dashboardDefinitionDto.getName()).orElse(DEFAULT_DASHBOARD_NAME));
    dashboardDefinitionDto.setLastModifier(userId);
    dashboardDefinitionDto.setId(id);
    return saveDashboard(dashboardDefinitionDto);
  }

  @Override
  public IdResponseDto saveDashboard(final DashboardDefinitionRestDto dashboardDefinitionDto) {
    if (dashboardDefinitionDto == null) {
      throw new OptimizeRuntimeException("dashboardDefinitionDto cannot be null");
    }

    dashboardDefinitionDto.setCreated(LocalDateUtil.getCurrentDateTime());
    dashboardDefinitionDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    final String dashboardId = dashboardDefinitionDto.getId();

    final IndexRequest.Builder<DashboardDefinitionRestDto> request =
        new IndexRequest.Builder<DashboardDefinitionRestDto>()
            .index(DASHBOARD_INDEX_NAME)
            .id(dashboardId)
            .document(dashboardDefinitionDto)
            .refresh(Refresh.True);

    final IndexResponse indexResponse = osClient.index(request);

    if (!indexResponse.result().equals(Result.Created)) {
      final String message =
          "Could not write dashboard to OpenSearch. "
              + "Maybe the connection to OpenSearch was lost?";
      LOG.error(message);
      throw new OptimizeRuntimeException(message);
    }

    LOG.debug("Dashboard with id [{}] has successfully been created.", dashboardId);
    return new IdResponseDto(dashboardId);
  }

  @Override
  public void updateDashboard(final DashboardDefinitionUpdateDto dashboard, final String id) {
    LOG.debug("Updating dashboard with id [{}] in OpenSearch", id);

    final UpdateRequest.Builder<Void, DashboardDefinitionUpdateDto> request =
        new UpdateRequest.Builder<Void, DashboardDefinitionUpdateDto>()
            .index(DASHBOARD_INDEX_NAME)
            .id(id)
            .doc(dashboard)
            .refresh(Refresh.True)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    final String errorMessage =
        String.format(
            "Was not able to update dashboard with id [%s] and name [%s].",
            id, dashboard.getName());

    final UpdateResponse<Void> updateResponse = osClient.update(request, errorMessage);

    if (updateResponse.shards().failed().intValue() > 0) {
      LOG.error(
          "Was not able to update dashboard with id [{}] and name [{}].", id, dashboard.getName());
      throw new OptimizeRuntimeException("Was not able to update dashboard!");
    }
  }

  @Override
  public void removeReportFromDashboards(final String reportId) {
    final String updateItem = String.format("report from dashboard with report ID [%s]", reportId);
    LOG.info("Removing {}}.", updateItem);

    final Script removeReportFromDashboardScript =
        OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
            "ctx._source.tiles.removeIf(report -> report.id.equals(params.idToRemove))",
            Collections.singletonMap("idToRemove", JsonData.of(reportId)));

    final Query query =
        new NestedQuery.Builder()
            .path(TILES)
            .query(QueryDSL.term(TILES + "." + DashboardIndex.REPORT_ID, reportId))
            .scoreMode(ChildScoreMode.None)
            .build()
            .toQuery();

    osClient.updateByQuery(DASHBOARD_INDEX_NAME, query, removeReportFromDashboardScript);
  }

  @Override
  public void deleteDashboardsOfCollection(final String collectionId) {
    final Query query = QueryDSL.term(COLLECTION_ID, collectionId);
    osClient.deleteByQuery(query, true, DASHBOARD_INDEX_NAME);
  }

  @Override
  public void deleteDashboard(final String dashboardId) {
    LOG.debug("Deleting dashboard with id [{}]", dashboardId);

    final DeleteRequest.Builder request =
        new DeleteRequest.Builder()
            .index(DASHBOARD_INDEX_NAME)
            .id(dashboardId)
            .refresh(Refresh.True);

    final String reason = String.format("Could not delete dashboard with id [%s].", dashboardId);

    final DeleteResponse deleteResponse = osClient.delete(request, reason);

    if (!deleteResponse.result().equals(Result.Deleted)) {
      final String message =
          String.format(
              "Could not delete dashboard with id [%s]. Dashboard does not exist.", dashboardId);
      LOG.error(message);
      throw new OptimizeRuntimeException(message);
    }
  }

  @Override
  public void deleteManagementDashboard() {
    final Query query = QueryDSL.term(MANAGEMENT_DASHBOARD, true);
    osClient.deleteByQuery(query, true, DASHBOARD_INDEX_NAME);
  }
}
