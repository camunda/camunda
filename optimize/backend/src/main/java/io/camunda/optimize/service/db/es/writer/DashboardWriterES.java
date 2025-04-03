/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.COLLECTION_ID;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.MANAGEMENT_DASHBOARD;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.TILES;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.db.schema.index.DashboardIndex;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class DashboardWriterES implements DashboardWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final TaskRepositoryES taskRepositoryES;

  @Override
  public IdResponseDto createNewDashboard(
      @NonNull final String userId,
      @NonNull final DashboardDefinitionRestDto dashboardDefinitionDto) {
    return createNewDashboard(userId, dashboardDefinitionDto, IdGenerator.getNextId());
  }

  @Override
  public IdResponseDto createNewDashboard(
      @NonNull final String userId,
      @NonNull final DashboardDefinitionRestDto dashboardDefinitionDto,
      @NonNull final String id) {
    log.debug("Writing new dashboard to Elasticsearch");
    dashboardDefinitionDto.setOwner(userId);
    dashboardDefinitionDto.setName(
        Optional.ofNullable(dashboardDefinitionDto.getName()).orElse(DEFAULT_DASHBOARD_NAME));
    dashboardDefinitionDto.setLastModifier(userId);
    dashboardDefinitionDto.setId(id);
    return saveDashboard(dashboardDefinitionDto);
  }

  @Override
  public IdResponseDto saveDashboard(
      @NonNull final DashboardDefinitionRestDto dashboardDefinitionDto) {
    dashboardDefinitionDto.setCreated(LocalDateUtil.getCurrentDateTime());
    dashboardDefinitionDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    final String dashboardId = dashboardDefinitionDto.getId();
    try {
      final IndexResponse indexResponse =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  i ->
                      i.optimizeIndex(esClient, DASHBOARD_INDEX_NAME)
                          .id(dashboardId)
                          .document(dashboardDefinitionDto)
                          .refresh(Refresh.True)));

      if (!indexResponse.result().equals(Result.Created)) {
        final String message =
            "Could not write dashboard to Elasticsearch. "
                + "Maybe the connection to Elasticsearch was lost?";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (final IOException e) {
      final String errorMessage = "Could not create dashboard.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    log.debug("Dashboard with id [{}] has successfully been created.", dashboardId);
    return new IdResponseDto(dashboardId);
  }

  @Override
  public void updateDashboard(final DashboardDefinitionUpdateDto dashboard, final String id) {
    log.debug("Updating dashboard with id [{}] in Elasticsearch", id);
    try {
      final UpdateResponse<DashboardDefinitionUpdateDto> updateResponse =
          esClient.update(
              new OptimizeUpdateRequestBuilderES<
                      DashboardDefinitionUpdateDto, DashboardDefinitionUpdateDto>()
                  .optimizeIndex(esClient, DASHBOARD_INDEX_NAME)
                  .id(id)
                  .doc(dashboard)
                  .refresh(Refresh.True)
                  .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                  .build(),
              DashboardDefinitionUpdateDto.class);

      if (!updateResponse.shards().failures().isEmpty()) {
        log.error(
            "Was not able to update dashboard with id [{}] and name [{}].",
            id,
            dashboard.getName());
        throw new OptimizeRuntimeException("Was not able to update dashboard!");
      }
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Was not able to update dashboard with id [%s] and name [%s].",
              id, dashboard.getName());
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final ElasticsearchException e) {
      final String errorMessage =
          String.format(
              "Was not able to update dashboard with id [%s] and name [%s]. Dashboard does not exist!",
              id, dashboard.getName());
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  @Override
  public void removeReportFromDashboards(final String reportId) {
    final String updateItem = String.format("report from dashboard with report ID [%s]", reportId);
    log.info("Removing {}}.", updateItem);

    final Script removeReportFromDashboardScript =
        Script.of(
            i ->
                i.lang(ScriptLanguage.Painless)
                    .params("idToRemove", JsonData.of(reportId))
                    .source(
                        "ctx._source.tiles.removeIf(report -> report.id.equals(params.idToRemove))"));

    final Query query =
        Query.of(
            q ->
                q.nested(
                    n ->
                        n.path(TILES)
                            .scoreMode(ChildScoreMode.None)
                            .query(
                                Query.of(
                                    qq ->
                                        qq.term(
                                            t ->
                                                t.field(TILES + "." + DashboardIndex.REPORT_ID)
                                                    .value(reportId))))));

    taskRepositoryES.tryUpdateByQueryRequest(
        updateItem, removeReportFromDashboardScript, query, DASHBOARD_INDEX_NAME);
  }

  @Override
  public void deleteDashboardsOfCollection(final String collectionId) {

    taskRepositoryES.tryDeleteByQueryRequest(
        Query.of(q -> q.term(t -> t.field(COLLECTION_ID).value(collectionId))),
        String.format("dashboards of collection with ID [%s]", collectionId),
        true,
        DASHBOARD_INDEX_NAME);
  }

  @Override
  public void deleteDashboard(final String dashboardId) {
    log.debug("Deleting dashboard with id [{}]", dashboardId);
    final DeleteResponse deleteResponse;
    try {
      deleteResponse =
          esClient.delete(
              OptimizeDeleteRequestBuilderES.of(
                  d ->
                      d.optimizeIndex(esClient, DASHBOARD_INDEX_NAME)
                          .id(dashboardId)
                          .refresh(Refresh.True)));
    } catch (final IOException e) {
      final String reason = String.format("Could not delete dashboard with id [%s].", dashboardId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.result().equals(Result.Deleted)) {
      final String message =
          String.format(
              "Could not delete dashboard with id [%s]. Dashboard does not exist. "
                  + "Maybe it was already deleted by someone else?",
              dashboardId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

  @Override
  public void deleteManagementDashboard() {

    taskRepositoryES.tryDeleteByQueryRequest(
        Query.of(q -> q.term(t -> t.field(MANAGEMENT_DASHBOARD).value(true))),
        "Management Dashboard",
        true,
        DASHBOARD_INDEX_NAME);
  }
}
