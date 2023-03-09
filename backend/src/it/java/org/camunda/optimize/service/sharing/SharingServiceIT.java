/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.sharing;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.test.optimize.CollectionClient.PRIVATE_COLLECTION_ID;

public class SharingServiceIT extends AbstractSharingIT {

  @Test
  public void dashboardWithoutReportsShare() {
    // given
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    DashboardDefinitionRestDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    // then
    List<DashboardReportTileDto> reportLocations = dashboardShareDto.getTiles();
    assertThat(reportLocations).isEmpty();
  }

  @Test
  public void dashboardsWithDuplicateReportsAreShared() {
    // given
    String reportId = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId);

    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    DashboardDefinitionRestDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    // then
    List<DashboardReportTileDto> reportLocation = dashboardShareDto.getTiles();
    assertThat(reportLocation).hasSize(2);
    assertThat(reportLocation.get(0).getPosition().getX()).isNotEqualTo(reportLocation.get(1).getPosition().getX());
  }

  @Test
  public void individualReportShareIsNotAffectedByDashboard() {
    // given
    String reportId = createReportWithInstance();
    String reportId2 = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(dashboardId);

    String reportShareId = addShareForReport(reportId2);

    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDeleteDashboardShareRequest(dashboardShareId)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    HashMap<?, ?> evaluatedReportAsMap = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest(reportShareId)
      .execute(HashMap.class, Response.Status.OK.getStatusCode());

    // then
    assertReportData(reportId2, evaluatedReportAsMap);
  }

  @Test
  public void dashboardReportShareCanBeEvaluatedWithAdditionalFilter() {
    // given a report with a completed instance
    String reportId = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    Response response = sharingClient.getDashboardShareResponse(dashboardId);
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
  public void dashboardCombinedReportShareCanBeEvaluatedWithAdditionalFilter() {
    // given
    deployAndStartSimpleProcess(DEFAULT_DEFINITION_KEY);
    final String reportId = reportClient.createSingleReport(null, PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    final String combinedReportId = reportClient.createNewCombinedReport(reportId);
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, combinedReportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    Response response = sharingClient.getDashboardShareResponse(dashboardId);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    importAllEngineEntitiesFromScratch();

    // when no filters are applied
    AuthorizedCombinedReportEvaluationResponseDto<Double> evaluationResult =
      evaluateDashboardCombinedReport(combinedReportId, dashboardShareId, null);

    // then the instance is included in response
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(1L);

    // when running instance filter is applied
    evaluationResult = evaluateDashboardCombinedReport(combinedReportId, dashboardShareId, runningInstanceFilter());

    // then the instance is filtered from result
    assertThat(evaluationResult.getResult().getInstanceCount()).isZero();
  }

  @Test
  public void collectionSharedReportCanBeEvaluated() {
    // given
    final String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();

    String reportId = createReportWithInstance(DEFAULT_DEFINITION_KEY, collectionId);
    String reportShareId = addShareForReport(reportId);

    final AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluationResult =
      embeddedOptimizeExtension.getRequestExecutor()
        .buildEvaluateSharedReportRequest(reportShareId)
        .withoutAuthentication()
        .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>>() {
        });

    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(evaluationResult.getResult().getData()).hasSize(1);
  }

  @Test
  public void collectionDashboardReportShareCanBeEvaluatedWithAdditionalFilter() {
    // given
    final String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();

    String reportId = createReportWithInstance(DEFAULT_DEFINITION_KEY, collectionId);
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    addReportToDashboard(dashboardId, reportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    Response response = sharingClient.getDashboardShareResponse(dashboardId);
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
    String dashboardId = addEmptyDashboardToOptimize();
    String externalResourceReportId = "";
    addExternalReportToDashboard(dashboardId, externalResourceReportId);

    // when
    DashboardShareRestDto share = createDashboardShareDto(dashboardId);
    Response response = sharingClient.createDashboardShareResponse(share);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void canEvaluateEveryReportOfSharedDashboard() {
    // given
    String reportId = createReportWithInstance();
    String reportId2 = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    Response response = sharingClient.getDashboardShareResponse(dashboardId);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // then
    HashMap<?, ?> evaluatedReportAsMap = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId)
      .execute(HashMap.class, Response.Status.OK.getStatusCode());

    assertReportData(reportId, evaluatedReportAsMap);

    evaluatedReportAsMap = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId2)
      .execute(HashMap.class, Response.Status.OK.getStatusCode());

    assertReportData(reportId2, evaluatedReportAsMap);
  }

  @Test
  public void paginationParamsCanBeUsedForSharedDashboardRawDataReportEvaluation() {
    // given
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getSimpleBpmnDiagram());
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      firstInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();
    String rawDataReportId = createReport(
      firstInstance.getProcessDefinitionKey(),
      Collections.singletonList(ALL_VERSIONS)
    );
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, rawDataReportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
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
    assertThat(evaluationResult.getResult().getData().get(0).getProcessInstanceId()).isEqualTo(secondInstance.getId());

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
    assertThat(evaluationResult.getResult().getData().get(0).getProcessInstanceId()).isEqualTo(firstInstance.getId());
  }

  @Test
  public void paginationParamsCannotBeUsedForNonRawDataSharedDashboardReportEvaluation() {
    // given a report that isn't of raw data type
    final SingleProcessReportDefinitionRequestDto reportDef = createSingleProcessReport(
      "someKey",
      Collections.singletonList(ALL_VERSIONS),
      ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE
    );
    final String reportId = reportClient.createSingleProcessReport(reportDef);
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    Response findShareResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildFindShareForDashboardRequest(dashboardId)
      .execute();

    assertThat(findShareResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when we get the first page of results
    PaginationRequestDto paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(1);
    paginationRequestDto.setOffset(0);
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId, paginationRequestDto, null)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void paginationParamsCanBeUsedForSharedRawDataReportEvaluation() {
    // given
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getSimpleBpmnDiagram());
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      firstInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();
    String rawDataReportId = createReport(
      firstInstance.getProcessDefinitionKey(),
      Collections.singletonList(ALL_VERSIONS)
    );
    String reportShareId = addShareForReport(rawDataReportId);

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
    assertThat(evaluationResult.getResult().getData().get(0).getProcessInstanceId()).isEqualTo(secondInstance.getId());

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
    assertThat(evaluationResult.getResult().getData().get(0).getProcessInstanceId()).isEqualTo(firstInstance.getId());
  }

  @Test
  public void paginationParamsCannotBeUsedForNonRawDataSharedReportEvaluation() {
    // given a report that isn't of raw data type
    final SingleProcessReportDefinitionRequestDto reportDef = createSingleProcessReport(
      "someKey",
      Collections.singletonList(ALL_VERSIONS),
      ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE
    );
    final String reportId = reportClient.createSingleProcessReport(reportDef);
    String reportShareId = addShareForReport(reportId);

    // when
    PaginationRequestDto paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(10);
    paginationRequestDto.setOffset(10);
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSharedReportRequest(reportShareId, paginationRequestDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void sharedDashboardReportsCannotBeEvaluateViaSharedReport() {
    // given
    String reportId = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    addShareForDashboard(dashboardId);

    // then
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest(reportId)
      .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void evaluateUnknownReportOfSharedDashboardThrowsError() {
    // given
    String reportId = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    String dashboardShareId = addShareForDashboard(dashboardId);

    // then
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedDashboardReportRequest(dashboardShareId, FAKE_REPORT_ID)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void evaluateUnknownSharedDashboardThrowsError() {
    // given
    String reportId = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    addShareForDashboard(dashboardId);

    // then
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedDashboardReportRequest("fakedashboardshareid", reportId)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void reportSharesOfDashboardsAreIndependent() {
    // given
    String reportId = createReportWithInstance();
    String reportId2 = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(dashboardId);

    String dashboardId2 = addEmptyDashboardToOptimize();
    assertThat(dashboardId).isNotEqualTo(dashboardId2);
    addReportToDashboard(dashboardId2, reportId, reportId2);
    String dashboardShareId2 = addShareForDashboard(dashboardId2);

    // when
    DashboardDefinitionRestDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId2);

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

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId2);

    assertThat(dashboardShareDto.getTiles()).hasSize(2);
  }

  @Test
  public void removingReportFromDashboardRemovesRespectiveShare() {
    // given
    String reportId = createReportWithInstance();
    String dashboardId = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    DashboardDefinitionRestDto fullBoard = new DashboardDefinitionRestDto();
    fullBoard.setId(dashboardId);
    dashboardClient.updateDashboard(dashboardId, fullBoard);

    // then
    DashboardDefinitionRestDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    assertThat(dashboardShareDto.getTiles()).isEmpty();
  }

  @Test
  public void updateDashboardShareMoreThanOnce() {
    // given
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);
    addShareForDashboard(dashboardWithReport);
    DashboardDefinitionRestDto fullBoard = new DashboardDefinitionRestDto();
    fullBoard.setId(dashboardWithReport);
    dashboardClient.updateDashboard(dashboardWithReport, fullBoard);

    // when
    Response response = dashboardClient.updateDashboard(dashboardWithReport, fullBoard);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void updateDashboard_addReportAndMetaData() {
    // given
    final String shouldBeIgnoredString = "shouldNotBeUpdated";
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);

    DashboardReportTileDto dashboardTileDto = new DashboardReportTileDto();
    final String reportId = reportClient.createSingleProcessReport(new SingleProcessReportDefinitionRequestDto());
    dashboardTileDto.setId(reportId);
    dashboardTileDto.setType(DashboardTileType.OPTIMIZE_REPORT);
    dashboardTileDto.setConfiguration("testConfiguration");
    DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setTiles(Collections.singletonList(dashboardTileDto));
    dashboard.setId(shouldBeIgnoredString);
    dashboard.setLastModifier("shouldNotBeUpdatedManually");
    dashboard.setName("MyDashboard");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    dashboard.setCreated(shouldBeIgnoredDate);
    dashboard.setLastModified(shouldBeIgnoredDate);
    dashboard.setOwner(shouldBeIgnoredString);

    // when
    dashboardClient.updateDashboard(dashboardId, dashboard);
    DashboardDefinitionRestDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    // then
    assertThat(dashboardShareDto.getTiles()).hasSize(1);
    DashboardReportTileDto retrievedLocation = dashboardShareDto.getTiles().get(0);
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
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);
    final String reportId =
      reportClient.createSingleReport(PRIVATE_COLLECTION_ID, PROCESS, "A_KEY", DEFAULT_TENANTS);

    // when
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportId));

    // then
    DashboardDefinitionRestDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);
    assertThat(dashboardShareDto.getTiles()).extracting(DashboardReportTileDto::getId).containsExactly(reportId);

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
    String dashboardId =
      dashboardClient.createDashboard(
        PRIVATE_COLLECTION_ID,
        Arrays.asList(reportIdToStayInDashboard, reportIdToBeRemovedFromDashboard)
      );
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportIdToStayInDashboard));

    // then
    DashboardDefinitionRestDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);
    assertThat(dashboardShareDto.getTiles())
      .extracting(DashboardReportTileDto::getId)
      .containsExactly(reportIdToStayInDashboard);

    // and then
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportIdToStayInDashboard)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // and then
    response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportIdToBeRemovedFromDashboard)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void addingReportToDashboardAddsRespectiveShare() {
    // given
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    String reportId = createReportWithInstance();
    addReportToDashboard(dashboardId, reportId);

    // then
    DashboardDefinitionRestDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    assertThat(dashboardShareDto.getTiles()).hasSize(1);
  }

  @Test
  public void unsharedDashboardRemovesNotStandaloneReportShares() {
    // given
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);
    String reportShareId = addShareForReport(reportId);

    DashboardDefinitionRestDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);
    String dashboardReportShareId = dashboardShareDto.getTiles().get(0).getId();

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
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    HashMap<?, ?> evaluatedReportAsMap = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest(reportShareId)
      .execute(HashMap.class, Response.Status.OK.getStatusCode());

    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void cannotEvaluateDashboardOverReportsEndpoint() {
    // given
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedReportRequest(dashboardShareId)
        .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void createNewFakeReportShareThrowsError() {

    // when
    Response response = sharingClient.createReportShareResponse(createReportShare());

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void cantCreateDashboardReportShare() {
    // given
    ReportShareRestDto sharingDto = new ReportShareRestDto();
    sharingDto.setReportId(FAKE_REPORT_ID);

    // when
    Response response = sharingClient.createReportShareResponse(sharingDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void createNewFakeDashboardShareThrowsError() {
    // given
    DashboardShareRestDto dashboardShare = new DashboardShareRestDto();
    dashboardShare.setDashboardId(FAKE_REPORT_ID);

    // when
    Response response = sharingClient.createDashboardShareResponse(dashboardShare);

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void shareIsNotCreatedForSameResourceTwice() {
    // given
    String reportId = createReportWithInstance();
    ReportShareRestDto share = createReportShare(reportId);

    // when
    Response response = sharingClient.createReportShareResponse(share);

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    String id =
      response.readEntity(String.class);
    assertThat(id).isNotNull();

    response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildShareReportRequest(share)
        .execute();

    assertThat(id).isEqualTo(response.readEntity(String.class));
  }

  @Test
  public void cantEvaluateNotExistingReportShare() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest(FAKE_REPORT_ID)
      .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void cantEvaluateNotExistingDashboardShare() {
    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedDashboardRequest(FAKE_REPORT_ID)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void cantEvaluateUnsharedReport() {
    // given
    String reportId = createReportWithInstance();
    String shareId = addShareForReport(reportId);

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
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void newIdGeneratedAfterDeletion() {
    String reportId = createReportWithInstance();
    String reportShareId = addShareForReport(reportId);

    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDeleteReportShareRequest(reportShareId)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    String newShareId = addShareForReport(reportId);
    assertThat(reportShareId).isNotEqualTo(newShareId);
  }

  @Test
  public void sharesRemovedOnReportDeletion() {
    // given
    String reportId = createReportWithInstance();
    addShareForReport(reportId);

    // when
    reportClient.deleteReport(reportId);

    // then
    ReportShareRestDto share = getShareForReport(reportId);
    assertThat(share).isNull();
  }

  @Test
  public void canEvaluateSharedReportWithoutAuthentication() {
    // given
    String reportId = createReportWithInstance();

    String shareId = addShareForReport(reportId);

    // when
    HashMap<?, ?> evaluatedReportAsMap = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest(shareId)
      .execute(HashMap.class, Response.Status.OK.getStatusCode());

    // then
    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void canCheckDashboardSharingStatus() {
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);

    addShareForDashboard(dashboardWithReport);

    ShareSearchRequestDto statusRequest = new ShareSearchRequestDto();
    statusRequest.getDashboards().add(dashboardWithReport);
    statusRequest.getReports().add(reportId);

    String dashboardWithReport2 = createDashboardWithReport(reportId);
    statusRequest.getDashboards().add(dashboardWithReport2);
    // when

    ShareSearchResultResponseDto result =
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
    String reportId = createReportWithInstance();
    addShareForReport(reportId);

    ShareSearchRequestDto statusRequest = new ShareSearchRequestDto();
    statusRequest.getReports().add(reportId);
    String reportId2 = createReportWithInstance();
    statusRequest.getReports().add(reportId2);

    // when
    ShareSearchResultResponseDto result =
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
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    // when
    String reportShareId = addShareForReport(reportId);

    // then
    assertThat(reportShareId).isNotNull();

    ReportShareRestDto findApiReport = getShareForReport(reportId);
    assertThat(dashboardShareId).isNotEqualTo(findApiReport.getId());
  }

  @Test
  public void errorMessageIsWellStructured() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess("aProcess");
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    reportData.setView(null);
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportData);

    String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    // when
    DashboardDefinitionRestDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    ReportEvaluationException errorResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(
        dashboardShareId,
        dashboardShareDto.getTiles().get(0).getId()
      )
      .execute(ReportEvaluationException.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    AbstractSharingIT.assertErrorFields(errorResponse);
  }

  @Test
  public void shareDashboard_containsUnauthorizedSingleReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    String authorizedReportId = createReportWithInstance("processDefinition1");
    String unauthorizedReportId = createReportWithInstance("processDefinition2");
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, authorizedReportId, unauthorizedReportId);

    authorizationClient.grantSingleResourceAuthorizationForKermit(
      "processDefinition1",
      RESOURCE_TYPE_PROCESS_DEFINITION
    );

    // when I want to share the dashboard as kermit and kermit has no access to report 2
    DashboardShareRestDto share = createDashboardShareDto(dashboardId);
    Response response = sharingClient.createDashboardShareResponse(share, KERMIT_USER, KERMIT_USER);

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
      collectionId, new CollectionRoleRequestDto(new IdentityDto(KERMIT_USER, IdentityType.USER), RoleType.VIEWER)
    );
    final String reportId =
      reportClient.createSingleReport(collectionId, PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    final String combinedReportId = reportClient.createCombinedReport(
      collectionId,
      Collections.singletonList(reportId)
    );
    final String dashboardId =
      dashboardClient.createDashboard(collectionId, Collections.singletonList(combinedReportId));

    // when
    DashboardShareRestDto share = createDashboardShareDto(dashboardId);
    Response response = sharingClient.createDashboardShareResponse(share, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private AuthorizedCombinedReportEvaluationResponseDto<Double> evaluateDashboardCombinedReport(
    final String combinedReportId,
    final String dashboardShareId,
    final AdditionalProcessReportEvaluationFilterDto filterDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, combinedReportId, null, filterDto)
      .withoutAuthentication()
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResponseDto<Double>>() {
      });
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluateDashboardReport(final String rawDataReportId,
                                                                                                                final String dashboardShareId) {
    return evaluateDashboardReport(rawDataReportId, dashboardShareId, null, null);
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluateDashboardReport(
    final String rawDataReportId,
    final String dashboardShareId,
    final AdditionalProcessReportEvaluationFilterDto filters) {
    return evaluateDashboardReport(rawDataReportId, dashboardShareId, null, filters);
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluateDashboardReport(
    final String rawDataReportId,
    final String dashboardShareId,
    final PaginationRequestDto paginationRequestDto) {
    return evaluateDashboardReport(rawDataReportId, dashboardShareId, paginationRequestDto, null);
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluateDashboardReport(
    final String rawDataReportId, final String dashboardShareId, final PaginationRequestDto paginationRequestDto,
    final AdditionalProcessReportEvaluationFilterDto filters) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, rawDataReportId, paginationRequestDto, filters)
      .withoutAuthentication()
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>>() {
      });
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluateReportWithPagination(
    final String reportShareId, final PaginationRequestDto paginationRequestDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSharedReportRequest(reportShareId, paginationRequestDto)
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>>() {
      });
  }

  private AdditionalProcessReportEvaluationFilterDto variableInFilter() {
    return new AdditionalProcessReportEvaluationFilterDto(
      ProcessFilterBuilder
        .filter()
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
      ProcessFilterBuilder.filter().candidateGroups().operator(IN).id("someId").add().buildList());
  }

  private void addExternalReportToDashboard(String dashboardId, String reportId) {
    DashboardDefinitionRestDto fullBoard = new DashboardDefinitionRestDto();
    fullBoard.setId(dashboardId);
    DashboardReportTileDto dashboardTile = new DashboardReportTileDto();
    dashboardTile.setId(reportId);
    dashboardTile.setType(DashboardTileType.EXTERNAL_URL);
    PositionDto position = new PositionDto();
    position.setX(0);
    position.setY(0);
    dashboardTile.setPosition(position);
    fullBoard.setTiles(List.of(dashboardTile));

    dashboardClient.updateDashboard(dashboardId, fullBoard);
  }

}
