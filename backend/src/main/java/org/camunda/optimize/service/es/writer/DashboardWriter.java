/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
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
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.DashboardIndex.COLLECTION_ID;
import static org.camunda.optimize.service.es.schema.index.DashboardIndex.MANAGEMENT_DASHBOARD;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class DashboardWriter {
  private static final String DEFAULT_DASHBOARD_NAME = "New Dashboard";

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public IdResponseDto createNewDashboard(@NonNull final String userId,
                                          @NonNull final DashboardDefinitionRestDto dashboardDefinitionDto) {
    return createNewDashboard(userId, dashboardDefinitionDto, IdGenerator.getNextId());
  }

  public IdResponseDto createNewDashboard(@NonNull final String userId,
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

  public IdResponseDto saveDashboard(@NonNull final DashboardDefinitionRestDto dashboardDefinitionDto) {
    dashboardDefinitionDto.setCreated(LocalDateUtil.getCurrentDateTime());
    dashboardDefinitionDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    final String dashboardId = dashboardDefinitionDto.getId();
    try {
      IndexRequest request = new IndexRequest(DASHBOARD_INDEX_NAME)
        .id(dashboardId)
        .source(objectMapper.writeValueAsString(dashboardDefinitionDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(DocWriteResponse.Result.CREATED)) {
        String message = "Could not write dashboard to Elasticsearch. " +
          "Maybe the connection to Elasticsearch was lost?";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create dashboard.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    log.debug("Dashboard with id [{}] has successfully been created.", dashboardId);
    return new IdResponseDto(dashboardId);
  }

  public void updateDashboard(DashboardDefinitionUpdateDto dashboard, String id) {
    log.debug("Updating dashboard with id [{}] in Elasticsearch", id);
    try {
      UpdateRequest request = new UpdateRequest()
        .index(DASHBOARD_INDEX_NAME)
        .id(id)
        .doc(objectMapper.writeValueAsString(dashboard), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      UpdateResponse updateResponse = esClient.update(request);

      if (updateResponse.getShardInfo().getFailed() > 0) {
        log.error(
          "Was not able to update dashboard with id [{}] and name [{}].",
          id,
          dashboard.getName()
        );
        throw new OptimizeRuntimeException("Was not able to update dashboard!");
      }
    } catch (IOException e) {
      String errorMessage = String.format(
        "Was not able to update dashboard with id [%s] and name [%s].",
        id,
        dashboard.getName()
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update dashboard with id [%s] and name [%s]. Dashboard does not exist!",
        id,
        dashboard.getName()
      );
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  public void removeReportFromDashboards(String reportId) {
    final String updateItem = String.format("report from dashboard with report ID [%s]", reportId);
    log.info("Removing {}}.", updateItem);

    Script removeReportFromDashboardScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.tiles.removeIf(report -> report.id.equals(params.idToRemove))",
      Collections.singletonMap("idToRemove", reportId)
    );

    NestedQueryBuilder query = QueryBuilders.nestedQuery(
      DashboardIndex.TILES,
      QueryBuilders.termQuery(DashboardIndex.TILES + "." + DashboardIndex.REPORT_ID, reportId),
      ScoreMode.None
    );

    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
      esClient,
      updateItem,
      removeReportFromDashboardScript,
      query,
      DASHBOARD_INDEX_NAME
    );
  }

  public void deleteDashboardsOfCollection(String collectionId) {
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
      esClient,
      QueryBuilders.termQuery(COLLECTION_ID, collectionId),
      String.format("dashboards of collection with ID [%s]", collectionId),
      true,
      DASHBOARD_INDEX_NAME
    );
  }

  public void deleteDashboard(String dashboardId) {
    log.debug("Deleting dashboard with id [{}]", dashboardId);
    DeleteRequest request = new DeleteRequest(DASHBOARD_INDEX_NAME)
      .id(dashboardId)
      .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete dashboard with id [%s].", dashboardId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
      String message =
        String.format("Could not delete dashboard with id [%s]. Dashboard does not exist." +
                        "Maybe it was already deleted by someone else?", dashboardId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

  public void deleteManagementDashboard() {
    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
      esClient,
      QueryBuilders.termQuery(MANAGEMENT_DASHBOARD, true),
      "Management Dashboard",
      true,
      DASHBOARD_INDEX_NAME
    );
  }

}
