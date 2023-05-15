/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities.dashboard;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import org.camunda.optimize.dto.optimize.rest.ImportedIndexMismatchResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.entities.AbstractExportImportEntityDefinitionIT;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;

public class DashboardDefinitionImportIT extends AbstractExportImportEntityDefinitionIT {

  @Test
  public void importDashboard() {
    // given a dashboard with one of each resource type
    dateFreezer().freezeDateAndReturn();
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    createAndSaveDefinition(DefinitionType.DECISION, null);
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
    final List<EntityIdResponseDto> importedIds =
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
    assertThat(importedDashboard.get().getTiles()).hasSize(2);
    assertThat(importedDashboard.get().getTileIds())
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
  public void importDashboard_validDescription() {
    // given
    final DashboardDefinitionExportDto dashboardExport = createSimpleDashboardExportDto();
    final String dashboardDescription = "This is a valid dashboard description";
    dashboardExport.setDescription(dashboardDescription);

    // when
    final EntityIdResponseDto entityIdResponseDto = importClient.importEntityAndReturnId(dashboardExport);

    // then
    assertThat(retrieveImportedDashboard(List.of(entityIdResponseDto))).isPresent().get()
      .satisfies(dashboard -> assertThat(dashboard.getDescription()).isEqualTo(dashboardDescription));
  }

  @Test
  public void importDashboard_invalidDescription() {
    // given
    final DashboardDefinitionExportDto exportedDashboard = createSimpleDashboardExportDto();
    exportedDashboard.setDescription("");

    // when
    final Response response = importClient.importEntity(exportedDashboard);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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
    final List<EntityIdResponseDto> importedIds =
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
    assertThat(importedDashboard.get().getTiles())
      .singleElement()
      .isEqualTo(DashboardReportTileDto.builder()
                   .id(importedReports.get(0).getId())
                   .type(DashboardTileType.OPTIMIZE_REPORT)
                   .position(dashboardExport.getTiles().get(0).getPosition())
                   .dimensions(dashboardExport.getTiles().get(0).getDimensions())
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

  public static Set<String> getExternalResourceUrls(final DashboardDefinitionRestDto dashboardDefinitionRestDto) {
    Gson gson = new Gson();
    return dashboardDefinitionRestDto.getTiles().stream()
      .filter(tile -> tile.getType() == DashboardTileType.EXTERNAL_URL)
      .map(tile -> gson.fromJson(String.valueOf(tile.getConfiguration()), JsonElement.class)
        .getAsJsonObject()
        .get("external")
        .getAsString())
      .collect(toSet());
  }

}
