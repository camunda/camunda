/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.type.DashboardType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.Collections;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class DashboardWriter {
  private static final String DEFAULT_DASHBOARD_NAME = "New Dashboard";

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public IdDto createNewDashboardAndReturnId(String userId) {
    log.debug("Writing new dashboard to Elasticsearch");

    String id = IdGenerator.getNextId();
    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setCreated(LocalDateUtil.getCurrentDateTime());
    dashboard.setLastModified(LocalDateUtil.getCurrentDateTime());
    dashboard.setOwner(userId);
    dashboard.setLastModifier(userId);
    dashboard.setName(DEFAULT_DASHBOARD_NAME);
    dashboard.setId(id);

    try {
      IndexRequest request = new IndexRequest(DASHBOARD_TYPE, DASHBOARD_TYPE, id)
        .source(objectMapper.writeValueAsString(dashboard), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write dashboard to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create dashboard.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    log.debug("Dashboard with id [{}] has successfully been created.", id);
    IdDto idDto = new IdDto();
    idDto.setId(id);
    return idDto;
  }

  public void updateDashboard(DashboardDefinitionUpdateDto dashboard, String id) {
    log.debug("Updating dashboard with id [{}] in Elasticsearch", id);
    try {
      UpdateRequest request = new UpdateRequest(DASHBOARD_TYPE, DASHBOARD_TYPE, id)
        .doc(objectMapper.writeValueAsString(dashboard), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      UpdateResponse updateResponse = esClient.update(request, RequestOptions.DEFAULT);

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
    Script removeReportIdFromCombinedReportsScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.reports.removeIf(report -> report.id.equals(params.idToRemove))",
      Collections.singletonMap("idToRemove", reportId)
    );

    NestedQueryBuilder query = QueryBuilders.nestedQuery(
      DashboardType.REPORTS,
      QueryBuilders.termQuery(DashboardType.REPORTS + "." + DashboardType.ID, reportId),
      ScoreMode.None
    );
    UpdateByQueryRequest request = new UpdateByQueryRequest(DASHBOARD_TYPE)
      .setAbortOnVersionConflict(false)
      .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
      .setQuery(query)
      .setScript(removeReportIdFromCombinedReportsScript)
      .setRefresh(true);

    BulkByScrollResponse bulkByScrollResponse;
    try {
      bulkByScrollResponse = esClient.updateByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not remove report with id [%s] from dashboards.", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!bulkByScrollResponse.getBulkFailures().isEmpty()) {
      String errorMessage =
        String.format(
          "Could not remove report id [%s] from dashboard! Error response: %s",
          reportId,
          bulkByScrollResponse.getBulkFailures()
        );
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void deleteDashboard(String dashboardId) {
    log.debug("Deleting dashboard with id [{}]", dashboardId);
    DeleteRequest request = new DeleteRequest(DASHBOARD_TYPE, DASHBOARD_TYPE, dashboardId)
      .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request, RequestOptions.DEFAULT);
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
}
