/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_SHARE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.REPORT_SHARE_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class SharingWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public ReportShareRestDto saveReportShare(ReportShareRestDto createSharingDto) {
    log.debug("Writing new report share to Elasticsearch");
    String id = IdGenerator.getNextId();
    createSharingDto.setId(id);
    try {
      IndexRequest request = new IndexRequest(REPORT_SHARE_INDEX_NAME)
        .id(id)
        .source(objectMapper.writeValueAsString(createSharingDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write report share to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create report share.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    log.debug("report share with id [{}] for resource [{}] has been created", id, createSharingDto.getReportId());
    return createSharingDto;
  }

  public DashboardShareRestDto saveDashboardShare(DashboardShareRestDto createSharingDto) {
    log.debug("Writing new dashboard share to Elasticsearch");
    String id = IdGenerator.getNextId();
    createSharingDto.setId(id);
    try {
      IndexRequest request = new IndexRequest(DASHBOARD_SHARE_INDEX_NAME)
        .id(id)
        .source(objectMapper.writeValueAsString(createSharingDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write dashboard share to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create dashboard share.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    log.debug(
      "dashboard share with id [{}] for resource [{}] has been created",
      id,
      createSharingDto.getDashboardId()
    );
    return createSharingDto;
  }

  public void updateDashboardShare(DashboardShareRestDto updatedShare) {
    String id = updatedShare.getId();
    try {
      IndexRequest request = new IndexRequest(DASHBOARD_SHARE_INDEX_NAME)
        .id(id)
        .source(objectMapper.writeValueAsString(updatedShare), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED) &&
        !indexResponse.getResult().equals(IndexResponse.Result.UPDATED)) {
        String message = "Could not write dashboard share to Elasticsearch.";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = String.format(
        "Was not able to update dashboard share with id [%s] for resource [%s].",
        id,
        updatedShare.getDashboardId()
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    log.debug("dashboard share with id [{}] for resource [{}] has been updated", id, updatedShare.getDashboardId());
  }

  public void deleteReportShare(String shareId) {
    log.debug("Deleting report share with id [{}]", shareId);
    DeleteRequest request =
      new DeleteRequest(REPORT_SHARE_INDEX_NAME)
        .id(shareId)
        .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete report share with id [%s].", shareId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
      String message =
        String.format("Could not delete report share with id [%s]. Report share does not exist." +
                        "Maybe it was already deleted by someone else?", shareId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

  public void deleteDashboardShare(String shareId) {
    log.debug("Deleting dashboard share with id [{}]", shareId);
    DeleteRequest request = new DeleteRequest(DASHBOARD_SHARE_INDEX_NAME)
      .id(shareId)
      .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete dashboard share with id [%s].", shareId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
      String message =
        String.format("Could not delete dashboard share with id [%s]. Dashboard share does not exist." +
                        "Maybe it was already deleted by someone else?", shareId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

}
