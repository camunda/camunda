/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.service.db.writer.SharingWriter;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class SharingWriterOS implements SharingWriter {

  private final OptimizeOpenSearchClient osClient;

  public ReportShareRestDto saveReportShare(final ReportShareRestDto createSharingDto) {
//    log.debug("Writing new report share to OpenSearch");
//    String id = IdGenerator.getNextId();
//    createSharingDto.setId(id);
//
//    final IndexRequest.Builder<ReportShareRestDto> indexRequestBuilder =
//      new IndexRequest.Builder<ReportShareRestDto>()
//      .index(REPORT_SHARE_INDEX_NAME) // TODO Check that prefix works
//      .id(createSharingDto.getId())
//      .document(createSharingDto);
//
//    IndexResponse indexResponse = osClient.index(indexRequestBuilder);
//
//    if (!indexResponse.result().equals(Result.Created)) {
//      String message = "Could not write report share to OpenSearch.";
//      log.error(message);
//      throw new OptimizeRuntimeException(message);
//    }
//
//    log.debug("report share with id [{}] for resource [{}] has been created", id, createSharingDto.getReportId());
//    return createSharingDto;
    //todo will be handled in the OPT-7376
    return null;
  }

  public DashboardShareRestDto saveDashboardShare(final DashboardShareRestDto createSharingDto) {
//    log.debug("Writing new dashboard share to OpenSearch");
//    String id = IdGenerator.getNextId();
//    createSharingDto.setId(id);
//
//    final IndexRequest.Builder<DashboardShareRestDto> indexRequestBuilder =
//      new IndexRequest.Builder<DashboardShareRestDto>()
//        .index(DASHBOARD_SHARE_INDEX_NAME) // TODO Check that prefix works
//        .id(createSharingDto.getId())
//        .document(createSharingDto);
//
//    IndexResponse indexResponse = osClient.index(indexRequestBuilder);
//
//    if (!indexResponse.result().equals(Result.Created)) {
//      String message = "Could not write dashboard share to OpenSearch";
//      log.error(message);
//      throw new OptimizeRuntimeException(message);
//    }
//
//    log.debug(
//      "dashboard share with id [{}] for resource [{}] has been created",
//      id,
//      createSharingDto.getDashboardId()
//    );
//    return createSharingDto;
    //todo will be handled in the OPT-7376
    return null;
  }

  public void updateDashboardShare(final DashboardShareRestDto updatedShare) {
//    String id = updatedShare.getId();
//
//    final IndexRequest.Builder<DashboardShareRestDto> indexRequestBuilder =
//      new IndexRequest.Builder<DashboardShareRestDto>()
//        .index(DASHBOARD_SHARE_INDEX_NAME) // TODO Check that prefix works
//        .id(updatedShare.getId())
//        .document(updatedShare);
//
//    IndexResponse indexResponse = osClient.index(indexRequestBuilder);
//
//    if (!indexResponse.result().equals(Result.Created) &&
//        !indexResponse.result().equals(Result.Updated)) {
//      String message = String.format(
//        "Was not able to update dashboard share with id [%s] for resource [%s].",
//        id,
//        updatedShare.getDashboardId()
//      );
//      log.error(message);
//      throw new OptimizeRuntimeException(message);
//    }
//    log.debug("dashboard share with id [{}] for resource [{}] has been updated", id, updatedShare.getDashboardId());
    //todo will be handled in the OPT-7376
  }

  public void deleteReportShare(final String shareId) {
//    log.debug("Deleting report share with id [{}]", shareId);
//    final DeleteResponse deleteResponse = osClient.delete(
//      REPORT_SHARE_INDEX_NAME,
//      shareId
//    );
//
//    if (!deleteResponse.result().equals(Result.Deleted)) {
//      String message =
//        String.format("Could not delete report share with id [%s]. Report share does not exist." +
//                        "Maybe it was already deleted by someone else?", shareId);
//      log.error(message);
//      throw new NotFoundException(message);
//    }
    //todo will be handled in the OPT-7376
  }

  public void deleteDashboardShare(final String shareId) {
//    log.debug("Deleting dashboard share with id [{}]", shareId);
//    final DeleteResponse deleteResponse = osClient.delete(
//      DASHBOARD_SHARE_INDEX_NAME,
//      shareId
//    );
//
//    if (!deleteResponse.result().equals(Result.Deleted)) {
//      String message =
//        String.format("Could not delete dashboard share with id [%s]. Dashboard share does not exist." +
//                        "Maybe it was already deleted by someone else?", shareId);
//      log.error(message);
//      throw new NotFoundException(message);
//    }
//  }
    //todo will be handled in the OPT-7376
  }

}
