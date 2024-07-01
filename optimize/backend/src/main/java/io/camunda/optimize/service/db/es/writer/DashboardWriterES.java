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
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.schema.index.DashboardIndex;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class DashboardWriterES implements DashboardWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

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
      final IndexRequest request =
          new IndexRequest(DASHBOARD_INDEX_NAME)
              .id(dashboardId)
              .source(objectMapper.writeValueAsString(dashboardDefinitionDto), XContentType.JSON)
              .setRefreshPolicy(IMMEDIATE);

      final IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(DocWriteResponse.Result.CREATED)) {
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
      final UpdateRequest request =
          new UpdateRequest()
              .index(DASHBOARD_INDEX_NAME)
              .id(id)
              .doc(objectMapper.writeValueAsString(dashboard), XContentType.JSON)
              .setRefreshPolicy(IMMEDIATE)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      final UpdateResponse updateResponse = esClient.update(request);

      if (updateResponse.getShardInfo().getFailed() > 0) {
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
    } catch (final ElasticsearchStatusException e) {
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
        new Script(
            ScriptType.INLINE,
            Script.DEFAULT_SCRIPT_LANG,
            "ctx._source.tiles.removeIf(report -> report.id.equals(params.idToRemove))",
            Collections.singletonMap("idToRemove", reportId));

    final NestedQueryBuilder query =
        QueryBuilders.nestedQuery(
            TILES,
            QueryBuilders.termQuery(TILES + "." + DashboardIndex.REPORT_ID, reportId),
            ScoreMode.None);

    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
        esClient, updateItem, removeReportFromDashboardScript, query, DASHBOARD_INDEX_NAME);
  }

  @Override
  public void deleteDashboardsOfCollection(final String collectionId) {
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        QueryBuilders.termQuery(COLLECTION_ID, collectionId),
        String.format("dashboards of collection with ID [%s]", collectionId),
        true,
        DASHBOARD_INDEX_NAME);
  }

  @Override
  public void deleteDashboard(final String dashboardId) {
    log.debug("Deleting dashboard with id [{}]", dashboardId);
    final DeleteRequest request =
        new DeleteRequest(DASHBOARD_INDEX_NAME).id(dashboardId).setRefreshPolicy(IMMEDIATE);

    final DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request);
    } catch (final IOException e) {
      final String reason = String.format("Could not delete dashboard with id [%s].", dashboardId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
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
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        QueryBuilders.termQuery(MANAGEMENT_DASHBOARD, true),
        "Management Dashboard",
        true,
        DASHBOARD_INDEX_NAME);
  }
}
