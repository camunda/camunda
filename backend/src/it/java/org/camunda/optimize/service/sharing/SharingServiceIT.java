/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.test.optimize.CollectionClient.PRIVATE_COLLECTION_ID;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class SharingServiceIT extends AbstractSharingIT {

  @Test
  public void dashboardWithoutReportsShare() {
    // given
    final String dashboardId = addEmptyDashboardToOptimize();
    final String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    final DashboardDefinitionRestDto dashboardShareDto =
        sharingClient.evaluateDashboard(dashboardShareId);

    // then
    final List<DashboardReportTileDto> reportLocations = dashboardShareDto.getTiles();
    assertThat(reportLocations).isEmpty();
  }

  @Test
  public void dashboardsWithDuplicateReportsAreShared() {
    // given
    final String reportId = createReportWithInstance();
    final String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId);

    final String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    final DashboardDefinitionRestDto dashboardShareDto =
        sharingClient.evaluateDashboard(dashboardShareId);

    // then
    final List<DashboardReportTileDto> reportLocation = dashboardShareDto.getTiles();
    assertThat(reportLocation).hasSize(2);
    assertThat(reportLocation.get(0).getPosition().getX())
        .isNotEqualTo(reportLocation.get(1).getPosition().getX());
  }

  @Test
  public void individualReportShareIsNotAffectedByDashboard() {
    // given
    final String reportId = createReportWithInstance();
    final String reportId2 = createReportWithInstance();
    final String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    final String dashboardShareId = addShareForDashboard(dashboardId);

    final String reportShareId = addShareForReport(reportId2);

    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteDashboardShareRequest(dashboardShareId)
            .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    final HashMap<?, ?> evaluatedReportAsMap =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(reportShareId)
            .execute(HashMap.class, Response.Status.OK.getStatusCode());

    // then
    assertReportData(reportId2, evaluatedReportAsMap);
  }

  @Test
  public void dashboardReportShareCanBeEvaluatedWithAdditionalFilter() {
    // given a report with a completed instance
    final String reportId = createReportWithInstance();
    final String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);
    final String dashboardShareId = addShareForDashboard(dashboardId);

    final Response response = sharingClient.getDashboardShareResponse(dashboardId);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when no filters are applied
    AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluationResult =
        evaluateDashboardReport(reportId, dashboardShareId);

    // then all instances are included in response
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);

    // when running instance filter is applied
    evaluationResult = evaluateDashboardReport(reportId, dashboardShareId, runningInstanceFilter());

    // then instances is filtered from result
    assertThat(evaluationResult.getResult().getInstanceCount()).isZero();
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);

    // when variable instance filter is applied that is not part of report
    evaluationResult = evaluateDashboardReport(reportId, dashboardShareId, variableInFilter());

    // then filter is ignored and instance is part of result
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);

    // when assignee filter is applied
    evaluationResult = evaluateDashboardReport(reportId, dashboardShareId, assigneeFilter());

    // then instance is filtered from result
    assertThat(evaluationResult.getResult().getInstanceCount()).isZero();
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);

    // when candidate filter is applied
    evaluationResult = evaluateDashboardReport(reportId, dashboardShareId, candidateFilter());

    // then instance is filtered from result
    assertThat(evaluationResult.getResult().getInstanceCount()).isZero();
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);
  }

  @Test
  @Tag(OPENSEARCH_SINGLE_TEST_FAIL_OK)
  public void dashboardCombinedReportShareCanBeEvaluatedWithAdditionalFilter() {
    // given
    deployAndStartSimpleProcess(DEFAULT_DEFINITION_KEY);
    final String reportId =
        reportClient.createSingleReport(null, PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    final String combinedReportId = reportClient.createNewCombinedReport(reportId);
    final String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, combinedReportId);
    final String dashboardShareId = addShareForDashboard(dashboardId);

    final Response response = sharingClient.getDashboardShareResponse(dashboardId);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    importAllEngineEntitiesFromScratch();

    // when no filters are applied
    AuthorizedCombinedReportEvaluationResponseDto<Double> evaluationResult =
        evaluateDashboardCombinedReport(combinedReportId, dashboardShareId, null);

    // then the instance is included in response
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(1L);

    // when running instance filter is applied
    evaluationResult =
        evaluateDashboardCombinedReport(
            combinedReportId, dashboardShareId, runningInstanceFilter());

    // then the instance is filtered from result
    assertThat(evaluationResult.getResult().getInstanceCount()).isZero();
  }

  @Test
  public void collectionSharedReportCanBeEvaluated() {
    // given
    final String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();

    final String reportId = createReportWithInstance(DEFAULT_DEFINITION_KEY, collectionId);
    final String reportShareId = addShareForReport(reportId);

    final AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
        evaluationResult =
            embeddedOptimizeExtension
                .getRequestExecutor()
                .buildEvaluateSharedReportRequest(reportShareId)
                .withoutAuthentication()
                .execute(new TypeReference<>() {});

    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(evaluationResult.getResult().getData()).hasSize(1);
  }

  @Test
  public void collectionDashboardReportShareCanBeEvaluatedWithAdditionalFilter() {
    // given
    final String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();

    final String reportId = createReportWithInstance(DEFAULT_DEFINITION_KEY, collectionId);
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    addReportToDashboard(dashboardId, reportId);
    final String dashboardShareId = addShareForDashboard(dashboardId);

    final Response response = sharingClient.getDashboardShareResponse(dashboardId);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when no filters are applied
    AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluationResult =
        evaluateDashboardReport(reportId, dashboardShareId);

    // then all instances are included in response
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);

    // when running instance filter is applied
    evaluationResult = evaluateDashboardReport(reportId, dashboardShareId, runningInstanceFilter());

    // then instances is filtered from result
    assertThat(evaluationResult.getResult().getInstanceCount()).isZero();
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);

    // when variable instance filter is applied that is not part of report
    evaluationResult = evaluateDashboardReport(reportId, dashboardShareId, variableInFilter());

    // then filter is ignored and instance is part of result
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);

    // when assignee filter is applied
    evaluationResult = evaluateDashboardReport(reportId, dashboardShareId, assigneeFilter());

    // then instance is filtered from result
    assertThat(evaluationResult.getResult().getInstanceCount()).isZero();
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);

    // when candidate filter is applied
    evaluationResult = evaluateDashboardReport(reportId, dashboardShareId, candidateFilter());

    // then instance is filtered from result
    assertThat(evaluationResult.getResult().getInstanceCount()).isZero();
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);
  }

  @Test
  public void shareDashboardWithExternalResourceReport() {
    // given
    final String dashboardId = addEmptyDashboardToOptimize();
    final String externalResourceReportId = "";
    addExternalReportToDashboard(dashboardId, externalResourceReportId);

    // when
    final DashboardShareRestDto share = createDashboardShareDto(dashboardId);
    final Response response = sharingClient.createDashboardShareResponse(share);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void canEvaluateEveryReportOfSharedDashboard() {
    // given
    final String reportId = createReportWithInstance();
    final String reportId2 = createReportWithInstance();
    final String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    final String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    final Response response = sharingClient.getDashboardShareResponse(dashboardId);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // then
    HashMap<?, ?> evaluatedReportAsMap =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId)
            .execute(HashMap.class, Response.Status.OK.getStatusCode());

    assertReportData(reportId, evaluatedReportAsMap);

    evaluatedReportAsMap =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId2)
            .execute(HashMap.class, Response.Status.OK.getStatusCode());

    assertReportData(reportId2, evaluatedReportAsMap);
  }

  @Test
  public void paginationParamsCanBeUsedForSharedDashboardRawDataReportEvaluation() {
    // given
    final ProcessInstanceEngineDto firstInstance =
        engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSimpleBpmnDiagram());
    final ProcessInstanceEngineDto secondInstance =
        engineIntegrationExtension.startProcessInstance(firstInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();
    final String rawDataReportId =
        createReport(
            firstInstance.getProcessDefinitionKey(), Collections.singletonList(ALL_VERSIONS));
    final String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, rawDataReportId);
    final String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildFindShareForDashboardRequest(dashboardId)
            .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when we get the first page of results
    PaginationRequestDto paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(1);
    paginationRequestDto.setOffset(0);
    AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluationResult =
        evaluateDashboardReport(rawDataReportId, dashboardShareId, paginationRequestDto);

    // then we get just the second instance
    assertThat(evaluationResult.getResult().getPagination())
        .isEqualTo(PaginationDto.fromPaginationRequest(paginationRequestDto));
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getData()).hasSize(1);
    assertThat(evaluationResult.getResult().getData().get(0).getProcessInstanceId())
        .isEqualTo(secondInstance.getId());

    // when we get the second page of results
    paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(1);
    paginationRequestDto.setOffset(1);
    evaluationResult =
        evaluateDashboardReport(rawDataReportId, dashboardShareId, paginationRequestDto);

    // then we get just the first instance
    assertThat(evaluationResult.getResult().getPagination())
        .isEqualTo(PaginationDto.fromPaginationRequest(paginationRequestDto));
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getData()).hasSize(1);
    assertThat(evaluationResult.getResult().getData().get(0).getProcessInstanceId())
        .isEqualTo(firstInstance.getId());
  }

  @Test
  public void paginationParamsCannotBeUsedForNonRawDataSharedDashboardReportEvaluation() {
    // given a report that isn't of raw data type
    final SingleProcessReportDefinitionRequestDto reportDef =
        createSingleProcessReport(
            "someKey",
            Collections.singletonList(ALL_VERSIONS),
            ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE);
    final String reportId = reportClient.createSingleProcessReport(reportDef);
    final String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);
    final String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    final Response findShareResponse =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildFindShareForDashboardRequest(dashboardId)
            .execute();

    assertThat(findShareResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when we get the first page of results
    final PaginationRequestDto paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(1);
    paginationRequestDto.setOffset(0);
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest(
                dashboardShareId, reportId, paginationRequestDto, null)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void paginationParamsCanBeUsedForSharedRawDataReportEvaluation() {
    // given
    final ProcessInstanceEngineDto firstInstance =
        engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSimpleBpmnDiagram());
    final ProcessInstanceEngineDto secondInstance =
        engineIntegrationExtension.startProcessInstance(firstInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();
    final String rawDataReportId =
        createReport(
            firstInstance.getProcessDefinitionKey(), Collections.singletonList(ALL_VERSIONS));
    final String reportShareId = addShareForReport(rawDataReportId);

    // when we get the first page of results
    PaginationRequestDto paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(1);
    paginationRequestDto.setOffset(0);
    AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluationResult =
        evaluateReportWithPagination(reportShareId, paginationRequestDto);

    // then we get just the second instance
    assertThat(evaluationResult.getResult().getPagination())
        .isEqualTo(PaginationDto.fromPaginationRequest(paginationRequestDto));
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getData()).hasSize(1);
    assertThat(evaluationResult.getResult().getData().get(0).getProcessInstanceId())
        .isEqualTo(secondInstance.getId());

    // when we get the second page of results
    paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(1);
    paginationRequestDto.setOffset(1);
    evaluationResult = evaluateReportWithPagination(reportShareId, paginationRequestDto);

    // then we get just the first instance
    assertThat(evaluationResult.getResult().getPagination())
        .isEqualTo(PaginationDto.fromPaginationRequest(paginationRequestDto));
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getData()).hasSize(1);
    assertThat(evaluationResult.getResult().getData().get(0).getProcessInstanceId())
        .isEqualTo(firstInstance.getId());
  }

  @Test
  public void paginationParamsCannotBeUsedForNonRawDataSharedReportEvaluation() {
    // given a report that isn't of raw data type
    final SingleProcessReportDefinitionRequestDto reportDef =
        createSingleProcessReport(
            "someKey",
            Collections.singletonList(ALL_VERSIONS),
            ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE);
    final String reportId = reportClient.createSingleProcessReport(reportDef);
    final String reportShareId = addShareForReport(reportId);

    // when
    final PaginationRequestDto paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(10);
    paginationRequestDto.setOffset(10);
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(reportShareId, paginationRequestDto)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void sharedDashboardReportsCannotBeEvaluateViaSharedReport() {
    // given
    final String reportId = createReportWithInstance();
    final String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    addShareForDashboard(dashboardId);

    // then
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(reportId)
            .execute();

    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void evaluateUnknownReportOfSharedDashboardThrowsError() {
    // given
    final String reportId = createReportWithInstance();
    final String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    final String dashboardShareId = addShareForDashboard(dashboardId);

    // then
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest(dashboardShareId, FAKE_REPORT_ID)
            .execute();

    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void evaluateUnknownSharedDashboardThrowsError() {
    // given
    final String reportId = createReportWithInstance();
    final String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    addShareForDashboard(dashboardId);

    // then
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest("fakedashboardshareid", reportId)
            .execute();

    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void reportSharesOfDashboardsAreIndependent() {
    // given
    final String reportId = createReportWithInstance();
    final String reportId2 = createReportWithInstance();
    final String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    final String dashboardShareId = addShareForDashboard(dashboardId);

    final String dashboardId2 = addEmptyDashboardToOptimize();
    assertThat(dashboardId).isNotEqualTo(dashboardId2);
    addReportToDashboard(dashboardId2, reportId, reportId2);
    final String dashboardShareId2 = addShareForDashboard(dashboardId2);

    // when
    DashboardDefinitionRestDto dashboardShareDto =
        sharingClient.evaluateDashboard(dashboardShareId2);

    assertThat(dashboardShareDto.getTiles()).hasSize(2);

    Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteDashboardShareRequest(dashboardShareId)
            .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // then
    response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(dashboardShareId)
            .execute();

    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId2);

    assertThat(dashboardShareDto.getTiles()).hasSize(2);
  }

  @Test
  public void removingReportFromDashboardRemovesRespectiveShare() {
    // given
    final String reportId = createReportWithInstance();
    final String dashboardId = createDashboardWithReport(reportId);
    final String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    final DashboardDefinitionRestDto fullBoard = new DashboardDefinitionRestDto();
    fullBoard.setId(dashboardId);
    dashboardClient.updateDashboard(dashboardId, fullBoard);

    // then
    final DashboardDefinitionRestDto dashboardShareDto =
        sharingClient.evaluateDashboard(dashboardShareId);

    assertThat(dashboardShareDto.getTiles()).isEmpty();
  }

  @Test
  public void updateDashboardShareMoreThanOnce() {
    // given
    final String reportId = createReportWithInstance();
    final String dashboardWithReport = createDashboardWithReport(reportId);
    addShareForDashboard(dashboardWithReport);
    final DashboardDefinitionRestDto fullBoard = new DashboardDefinitionRestDto();
    fullBoard.setId(dashboardWithReport);
    dashboardClient.updateDashboard(dashboardWithReport, fullBoard);

    // when
    final Response response = dashboardClient.updateDashboard(dashboardWithReport, fullBoard);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void updateDashboard_addReportAndMetaData() {
    // given
    final String shouldBeIgnoredString = "shouldNotBeUpdated";
    final String dashboardId = addEmptyDashboardToOptimize();
    final String dashboardShareId = addShareForDashboard(dashboardId);

    final DashboardReportTileDto dashboardTileDto = new DashboardReportTileDto();
    final String reportId =
        reportClient.createSingleProcessReport(new SingleProcessReportDefinitionRequestDto());
    dashboardTileDto.setId(reportId);
    dashboardTileDto.setType(DashboardTileType.OPTIMIZE_REPORT);
    dashboardTileDto.setConfiguration("testConfiguration");
    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setTiles(Collections.singletonList(dashboardTileDto));
    dashboard.setId(shouldBeIgnoredString);
    dashboard.setLastModifier("shouldNotBeUpdatedManually");
    dashboard.setName("MyDashboard");
    final OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    dashboard.setCreated(shouldBeIgnoredDate);
    dashboard.setLastModified(shouldBeIgnoredDate);
    dashboard.setOwner(shouldBeIgnoredString);

    // when
    dashboardClient.updateDashboard(dashboardId, dashboard);
    final DashboardDefinitionRestDto dashboardShareDto =
        sharingClient.evaluateDashboard(dashboardShareId);

    // then
    assertThat(dashboardShareDto.getTiles()).hasSize(1);
    final DashboardReportTileDto retrievedLocation = dashboardShareDto.getTiles().get(0);
    assertThat(retrievedLocation.getId()).isEqualTo(reportId);
    assertThat(retrievedLocation.getConfiguration()).isEqualTo("testConfiguration");
    assertThat(dashboardShareDto.getId()).isEqualTo(dashboardId);
    assertThat(dashboardShareDto.getCreated()).isNotEqualTo(shouldBeIgnoredDate);
    assertThat(dashboardShareDto.getLastModified()).isNotEqualTo(shouldBeIgnoredDate);
    assertThat(dashboardShareDto.getName()).isEqualTo("MyDashboard");
    assertThat(dashboardShareDto.getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @Test
  public void updateDashboard_addReportAndEvaluateShare() {
    // given
    final String dashboardId = addEmptyDashboardToOptimize();
    final String dashboardShareId = addShareForDashboard(dashboardId);
    final String reportId =
        reportClient.createSingleReport(PRIVATE_COLLECTION_ID, PROCESS, "A_KEY", DEFAULT_TENANTS);

    // when
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportId));

    // then
    final DashboardDefinitionRestDto dashboardShareDto =
        sharingClient.evaluateDashboard(dashboardShareId);
    assertThat(dashboardShareDto.getTiles())
        .extracting(DashboardReportTileDto::getId)
        .containsExactly(reportId);

    // and then
    final ReportDefinitionDto<?> authorizedEvaluationResultDto =
        sharingClient.evaluateReportForSharedDashboard(dashboardShareId, reportId);
    assertThat(authorizedEvaluationResultDto.getId()).isEqualTo(reportId);
  }

  @Test
  public void updateDashboard_removeReportAndEvaluateDashboardShare() {
    // given
    final String reportIdToStayInDashboard =
        reportClient.createSingleReport(PRIVATE_COLLECTION_ID, PROCESS, "A_KEY", DEFAULT_TENANTS);
    final String reportIdToBeRemovedFromDashboard =
        reportClient.createSingleReport(PRIVATE_COLLECTION_ID, PROCESS, "A_KEY", DEFAULT_TENANTS);
    final String dashboardId =
        dashboardClient.createDashboard(
            PRIVATE_COLLECTION_ID,
            Arrays.asList(reportIdToStayInDashboard, reportIdToBeRemovedFromDashboard));
    final String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    dashboardClient.updateDashboardWithReports(
        dashboardId, Collections.singletonList(reportIdToStayInDashboard));

    // then
    final DashboardDefinitionRestDto dashboardShareDto =
        sharingClient.evaluateDashboard(dashboardShareId);
    assertThat(dashboardShareDto.getTiles())
        .extracting(DashboardReportTileDto::getId)
        .containsExactly(reportIdToStayInDashboard);

    // and then
    Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportIdToStayInDashboard)
            .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // and then
    response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest(
                dashboardShareId, reportIdToBeRemovedFromDashboard)
            .execute();
    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void addingReportToDashboardAddsRespectiveShare() {
    // given
    final String dashboardId = addEmptyDashboardToOptimize();
    final String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    final String reportId = createReportWithInstance();
    addReportToDashboard(dashboardId, reportId);

    // then
    final DashboardDefinitionRestDto dashboardShareDto =
        sharingClient.evaluateDashboard(dashboardShareId);

    assertThat(dashboardShareDto.getTiles()).hasSize(1);
  }

  @Test
  public void unsharedDashboardRemovesNotStandaloneReportShares() {
    // given
    final String reportId = createReportWithInstance();
    final String dashboardWithReport = createDashboardWithReport(reportId);
    final String dashboardShareId = addShareForDashboard(dashboardWithReport);
    final String reportShareId = addShareForReport(reportId);

    final DashboardDefinitionRestDto dashboardShareDto =
        sharingClient.evaluateDashboard(dashboardShareId);
    final String dashboardReportShareId = dashboardShareDto.getTiles().get(0).getId();

    // when
    Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteDashboardShareRequest(dashboardShareId)
            .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // then
    response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(dashboardReportShareId)
            .execute();
    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    final HashMap<?, ?> evaluatedReportAsMap =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(reportShareId)
            .execute(HashMap.class, Response.Status.OK.getStatusCode());

    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void cannotEvaluateDashboardOverReportsEndpoint() {
    // given
    final String reportId = createReportWithInstance();
    final String dashboardWithReport = createDashboardWithReport(reportId);
    final String dashboardShareId = addShareForDashboard(dashboardWithReport);

    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(dashboardShareId)
            .execute();

    // then
    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void createNewFakeReportShareThrowsError() {

    // when
    final Response response = sharingClient.createReportShareResponse(createReportShare());

    // then
    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void cantCreateDashboardReportShare() {
    // given
    final ReportShareRestDto sharingDto = new ReportShareRestDto();
    sharingDto.setReportId(FAKE_REPORT_ID);

    // when
    final Response response = sharingClient.createReportShareResponse(sharingDto);

    // then
    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void createNewFakeDashboardShareThrowsError() {
    // given
    final DashboardShareRestDto dashboardShare = new DashboardShareRestDto();
    dashboardShare.setDashboardId(FAKE_REPORT_ID);

    // when
    final Response response = sharingClient.createDashboardShareResponse(dashboardShare);

    // then the status code is okay
    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void shareIsNotCreatedForSameResourceTwice() {
    // given
    final String reportId = createReportWithInstance();
    final ReportShareRestDto share = createReportShare(reportId);

    // when
    Response response = sharingClient.createReportShareResponse(share);

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final String id = response.readEntity(String.class);
    assertThat(id).isNotNull();

    response =
        embeddedOptimizeExtension.getRequestExecutor().buildShareReportRequest(share).execute();

    assertThat(id).isEqualTo(response.readEntity(String.class));
  }

  @Test
  public void cantEvaluateNotExistingReportShare() {
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(FAKE_REPORT_ID)
            .execute();

    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void cantEvaluateNotExistingDashboardShare() {
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(FAKE_REPORT_ID)
            .execute();

    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void cantEvaluateUnsharedReport() {
    // given
    final String reportId = createReportWithInstance();
    final String shareId = addShareForReport(reportId);

    Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(shareId)
            .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when
    response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteReportShareRequest(shareId)
            .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // then
    response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(shareId)
            .execute();
    assertThat(response.getStatus())
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void newIdGeneratedAfterDeletion() {
    final String reportId = createReportWithInstance();
    final String reportShareId = addShareForReport(reportId);

    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteReportShareRequest(reportShareId)
            .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    final String newShareId = addShareForReport(reportId);
    assertThat(reportShareId).isNotEqualTo(newShareId);
  }

  @Test
  public void sharesRemovedOnReportDeletion() {
    // given
    final String reportId = createReportWithInstance();
    addShareForReport(reportId);

    // when
    reportClient.deleteReport(reportId);

    // then
    final ReportShareRestDto share = getShareForReport(reportId);
    assertThat(share).isNull();
  }

  @Test
  public void canEvaluateSharedReportWithoutAuthentication() {
    // given
    final String reportId = createReportWithInstance();

    final String shareId = addShareForReport(reportId);

    // when
    final HashMap<?, ?> evaluatedReportAsMap =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(shareId)
            .execute(HashMap.class, Response.Status.OK.getStatusCode());

    // then
    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void canCheckDashboardSharingStatus() {
    final String reportId = createReportWithInstance();
    final String dashboardWithReport = createDashboardWithReport(reportId);

    addShareForDashboard(dashboardWithReport);

    final ShareSearchRequestDto statusRequest = new ShareSearchRequestDto();
    statusRequest.getDashboards().add(dashboardWithReport);
    statusRequest.getReports().add(reportId);

    final String dashboardWithReport2 = createDashboardWithReport(reportId);
    statusRequest.getDashboards().add(dashboardWithReport2);
    // when

    final ShareSearchResultResponseDto result =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildCheckSharingStatusRequest(statusRequest)
            .execute(ShareSearchResultResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(result.getDashboards()).hasSize(2);
    assertThat(result.getDashboards().get(dashboardWithReport)).isEqualTo(true);
    assertThat(result.getDashboards().get(dashboardWithReport2)).isEqualTo(false);

    assertThat(result.getReports()).hasSize(1);
    assertThat(result.getReports().get(reportId)).isEqualTo(false);
  }

  @Test
  public void canCheckReportSharingStatus() {
    final String reportId = createReportWithInstance();
    addShareForReport(reportId);

    final ShareSearchRequestDto statusRequest = new ShareSearchRequestDto();
    statusRequest.getReports().add(reportId);
    final String reportId2 = createReportWithInstance();
    statusRequest.getReports().add(reportId2);

    // when
    final ShareSearchResultResponseDto result =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildCheckSharingStatusRequest(statusRequest)
            .execute(ShareSearchResultResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(result.getReports()).hasSize(2);
    assertThat(result.getReports().get(reportId)).isEqualTo(true);
    assertThat(result.getReports().get(reportId2)).isEqualTo(false);
  }

  @Test
  public void canCreateReportShareIfDashboardIsShared() {
    // given
    final String reportId = createReportWithInstance();
    final String dashboardWithReport = createDashboardWithReport(reportId);
    final String dashboardShareId = addShareForDashboard(dashboardWithReport);

    // when
    final String reportShareId = addShareForReport(reportId);

    // then
    assertThat(reportShareId).isNotNull();

    final ReportShareRestDto findApiReport = getShareForReport(reportId);
    assertThat(dashboardShareId).isNotEqualTo(findApiReport.getId());
  }

  @Test
  public void errorMessageIsWellStructured() {
    // given
    final ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess("aProcess");
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData =
        TemplatedProcessReportDataBuilder.createReportData()
            .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
            .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
            .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
            .build();
    reportData.setView(null);
    final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
        new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportData);

    final String reportId =
        reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    final String dashboardWithReport = createDashboardWithReport(reportId);
    final String dashboardShareId = addShareForDashboard(dashboardWithReport);

    // when
    final DashboardDefinitionRestDto dashboardShareDto =
        sharingClient.evaluateDashboard(dashboardShareId);

    final ReportEvaluationException errorResponse =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest(
                dashboardShareId, dashboardShareDto.getTiles().get(0).getId())
            .execute(ReportEvaluationException.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    AbstractSharingIT.assertErrorFields(errorResponse);
  }

  @Test
  public void shareDashboard_containsUnauthorizedSingleReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String authorizedReportId = createReportWithInstance("processDefinition1");
    final String unauthorizedReportId = createReportWithInstance("processDefinition2");
    final String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, authorizedReportId, unauthorizedReportId);

    authorizationClient.grantSingleResourceAuthorizationForKermit(
        "processDefinition1", RESOURCE_TYPE_PROCESS_DEFINITION);

    // when I want to share the dashboard as kermit and kermit has no access to report 2
    final DashboardShareRestDto share = createDashboardShareDto(dashboardId);
    final Response response =
        sharingClient.createDashboardShareResponse(share, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void shareDashboard_containsUnauthorizedCombinedReport() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    final String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    collectionClient.addRolesToCollection(
        collectionId,
        new CollectionRoleRequestDto(
            new IdentityDto(KERMIT_USER, IdentityType.USER), RoleType.VIEWER));
    final String reportId =
        reportClient.createSingleReport(
            collectionId, PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    final String combinedReportId =
        reportClient.createCombinedReport(collectionId, Collections.singletonList(reportId));
    final String dashboardId =
        dashboardClient.createDashboard(collectionId, Collections.singletonList(combinedReportId));

    // when
    final DashboardShareRestDto share = createDashboardShareDto(dashboardId);
    final Response response =
        sharingClient.createDashboardShareResponse(share, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private AuthorizedCombinedReportEvaluationResponseDto<Double> evaluateDashboardCombinedReport(
      final String combinedReportId,
      final String dashboardShareId,
      final AdditionalProcessReportEvaluationFilterDto filterDto) {
    return embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedDashboardReportRequest(
            dashboardShareId, combinedReportId, null, filterDto)
        .withoutAuthentication()
        .execute(new TypeReference<>() {});
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
      evaluateDashboardReport(final String rawDataReportId, final String dashboardShareId) {
    return evaluateDashboardReport(rawDataReportId, dashboardShareId, null, null);
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
      evaluateDashboardReport(
          final String rawDataReportId,
          final String dashboardShareId,
          final AdditionalProcessReportEvaluationFilterDto filters) {
    return evaluateDashboardReport(rawDataReportId, dashboardShareId, null, filters);
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
      evaluateDashboardReport(
          final String rawDataReportId,
          final String dashboardShareId,
          final PaginationRequestDto paginationRequestDto) {
    return evaluateDashboardReport(rawDataReportId, dashboardShareId, paginationRequestDto, null);
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
      evaluateDashboardReport(
          final String rawDataReportId,
          final String dashboardShareId,
          final PaginationRequestDto paginationRequestDto,
          final AdditionalProcessReportEvaluationFilterDto filters) {
    return embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedDashboardReportRequest(
            dashboardShareId, rawDataReportId, paginationRequestDto, filters)
        .withoutAuthentication()
        .execute(new TypeReference<>() {});
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
      evaluateReportWithPagination(
          final String reportShareId, final PaginationRequestDto paginationRequestDto) {
    return embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedReportRequest(reportShareId, paginationRequestDto)
        .execute(new TypeReference<>() {});
  }

  private AdditionalProcessReportEvaluationFilterDto variableInFilter() {
    return new AdditionalProcessReportEvaluationFilterDto(
        ProcessFilterBuilder.filter()
            .variable()
            .stringType()
            .name("variableName")
            .values(Collections.singletonList("variableValue"))
            .operator(FilterOperator.IN)
            .add()
            .buildList());
  }

  private AdditionalProcessReportEvaluationFilterDto runningInstanceFilter() {
    return new AdditionalProcessReportEvaluationFilterDto(
        ProcessFilterBuilder.filter().runningInstancesOnly().add().buildList());
  }

  private AdditionalProcessReportEvaluationFilterDto assigneeFilter() {
    return new AdditionalProcessReportEvaluationFilterDto(
        ProcessFilterBuilder.filter().assignee().operator(IN).id("someId").add().buildList());
  }

  private AdditionalProcessReportEvaluationFilterDto candidateFilter() {
    return new AdditionalProcessReportEvaluationFilterDto(
        ProcessFilterBuilder.filter()
            .candidateGroups()
            .operator(IN)
            .id("someId")
            .add()
            .buildList());
  }

  private void addExternalReportToDashboard(final String dashboardId, final String reportId) {
    final DashboardDefinitionRestDto fullBoard = new DashboardDefinitionRestDto();
    fullBoard.setId(dashboardId);
    final DashboardReportTileDto dashboardTile = new DashboardReportTileDto();
    dashboardTile.setId(reportId);
    dashboardTile.setType(DashboardTileType.EXTERNAL_URL);
    final PositionDto position = new PositionDto();
    position.setX(0);
    position.setY(0);
    dashboardTile.setPosition(position);
    fullBoard.setTiles(List.of(dashboardTile));

    dashboardClient.updateDashboard(dashboardId, fullBoard);
  }
}
