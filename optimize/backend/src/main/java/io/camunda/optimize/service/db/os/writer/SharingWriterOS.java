/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_SHARE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.REPORT_SHARE_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.writer.SharingWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import jakarta.ws.rs.NotFoundException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class SharingWriterOS implements SharingWriter {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(SharingWriterOS.class);
  private final OptimizeOpenSearchClient osClient;

  public SharingWriterOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public ReportShareRestDto saveReportShare(final ReportShareRestDto createSharingDto) {
    log.debug("Writing new report share to OpenSearch");
    final String id = IdGenerator.getNextId();
    createSharingDto.setId(id);

    final IndexRequest.Builder<ReportShareRestDto> indexRequestBuilder =
        new IndexRequest.Builder<ReportShareRestDto>()
            .index(REPORT_SHARE_INDEX_NAME)
            .id(createSharingDto.getId())
            .refresh(Refresh.True)
            .document(createSharingDto);

    final IndexResponse indexResponse = osClient.index(indexRequestBuilder);

    if (!indexResponse.result().equals(Result.Created)) {
      final String message = "Could not write report share to OpenSearch.";
      log.error(message);
      throw new OptimizeRuntimeException(message);
    }

    log.debug(
        "report share with id [{}] for resource [{}] has been created",
        id,
        createSharingDto.getReportId());
    return createSharingDto;
  }

  @Override
  public DashboardShareRestDto saveDashboardShare(final DashboardShareRestDto createSharingDto) {
    log.debug("Writing new dashboard share to OpenSearch");
    final String id = IdGenerator.getNextId();
    createSharingDto.setId(id);

    final IndexRequest.Builder<DashboardShareRestDto> indexRequestBuilder =
        new IndexRequest.Builder<DashboardShareRestDto>()
            .index(DASHBOARD_SHARE_INDEX_NAME)
            .id(createSharingDto.getId())
            .refresh(Refresh.True)
            .document(createSharingDto);

    final IndexResponse indexResponse = osClient.index(indexRequestBuilder);

    if (!indexResponse.result().equals(Result.Created)) {
      final String message = "Could not write dashboard share to OpenSearch";
      log.error(message);
      throw new OptimizeRuntimeException(message);
    }
    log.debug(
        "Dashboard share with id [{}] for resource [{}] has been created",
        id,
        createSharingDto.getDashboardId());
    return createSharingDto;
  }

  @Override
  public void updateDashboardShare(final DashboardShareRestDto updatedShare) {
    final String id = updatedShare.getId();
    final IndexRequest.Builder<DashboardShareRestDto> indexRequestBuilder =
        new IndexRequest.Builder<DashboardShareRestDto>()
            .index(DASHBOARD_SHARE_INDEX_NAME)
            .id(id)
            .document(updatedShare)
            .refresh(Refresh.True);

    final IndexResponse indexResponse = osClient.index(indexRequestBuilder);

    if (!indexResponse.result().equals(Result.Created)
        && !indexResponse.result().equals(Result.Updated)) {
      final String message =
          String.format(
              "Was not able to update dashboard share with id [%s] for resource [%s].",
              id, updatedShare.getDashboardId());
      log.error(message);
      throw new OptimizeRuntimeException(message);
    }
    log.debug(
        "Dashboard share with id [{}] for resource [{}] has been updated",
        id,
        updatedShare.getDashboardId());
  }

  @Override
  public void deleteReportShare(final String shareId) {
    log.debug("Deleting report share with id [{}]", shareId);

    final DeleteResponse deleteResponse = osClient.delete(REPORT_SHARE_INDEX_NAME, shareId);
    if (!deleteResponse.result().equals(Result.Deleted)) {
      final String message =
          String.format(
              "Could not delete report share with id [%s]. Report share does not exist. "
                  + "Maybe it was already deleted by someone else?",
              shareId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

  @Override
  public void deleteDashboardShare(final String shareId) {
    log.debug("Deleting dashboard share with id [{}]", shareId);

    final DeleteResponse deleteResponse = osClient.delete(DASHBOARD_SHARE_INDEX_NAME, shareId);

    if (!deleteResponse.result().equals(Result.Deleted)) {
      final String errorMessage =
          String.format(
              "Could not delete dashboard share with id [%s]. Dashboard share does not exist. "
                  + "Maybe it was already deleted by someone else?",
              shareId);
      log.error(errorMessage);
      throw new NotFoundException(errorMessage);
    }
  }
}
