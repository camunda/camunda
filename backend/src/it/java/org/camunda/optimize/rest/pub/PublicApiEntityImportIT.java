/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.pub;

import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.BaseDashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.entities.AbstractExportImportEntityDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.API_IMPORT_OWNER_NAME;
import static org.camunda.optimize.dto.optimize.query.report.single.ViewProperty.RAW_DATA;
import static org.camunda.optimize.service.entities.dashboard.DashboardDefinitionImportIT.getExternalResourceUrls;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;

public class PublicApiEntityImportIT extends AbstractExportImportEntityDefinitionIT {

  private static final String ACCESS_TOKEN = "secret_export_token";
  private String collectionId;

  @BeforeEach
  public void beforeEach() {
    // freeze time to enable assert on report timestamps
    dateFreezer().freezeDateAndReturn();
    setAccessToken();
    collectionId = createCollectionWithScope();
  }

  @Test
  public void importReport() {
    // given
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.getConfiguration().getTableColumns().setIncludeNewVariables(false);
    reportData.getConfiguration()
      .getTableColumns()
      .getExcludedColumns()
      .add(ProcessInstanceDto.Fields.endDate);
    final SingleProcessReportDefinitionRequestDto reportDefToImport = createProcessReportDefinition(reportData);

    // when
    final List<EntityIdResponseDto> importedId = publicApiClient.importEntityAndReturnIds(
      Collections.singleton(createExportDto(reportDefToImport)),
      collectionId,
      ACCESS_TOKEN
    );

    // then
    assertThat(importedId).hasSize(1)
      .extracting(EntityIdResponseDto::getEntityType)
      .singleElement()
      .isEqualTo(EntityType.REPORT);

    final SingleProcessReportDefinitionRequestDto importedReport =
      (SingleProcessReportDefinitionRequestDto) reportClient.getReportById(importedId.get(0).getId());

    assertImportedReport(importedReport, reportDefToImport, collectionId, API_IMPORT_OWNER_NAME);
  }

  @Test
  public void importReportWithInvalidDescription() {
    // given
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    final SingleProcessReportDefinitionRequestDto processReportDefinition = createProcessReportDefinition(reportData);
    processReportDefinition.setDescription("");

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildPublicImportEntityDefinitionsRequest(
        collectionId,
        Sets.newHashSet(createExportDto(processReportDefinition)),
        ACCESS_TOKEN
      )
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void importManagementReportNotPossible() {
    // given
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.setManagementReport(true);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildPublicImportEntityDefinitionsRequest(
        collectionId,
        Sets.newHashSet(createExportDto(createProcessReportDefinition(reportData))),
        ACCESS_TOKEN
      )
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void importMultipleReports() {
    // given
    final SingleProcessReportDefinitionRequestDto report1 = createProcessReportDefinition(
      TemplatedProcessReportDataBuilder
        .createReportData()
        .setProcessDefinitionKey(DEFINITION_KEY)
        .setProcessDefinitionVersion(DEFINITION_VERSION)
        .setReportDataType(ProcessReportDataType.RAW_DATA)
        .build());
    report1.setName("ProcessReport");
    final DecisionReportDataDto report2Data = new DecisionReportDataDto();
    report2Data.setDecisionDefinitionKey(DEFINITION_KEY);
    report2Data.setDecisionDefinitionVersion(DEFINITION_VERSION);
    report2Data.setVisualization(DecisionVisualization.TABLE);
    report2Data.setView(new DecisionViewDto(RAW_DATA));
    report2Data.getConfiguration().getTableColumns().getExcludedColumns().add(DecisionInstanceDto.Fields.engine);
    final SingleDecisionReportDefinitionRequestDto report2 = createDecisionReportDefinition(report2Data);
    report2.setName("DecisionReport");

    // when
    final List<EntityIdResponseDto> importedIds = publicApiClient.importEntityAndReturnIds(
      Set.of(createExportDto(report1), createExportDto(report2)),
      collectionId,
      ACCESS_TOKEN
    );

    // then
    assertThat(importedIds).hasSize(2)
      .extracting(EntityIdResponseDto::getEntityType)
      .containsOnly(EntityType.REPORT);

    final List<SingleReportDefinitionDto<? extends SingleReportDataDto>> importedReports =
      importedIds.stream()
        .map(idResp -> (SingleReportDefinitionDto<? extends SingleReportDataDto>) reportClient.getReportById(idResp.getId()))
        .collect(toList());

    assertThat(importedReports).hasSize(2)
      .extracting(SingleReportDefinitionDto::getName)
      .containsExactlyInAnyOrder("ProcessReport", "DecisionReport");
    assertThat(importedReports)
      .allSatisfy(importedReport -> {
        if ("ProcessReport".equals(importedReport.getName())) {
          assertImportedReport(importedReport, report1, collectionId, API_IMPORT_OWNER_NAME);
        } else {
          assertImportedReport(importedReport, report2, collectionId, API_IMPORT_OWNER_NAME);
        }
      });
  }

  @Test
  public void importDashboard() {
    // given a dashboard with one of each resource type
    final SingleProcessReportDefinitionExportDto processReportExport = createSimpleProcessExportDto();
    final SingleDecisionReportDefinitionExportDto decisionReportExport = createSimpleDecisionExportDto();
    final CombinedProcessReportDefinitionExportDto combinedReportExport = createSimpleCombinedExportDto();
    final String externalResourceUrl = "my.external-resource.com";
    final DashboardDefinitionExportDto dashboardExport =
      createDashboardExportDtoWithResources(Arrays.asList(
        processReportExport.getId(),
        decisionReportExport.getId(),
        combinedReportExport.getId()
      ));
    final JsonObject config = new JsonObject();
    config.addProperty("external", externalResourceUrl);
    dashboardExport.getTiles()
      .add(DashboardReportTileDto.builder()
             .id("")
             .type(DashboardTileType.EXTERNAL_URL)
             .configuration(config.toString())
             .build());

    // when
    final List<EntityIdResponseDto> importedIds =
      publicApiClient.importEntityAndReturnIds(
        Sets.newHashSet(dashboardExport, processReportExport, combinedReportExport, decisionReportExport),
        collectionId,
        ACCESS_TOKEN
      );

    // then
    assertThat(importedIds).hasSize(4)
      .extracting(EntityIdResponseDto::getEntityType)
      .containsExactlyInAnyOrder(EntityType.REPORT, EntityType.REPORT, EntityType.REPORT, EntityType.DASHBOARD);

    final List<ReportDefinitionDto> importedReports = retrieveImportedReports(importedIds);
    final Optional<DashboardDefinitionRestDto> importedDashboard = retrieveImportedDashboard(importedIds);

    // the process report within the combined report is only imported once
    assertThat(importedReports).hasSize(3);

    assertThat(importedDashboard).isPresent().get()
      .extracting(
        BaseDashboardDefinitionDto::getName, BaseDashboardDefinitionDto::getOwner, BaseDashboardDefinitionDto::getLastModifier,
        BaseDashboardDefinitionDto::getCreated, BaseDashboardDefinitionDto::getLastModified,
        BaseDashboardDefinitionDto::getCollectionId, BaseDashboardDefinitionDto::getAvailableFilters,
        BaseDashboardDefinitionDto::isManagementDashboard
      )
      .containsExactly(
        dashboardExport.getName(), API_IMPORT_OWNER_NAME, API_IMPORT_OWNER_NAME, LocalDateUtil.getCurrentDateTime(),
        LocalDateUtil.getCurrentDateTime(), collectionId, dashboardExport.getAvailableFilters(), false
      );

    // the dashboard resources have been imported with correct IDs
    assertThat(importedDashboard.get().getTiles())
      .hasSize(4)
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields(DashboardReportTileDto.Fields.id)
      .containsAll(dashboardExport.getTiles());
    assertThat(importedDashboard.get().getTileIds())
      .hasSize(3)
      .containsAll(importedReports.stream().map(ReportDefinitionDto::getId).collect(toList()));
    assertThat(getExternalResourceUrls(importedDashboard.get()))
      .singleElement()
      .isEqualTo(externalResourceUrl);
  }

  @Test
  public void importMultipleDashboards() {
    // given
    final DashboardDefinitionExportDto dashboard1 = createSimpleDashboardExportDto();
    dashboard1.setName("Dashboard1");
    dashboard1.setId("Id1");
    final DashboardDefinitionExportDto dashboard2 = createSimpleDashboardExportDto();
    dashboard2.setName("Dashboard2");
    dashboard1.setId("Id2");

    // when
    final List<EntityIdResponseDto> importedIds =
      publicApiClient.importEntityAndReturnIds(
        Sets.newHashSet(dashboard1, dashboard2),
        collectionId,
        ACCESS_TOKEN
      );

    // then
    assertThat(importedIds).hasSize(2)
      .extracting(EntityIdResponseDto::getEntityType)
      .containsOnly(EntityType.DASHBOARD);

    assertThat(retrieveImportedDashboards(importedIds))
      .hasSize(2)
      .extracting(DashboardDefinitionRestDto::getName)
      .containsExactlyInAnyOrder("Dashboard1", "Dashboard2");
  }

  @Test
  public void importInvalidEntities() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    SingleProcessReportDefinitionRequestDto reportDef = new SingleProcessReportDefinitionRequestDto();
    final SingleProcessReportDefinitionExportDto exportDto = new SingleProcessReportDefinitionExportDto(reportDef);
    exportDto.setId(null);

    // when
    final Response response = publicApiClient.importEntityAndReturnResponse(
      Collections.singleton(exportDto),
      collectionId,
      ACCESS_TOKEN
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ErrorResponseDto.class).getErrorCode()).isEqualTo("importFileInvalid");
    assertThat(response.readEntity(ErrorResponseDto.class).getDetailedMessage())
      .contains("Could not import entities because the provided file contains invalid OptimizeExportDtos.");
  }

  @Test
  public void importInvalidJson() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildPublicImportEntityDefinitionsRequest(
        Entity.entity("Invalid Json String", MediaType.APPLICATION_JSON_TYPE),
        collectionId,
        ACCESS_TOKEN
      )
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ErrorResponseDto.class).getErrorCode()).isEqualTo("importFileInvalid");
    assertThat(response.readEntity(ErrorResponseDto.class).getDetailedMessage())
      .contains("Could not import entities because the provided file is not a valid list of OptimizeEntityExportDtos.");
  }

  @Test
  public void importEmptyBody() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildPublicImportEntityDefinitionsRequest(
        Entity.entity("", MediaType.APPLICATION_JSON_TYPE),
        collectionId,
        ACCESS_TOKEN
      )
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ErrorResponseDto.class).getErrorCode()).isEqualTo("importFileInvalid");
    assertThat(response.readEntity(ErrorResponseDto.class).getDetailedMessage())
      .contains("Could not import entity because the provided file is null or empty.");
  }

  private String createCollectionWithScope() {
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    createAndSaveDefinition(DefinitionType.DECISION, null);
    final String collectionId = collectionClient.createNewCollectionWithScope(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      DefinitionType.PROCESS,
      DEFINITION_KEY,
      Collections.singletonList(null)
    );
    collectionClient.addScopeEntryToCollection(
      collectionId,
      DEFINITION_KEY,
      DefinitionType.DECISION,
      Collections.singletonList(null)
    );
    return collectionId;
  }

  private void setAccessToken() {
    embeddedOptimizeExtension.getConfigurationService().getOptimizeApiConfiguration().setAccessToken(ACCESS_TOKEN);
  }

}
