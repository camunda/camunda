/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.entities.report;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.ReportType;
// import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
// import io.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
// import
// io.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
// import io.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
// import
// io.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
// import
// io.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
// import io.camunda.optimize.service.entities.AbstractExportImportEntityDefinitionIT;
// import jakarta.ws.rs.core.Response;
// import java.util.ArrayList;
// import java.util.Collections;
// import java.util.List;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// public abstract class AbstractReportDefinitionExportIT
//     extends AbstractExportImportEntityDefinitionIT {
//
//   protected abstract List<ReportDefinitionExportDto> exportReportDefinitionAndReturnAsList(
//       final String reportId);
//
//   protected abstract Response exportReportDefinitionAndReturnResponse(final String reportId);
//
//   @Test
//   public void exportReportAsJsonFile_reportDoesNotExist() {
//     // when
//     Response response = exportClient.exportReportAsJsonAsDemo("fakeId", "my_file.json");
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
//     assertThat(response.readEntity(String.class)).contains("fakeId");
//   }
//
//   @ParameterizedTest
//   @MethodSource("getTestProcessReports")
//   public void exportProcessReportDefinitionAsJson(
//       final SingleProcessReportDefinitionRequestDto reportDefToExport) {
//     // given
//     final String reportId = reportClient.createSingleProcessReport(reportDefToExport);
//     final SingleProcessReportDefinitionExportDto expectedReportExportDto =
//         createExportDto(reportDefToExport);
//     expectedReportExportDto.setId(reportId);
//
//     // when
//     final List<ReportDefinitionExportDto> actualExportDtos =
//         exportReportDefinitionAndReturnAsList(reportId);
//
//     // then
//     assertThat(actualExportDtos)
//         .singleElement()
//         .usingRecursiveComparison()
//         .isEqualTo(expectedReportExportDto);
//   }
//
//   @ParameterizedTest
//   @MethodSource("getTestDecisionReports")
//   public void exportDecisionReportDefinitionAsJson(
//       final SingleDecisionReportDefinitionRequestDto reportDefToExport) {
//     // given
//     final String reportId = reportClient.createSingleDecisionReport(reportDefToExport);
//     final SingleDecisionReportDefinitionExportDto expectedReportExportDto =
//         new SingleDecisionReportDefinitionExportDto(reportDefToExport);
//     expectedReportExportDto.setId(reportId);
//
//     // when
//     final List<ReportDefinitionExportDto> actualExportDtos =
//         exportReportDefinitionAndReturnAsList(reportId);
//
//     // then
//     assertThat(actualExportDtos)
//         .singleElement()
//         .usingRecursiveComparison()
//         .isEqualTo(expectedReportExportDto);
//   }
//
//   @ParameterizedTest
//   @MethodSource("getTestCombinableReports")
//   public void exportCombinedReportAsJsonFile(
//       final List<SingleProcessReportDefinitionRequestDto> combinableReports) {
//     // given
//     final List<String> combinableReportIds = new ArrayList<>();
//     final List<SingleProcessReportDefinitionExportDto> expectedSingleReportExportDtos =
//         new ArrayList<>();
//     combinableReports.forEach(
//         reportDef -> {
//           final String reportId = reportClient.createSingleProcessReport(reportDef);
//           final SingleProcessReportDefinitionExportDto singleReportExportDto =
//               createExportDto(reportDef);
//           singleReportExportDto.setId(reportId);
//           combinableReportIds.add(reportId);
//           expectedSingleReportExportDtos.add(singleReportExportDto);
//         });
//     final String reportId = reportClient.createCombinedReport(null, combinableReportIds);
//     final CombinedReportDefinitionRequestDto combinedReport =
//         reportClient.getCombinedProcessReportById(reportId);
//     final CombinedProcessReportDefinitionExportDto expectedCombinedReportDto =
//         createExportDto(combinedReport);
//     expectedCombinedReportDto.setId(reportId);
//
//     // when
//     final List<ReportDefinitionExportDto> actualExportDtos =
//         exportReportDefinitionAndReturnAsList(reportId);
//
//     // then
//     assertThat(actualExportDtos)
//         .hasSize(3)
//         .filteredOn(
//             exportDto ->
// ExportEntityType.COMBINED_REPORT.equals(exportDto.getExportEntityType()))
//         .singleElement()
//         .usingRecursiveComparison()
//         .isEqualTo(expectedCombinedReportDto);
//     assertThat(actualExportDtos)
//         .filteredOn(
//             exportDto ->
//                 ExportEntityType.SINGLE_PROCESS_REPORT.equals(exportDto.getExportEntityType()))
//         .hasSize(2)
//         .containsExactlyElementsOf(expectedSingleReportExportDtos);
//   }
//
//   @Test
//   public void exportCombinedReportAsJsonFile_singleReportMissing() {
//     // given
//     final String singleReportId = createSimpleReport(ReportType.PROCESS);
//     final String combinedReportId =
//         reportClient.createCombinedReport(null, Collections.singletonList(singleReportId));
//     databaseIntegrationTestExtension.deleteAllSingleProcessReports();
//
//     // when
//     Response response = exportReportDefinitionAndReturnResponse(combinedReportId);
//
//     // then
//     assertThat(response.getStatus())
//         .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
//   }
// }
