/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.sharing;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public abstract class AbstractSharingIT extends AbstractIT {

  protected static final String FAKE_REPORT_ID = "fake";

  protected String createReportWithInstance() {
    return createReportWithInstance("aProcess");
  }

  protected String createReportWithInstance(final String definitionKey) {
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess(definitionKey);
    importAllEngineEntitiesFromScratch();
    return createReport(processInstance.getProcessDefinitionKey(), Collections.singletonList(ALL_VERSIONS));
  }

  protected String createReportWithInstance(String definitionKey, final String collectionId) {
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess(definitionKey);
    importAllEngineEntitiesFromScratch();
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto = createSingleProcessReport(
      processInstance.getProcessDefinitionKey(),
      Collections.singletonList("ALL"),
      ProcessReportDataType.RAW_DATA
    );
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  protected String createReport(String definitionKey, List<String> versions) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto = createSingleProcessReport(
      definitionKey,
      versions,
      ProcessReportDataType.RAW_DATA
    );
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  protected SingleProcessReportDefinitionRequestDto createSingleProcessReport(final String definitionKey,
                                                                              final List<String> versions,
                                                                              final ProcessReportDataType type) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definitionKey)
      .setProcessDefinitionVersions(versions)
      .setReportDataType(type)
      .build();
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportData);
    return singleProcessReportDefinitionDto;
  }

  public static void assertErrorFields(ReportEvaluationException errorMessage) {
    assertThat(errorMessage.getReportDefinition()).isNotNull();
    ReportDefinitionDto<?> reportDefinitionDto = errorMessage.getReportDefinition().getDefinitionDto();
    if (reportDefinitionDto instanceof SingleProcessReportDefinitionRequestDto) {
      SingleProcessReportDefinitionRequestDto singleProcessReport =
        (SingleProcessReportDefinitionRequestDto) reportDefinitionDto;
      assertThat(singleProcessReport.getData()).isNotNull();
      assertThat(singleProcessReport.getName()).isNotNull();
      assertThat(singleProcessReport.getId()).isNotNull();
    } else if (reportDefinitionDto instanceof SingleDecisionReportDefinitionRequestDto) {
      SingleDecisionReportDefinitionRequestDto singleDecisionReport =
        (SingleDecisionReportDefinitionRequestDto) reportDefinitionDto;
      assertThat(singleDecisionReport.getData()).isNotNull();
      assertThat(singleDecisionReport.getName()).isNotNull();
      assertThat(singleDecisionReport.getId()).isNotNull();
    } else {
      throw new OptimizeIntegrationTestException("Evaluation exception should return single report definition!");
    }
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess(String definitionKey) {
    return deployAndStartSimpleProcessWithVariables(definitionKey, new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(String definitionKey,
                                                                            Map<String, Object> variables) {
    BpmnModelInstance processModel = getSimpleBpmnDiagram(definitionKey);
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected String createDashboardWithReport(String reportId) {
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);
    return dashboardId;
  }

  protected void addReportToDashboard(String dashboardId, String... reportIds) {
    DashboardDefinitionRestDto fullBoard = new DashboardDefinitionRestDto();
    fullBoard.setId(dashboardId);

    List<DashboardReportTileDto> reports = new ArrayList<>();

    if (reportIds != null) {
      int i = 0;
      for (String reportId : reportIds) {
        DashboardReportTileDto dashboardTile = new DashboardReportTileDto();
        dashboardTile.setId(reportId);
        dashboardTile.setType(DashboardTileType.OPTIMIZE_REPORT);
        PositionDto position = new PositionDto();
        position.setX(i);
        position.setY(i);
        dashboardTile.setPosition(position);
        reports.add(dashboardTile);
        i = i + 2;
      }
    }

    fullBoard.setTiles(reports);

    dashboardClient.updateDashboard(dashboardId, fullBoard);
  }

  protected String addEmptyDashboardToOptimize() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  protected String addShareForDashboard(String dashboardId) {
    DashboardShareRestDto share = createDashboardShareDto(dashboardId);
    Response response = sharingClient.createDashboardShareResponse(share);

    return response.readEntity(IdResponseDto.class).getId();
  }

  ReportShareRestDto createReportShare() {
    return createReportShare(FAKE_REPORT_ID);
  }

  protected ReportShareRestDto createReportShare(String reportId) {
    ReportShareRestDto sharingDto = new ReportShareRestDto();
    sharingDto.setReportId(reportId);
    return sharingDto;
  }

  protected DashboardShareRestDto createDashboardShareDto(String dashboardId) {
    DashboardShareRestDto sharingDto = new DashboardShareRestDto();
    sharingDto.setDashboardId(dashboardId);
    return sharingDto;
  }

  protected String addShareForReport(String reportId) {
    ReportShareRestDto share = createReportShare(reportId);
    Response response = sharingClient.createReportShareResponse(share);

    return response.readEntity(IdResponseDto.class).getId();
  }

  private Response findShareForReport(String reportId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildFindShareForReportRequest(reportId)
      .execute();
  }

  protected ReportShareRestDto getShareForReport(String reportId) {
    Response response = findShareForReport(reportId);
    return response.readEntity(ReportShareRestDto.class);
  }

  protected void assertReportData(String reportId, HashMap<?, ?> evaluatedReportAsMap) {
    assertThat(evaluatedReportAsMap).isNotNull();
    assertThat(evaluatedReportAsMap.get("id")).isEqualTo(reportId);
    assertThat(evaluatedReportAsMap.get("data")).isNotNull();
  }

}
