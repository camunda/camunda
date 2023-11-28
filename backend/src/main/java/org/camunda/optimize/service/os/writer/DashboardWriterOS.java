/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.service.db.writer.DashboardWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static org.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DashboardWriterOS implements DashboardWriter {

  private final OptimizeOpenSearchClient osClient;

  public IdResponseDto createNewDashboard(@NonNull final String userId,
                                          @NonNull final DashboardDefinitionRestDto dashboardDefinitionDto) {
    return createNewDashboard(userId, dashboardDefinitionDto, IdGenerator.getNextId());
  }

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

  public void updateDashboard(DashboardDefinitionUpdateDto dashboard, String id) {
//    log.debug("Updating dashboard with id [{}] in OpenSearch", id);
//
//    UpdateRequest.Builder<Void, DashboardDefinitionUpdateDto> request = new UpdateRequest.Builder<Void,
//      DashboardDefinitionUpdateDto>()
//      .index(DASHBOARD_INDEX_NAME)
//      .id(id)
//      .doc(dashboard)
//      .refresh(Refresh.True)
//      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
//
//    UpdateResponse<Void> updateResponse = osClient.update(request, e -> {
//      final String errorMessage = String.format(
//        "Was not able to update dashboard with id [%s] and name [%s].",
//        id,
//        dashboard.getName()
//      );
//      log.error(errorMessage, e);
//      return "There were errors while updating dashboard to OS." + e.getMessage();
//    });
//
//    if (updateResponse.shards().failed().intValue() > 0) {
//      log.error(
//        "Was not able to update dashboard with id [{}] and name [{}].",
//        id,
//        dashboard.getName()
//      );
//      throw new OptimizeRuntimeException("Was not able to update dashboard!");
//    }
    //todo will be handled in the OPT-7376
  }

  public void removeReportFromDashboards(String reportId) {
    //        final String updateItem = String.format("report from dashboard with report ID [%s]", reportId);
//        log.info("Removing {}}.", updateItem);
//
//        Script removeReportFromDashboardScript =
//                OpenSearchWriterUtil.createDefaultScriptWithSpecificDtoParams(
//                        "ctx._source.tiles.removeIf(report -> report.id.equals(params.idToRemove))",
//                        Collections.singletonMap("idToRemove", JsonData.of(reportId)));
//
    //todo implement that after I become sure about nester queries
//        NestedQueryBuilder query = QueryBuilders.nestedQuery(
//                TILES,
//                QueryBuilders.termQuery(TILES + "." + DashboardIndex.REPORT_ID, reportId),
//                ScoreMode.None
//        );
//
//        ElasticsearchWriterUtil.tryUpdateByQueryRequest(
//                esClient,
//                updateItem,
//                removeReportFromDashboardScript,
//                query,
//                DASHBOARD_INDEX_NAME
//        );
    //todo will be handled in the OPT-7376
  }

  public void deleteDashboardsOfCollection(String collectionId) {
//        var query = QueryDSL.term(COLLECTION_ID, collectionId);
//
//        OpenSearchWriterUtil.tryDeleteByQueryRequest(
//                osClient,
//                query,
//                String.format("dashboards of collection with ID [%s]", collectionId),
//                true,
//                DASHBOARD_INDEX_NAME
//        );
    //todo will be handled in the OPT-7376
  }

  public void deleteDashboard(String dashboardId) {
    //log.debug("Deleting dashboard with id [{}]", dashboardId);
//
//        DeleteRequest.Builder request = new DeleteRequest.Builder()
//                .index(DASHBOARD_INDEX_NAME)
//                .id(dashboardId)
//                .refresh(Refresh.True);
//
//        DeleteResponse deleteResponse = osClient.delete(request, e -> {
//
//            String reason =
//                    String.format("Could not delete dashboard with id [%s].", dashboardId);
//            log.error(reason, e);
//            throw new OptimizeRuntimeException(reason, e);
//
//        });
//
//        if (!deleteResponse.result().equals(Result.Deleted)) {
//            String message =
//                    String.format("Could not delete dashboard with id [%s]. Dashboard does not exist." +
//                            "Maybe it was already deleted by someone else?", dashboardId);
//            log.error(message);
//            throw new NotFoundException(message);
//        }
    //todo will be handled in the OPT-7376
  }

  public void deleteManagementDashboard() {
//    var query = QueryDSL.term(MANAGEMENT_DASHBOARD, true);
//
//        OpenSearchWriterUtil.tryDeleteByQueryRequest(
//                osClient,
//                query,
//                "Management Dashboard",
//                true,
//                DASHBOARD_INDEX_NAME
//        );
    //todo will be handled in the OPT-7376
  }

}
