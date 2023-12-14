/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.schema.index.DashboardIndex;
import org.camunda.optimize.service.db.writer.DashboardWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;

import static org.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.schema.index.DashboardIndex.COLLECTION_ID;
import static org.camunda.optimize.service.db.schema.index.DashboardIndex.MANAGEMENT_DASHBOARD;
import static org.camunda.optimize.service.db.schema.index.DashboardIndex.TILES;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DashboardWriterOS implements DashboardWriter {

  private final OptimizeOpenSearchClient osClient;

  @Override
  public IdResponseDto createNewDashboard(@NonNull final String userId,
                                          @NonNull final DashboardDefinitionRestDto dashboardDefinitionDto) {
    return createNewDashboard(userId, dashboardDefinitionDto, IdGenerator.getNextId());
  }

  @Override
  public IdResponseDto createNewDashboard(@NonNull final String userId,
                                          @NonNull final DashboardDefinitionRestDto dashboardDefinitionDto,
                                          @NonNull final String id) {
    log.debug("Writing new dashboard to OpenSearch");
    dashboardDefinitionDto.setOwner(userId);
    dashboardDefinitionDto.setName(
      Optional.ofNullable(dashboardDefinitionDto.getName()).orElse(DEFAULT_DASHBOARD_NAME));
    dashboardDefinitionDto.setLastModifier(userId);
    dashboardDefinitionDto.setId(id);
    return saveDashboard(dashboardDefinitionDto);
  }

  @Override
  public IdResponseDto saveDashboard(@NonNull final DashboardDefinitionRestDto dashboardDefinitionDto) {
    dashboardDefinitionDto.setCreated(LocalDateUtil.getCurrentDateTime());
    dashboardDefinitionDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    final String dashboardId = dashboardDefinitionDto.getId();

    IndexRequest.Builder<DashboardDefinitionRestDto> request = new IndexRequest.Builder<DashboardDefinitionRestDto>()
      .index(DASHBOARD_INDEX_NAME)
      .id(dashboardId)
      .document(dashboardDefinitionDto)
      .refresh(Refresh.True);

    IndexResponse indexResponse = osClient.index(request);

    if (!indexResponse.result().equals(Result.Created)) {
      String message = "Could not write dashboard to OpenSearch. " +
        "Maybe the connection to OpenSearch was lost?";
      log.error(message);
      throw new OptimizeRuntimeException(message);
    }

    log.debug("Dashboard with id [{}] has successfully been created.", dashboardId);
    return new IdResponseDto(dashboardId);
  }

  @Override
  public void updateDashboard(DashboardDefinitionUpdateDto dashboard, String id) {
    log.debug("Updating dashboard with id [{}] in OpenSearch", id);

    UpdateRequest.Builder<Void, DashboardDefinitionUpdateDto> request = new UpdateRequest.Builder<Void,
      DashboardDefinitionUpdateDto>()
      .index(DASHBOARD_INDEX_NAME)
      .id(id)
      .doc(dashboard)
      .refresh(Refresh.True)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    final String errorMessage = String.format(
      "Was not able to update dashboard with id [%s] and name [%s].",
      id,
      dashboard.getName()
    );

    UpdateResponse<DashboardDefinitionUpdateDto> updateResponse = osClient.update(request, errorMessage);

    if (updateResponse.shards().failed().intValue() > 0) {
      log.error(
        "Was not able to update dashboard with id [{}] and name [{}].",
        id,
        dashboard.getName()
      );
      throw new OptimizeRuntimeException("Was not able to update dashboard!");
    }
  }

  public void removeReportFromDashboards(String reportId) {
    final String updateItem = String.format("report from dashboard with report ID [%s]", reportId);
    log.info("Removing {}}.", updateItem);

    Script removeReportFromDashboardScript =
      OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
        "ctx._source.tiles.removeIf(report -> report.id.equals(params.idToRemove))",
        Collections.singletonMap("idToRemove", JsonData.of(reportId))
      );

    Query query = new NestedQuery.Builder()
      .path(TILES)
      .query(QueryDSL.term(TILES + "." + DashboardIndex.REPORT_ID, reportId))
      .scoreMode(ChildScoreMode.None)
      .build()._toQuery();

    osClient.updateByQuery(DASHBOARD_INDEX_NAME, query, removeReportFromDashboardScript);
  }

  public void deleteDashboardsOfCollection(String collectionId) {
    Query query = QueryDSL.term(COLLECTION_ID, collectionId);
    osClient.deleteByQuery(query, DASHBOARD_INDEX_NAME);
  }

  public void deleteDashboard(String dashboardId) {
    log.debug("Deleting dashboard with id [{}]", dashboardId);

    DeleteRequest.Builder request = new DeleteRequest.Builder()
      .index(DASHBOARD_INDEX_NAME)
      .id(dashboardId)
      .refresh(Refresh.True);

    String reason = String.format("Could not delete dashboard with id [%s].", dashboardId);

    DeleteResponse deleteResponse = osClient.delete(request, reason);

    if (!deleteResponse.result().equals(Result.Deleted)) {
      String message =
        String.format("Could not delete dashboard with id [%s]. Dashboard does not exist.", dashboardId);
      log.error(message);
      throw new OptimizeRuntimeException(message);
    }
  }

  public void deleteManagementDashboard() {
    Query query = QueryDSL.term(MANAGEMENT_DASHBOARD, true);
    osClient.deleteByQuery(query, DASHBOARD_INDEX_NAME);
  }

}
