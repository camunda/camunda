/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.dashboard;

import com.google.common.collect.Sets;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import org.camunda.optimize.dto.optimize.rest.ImportedIndexMismatchResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.entities.AbstractExportImportIT;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;

public class DashboardImportIT extends AbstractExportImportIT {

  @Test
  public void importDashboard() {
    // given a dashboard with one of each resource type
    dateFreezer().freezeDateAndReturn();
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    createAndSaveDefinition(DefinitionType.DECISION, null);
    final SingleProcessReportDefinitionExportDto processReportExport = createSimpleProcessExportDto();
    final SingleDecisionReportDefinitionExportDto decisionReportExport = createSimpleDecisionExportDto();
    final CombinedProcessReportDefinitionExportDto combinedReportExport = createSimpleCombinedExportDto();
    final String externalResourceId = "my.external-resource.com";
    final DashboardDefinitionExportDto dashboardExport =
      createDashboardExportDtoWithResources(Arrays.asList(
        processReportExport.getId(),
        decisionReportExport.getId(),
        combinedReportExport.getId(),
        externalResourceId
      ));

    // when
    final List<IdResponseDto> importedIds =
      importClient.importEntitiesAndReturnIds(Sets.newHashSet(
        dashboardExport,
        processReportExport,
        combinedReportExport,
        decisionReportExport
      ));

    // then
    assertThat(importedIds).hasSize(4);
    final List<ReportDefinitionDto> importedReports = retrieveImportedReports(importedIds);
    final Optional<DashboardDefinitionRestDto> importedDashboard = retrieveImportedDashboard(importedIds);

    // the process report within the combined report is only imported once
    assertThat(importedReports).hasSize(3);

    assertThat(importedDashboard).isPresent();
    assertThat(importedDashboard.get().getName()).isEqualTo(dashboardExport.getName());
    assertThat(importedDashboard.get().getOwner()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedDashboard.get().getLastModifier()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedDashboard.get().getCreated()).isEqualTo(LocalDateUtil.getCurrentDateTime());
    assertThat(importedDashboard.get().getLastModified()).isEqualTo(LocalDateUtil.getCurrentDateTime());
    assertThat(importedDashboard.get().getCollectionId()).isNull();
    assertThat(importedDashboard.get().getAvailableFilters()).isEqualTo(dashboardExport.getAvailableFilters());

    // the dashboard resources have been imported with correct IDs
    assertThat(importedDashboard.get().getReports())
      .hasSize(4)
      .usingElementComparatorIgnoringFields(ReportLocationDto.Fields.id)
      .containsAll(dashboardExport.getReports());
    assertThat(importedDashboard.get().getReportIds())
      .hasSize(3)
      .containsAll(importedReports.stream().map(ReportDefinitionDto::getId).collect(toList()));
    assertThat(importedDashboard.get().getExternalResourceUrls())
      .singleElement()
      .isEqualTo(externalResourceId);
  }

  @Test
  public void importDashboard_containsSameReportTwice() {
    // given a dashboard with one of each resource type
    dateFreezer().freezeDateAndReturn();
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final SingleProcessReportDefinitionExportDto processReportExport = createSimpleProcessExportDto();
    final DashboardDefinitionExportDto dashboardExport =
      createDashboardExportDtoWithResources(Arrays.asList(
        processReportExport.getId(),
        processReportExport.getId()
      ));

    // when
    final List<IdResponseDto> importedIds =
      importClient.importEntitiesAndReturnIds(Sets.newHashSet(
        dashboardExport,
        processReportExport
      ));

    // then
    assertThat(importedIds).hasSize(2);
    final List<ReportDefinitionDto> importedReports = retrieveImportedReports(importedIds);
    final Optional<DashboardDefinitionRestDto> importedDashboard = retrieveImportedDashboard(importedIds);

    // the report within the dashboard is imported once and referenced correctly in the new dashboard
    assertThat(importedReports).hasSize(1);
    assertThat(importedDashboard).isPresent();
    assertThat(importedDashboard.get().getReports()).hasSize(2);
    assertThat(importedDashboard.get().getReportIds())
      .singleElement()
      .isEqualTo(importedReports.get(0).getId());
  }

  @Test
  public void importDashboard_incorrectIndexVersion() {
    // given
    final DashboardDefinitionExportDto exportedDashboard = createSimpleDashboardExportDto();
    exportedDashboard.setSourceIndexVersion(DashboardIndex.VERSION + 1);

    // when
    final Response response = importClient.importEntity(exportedDashboard);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ImportedIndexMismatchResponseDto.class).getErrorCode())
      .isEqualTo("importIndexVersionMismatch");
    assertThat(response.readEntity(ImportedIndexMismatchResponseDto.class).getMismatchingIndices())
      .hasSize(1)
      .containsExactly(
        ImportIndexMismatchDto.builder()
          .indexName(embeddedOptimizeExtension.getIndexNameService()
                       .getOptimizeIndexNameWithVersion(new DashboardIndex()))
          .sourceIndexVersion(DashboardIndex.VERSION + 1)
          .targetIndexVersion(DashboardIndex.VERSION)
          .build());
  }

  @Test
  public void importIncompleteDashboard_throwsInvalidImportFileException() {
    // given
    final SingleProcessReportDefinitionExportDto reportExport = createSimpleProcessExportDto();
    final DashboardDefinitionExportDto dashboardExport =
      createDashboardExportDtoWithResources(Collections.singletonList(reportExport.getId()));

    // when importing only the dashboard without the required report
    final Response response = importClient.importEntity(dashboardExport);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ErrorResponseDto.class).getErrorCode()).isEqualTo("importFileInvalid");
  }

  @Test
  public void importDashboardIntoCollection() {
    // given
    dateFreezer().freezeDateAndReturn();
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final String collectionId = collectionClient.createNewCollectionWithScope(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      DefinitionType.PROCESS,
      DEFINITION_KEY,
      Collections.singletonList(null)
    );
    final SingleProcessReportDefinitionExportDto reportExport = createSimpleProcessExportDto();
    final DashboardDefinitionExportDto dashboardExport =
      createDashboardExportDtoWithResources(Collections.singletonList(reportExport.getId()));

    // when
    final List<IdResponseDto> importedIds =
      importClient.importEntitiesIntoCollectionAndReturnIds(
        collectionId,
        Sets.newHashSet(dashboardExport, reportExport)
      );

    // then
    assertThat(importedIds).hasSize(2);
    final List<ReportDefinitionDto> importedReports = retrieveImportedReports(importedIds);
    final Optional<DashboardDefinitionRestDto> importedDashboard = retrieveImportedDashboard(importedIds);

    assertThat(importedReports).hasSize(1);
    assertThat(importedDashboard).isPresent();
    assertThat(importedDashboard.get().getName()).isEqualTo(dashboardExport.getName());
    assertThat(importedDashboard.get().getOwner()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedDashboard.get().getLastModifier()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedDashboard.get().getCreated()).isEqualTo(LocalDateUtil.getCurrentDateTime());
    assertThat(importedDashboard.get().getLastModified()).isEqualTo(LocalDateUtil.getCurrentDateTime());
    assertThat(importedDashboard.get().getCollectionId()).isEqualTo(collectionId);
    assertThat(importedDashboard.get().getAvailableFilters()).isEqualTo(dashboardExport.getAvailableFilters());
    assertThat(importedDashboard.get().getReports())
      .singleElement()
      .isEqualTo(ReportLocationDto.builder()
                   .id(importedReports.get(0).getId())
                   .position(dashboardExport.getReports().get(0).getPosition())
                   .dimensions(dashboardExport.getReports().get(0).getDimensions())
                   .build());
  }

  @Test
  public void importDashboardIntoCollection_collectionDoesNotExist() {
    // when
    final Response response =
      importClient.importEntityIntoCollection("fakeCollection", createSimpleDashboardExportDto());

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  private Optional<DashboardDefinitionRestDto> retrieveImportedDashboard(final List<IdResponseDto> importedIds) {
    DashboardDefinitionRestDto dashboard = null;
    for (IdResponseDto id : importedIds) {
      final Response response = embeddedOptimizeExtension.getRequestExecutor()
        .buildGetDashboardRequest(id.getId())
        .execute();
      if (Response.Status.OK.getStatusCode() == response.getStatus()) {
        dashboard = response.readEntity(DashboardDefinitionRestDto.class);
        break;
      }
    }
    return Optional.ofNullable(dashboard);
  }

  private List<ReportDefinitionDto> retrieveImportedReports(final List<IdResponseDto> importedIds) {
    List<ReportDefinitionDto> reports = new ArrayList<>();
    for (IdResponseDto id : importedIds) {
      final Response response = embeddedOptimizeExtension.getRequestExecutor()
        .buildGetReportRequest(id.getId())
        .execute();
      if (Response.Status.OK.getStatusCode() == response.getStatus()) {
        reports.add(response.readEntity(ReportDefinitionDto.class));
      }
    }
    return reports;
  }

}
