/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.report.process.combined;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.ReportConstants;
// import io.camunda.optimize.dto.optimize.query.IdResponseDto;
// import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
// import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import io.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
// import io.camunda.optimize.service.db.es.report.process.AbstractProcessDefinitionIT;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import jakarta.ws.rs.core.Response;
// import java.util.ArrayList;
// import java.util.List;
// import org.junit.jupiter.api.Test;
//
// public abstract class AbstractCombinedDurationReportIT extends AbstractProcessDefinitionIT {
//
//   protected abstract ProcessReportDataType getReportDataType();
//
//   protected abstract void startInstanceAndModifyRelevantDurations(
//       final String definitionId, final int durationInMillis);
//
//   @Test
//   public void distinctRangesGetMerged() {
//     // given
//     final ProcessDefinitionEngineDto firstDefinition = deploySimpleOneUserTasksDefinition();
//     startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 1000);
//     startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 2000);
//
//     final ProcessDefinitionEngineDto secondDefinition =
// deploySimpleOneUserTasksDefinition("other");
//     startInstanceAndModifyRelevantDurations(secondDefinition.getId(), 8000);
//     startInstanceAndModifyRelevantDurations(secondDefinition.getId(), 10_000);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final CombinedReportDefinitionRequestDto combinedReport =
//         createCombinedReport(firstDefinition.getKey(), secondDefinition.getKey());
//     final IdResponseDto response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildCreateCombinedReportRequest(combinedReport)
//             .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());
//
//     // then
//     final CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
//         reportClient
//             .<List<MapResultEntryDto>>evaluateCombinedReportById(response.getId())
//             .getResult();
//     assertThat(result.getData().values())
//         .hasSize(2)
//         .allSatisfy(
//             singleReportResult -> {
//               assertThat(singleReportResult.getResult().getFirstMeasureData())
//                   .hasSize(10)
//                   .extracting(MapResultEntryDto::getKey)
//                   .first()
//                   .isEqualTo(createDurationBucketKey(1000));
//               assertThat(singleReportResult.getResult().getFirstMeasureData())
//                   .extracting(MapResultEntryDto::getKey)
//                   .last()
//                   .isEqualTo(createDurationBucketKey(10_000));
//             });
//   }
//
//   @Test
//   public void intersectingRangesGetMerged() {
//     // given
//     final ProcessDefinitionEngineDto firstDefinition = deploySimpleOneUserTasksDefinition();
//     startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 5000);
//     startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 7000);
//
//     final ProcessDefinitionEngineDto secondDefinition = deploySimpleOneUserTasksDefinition();
//     startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 6000);
//     startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 10_000);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final CombinedReportDefinitionRequestDto combinedReport =
//         createCombinedReport(firstDefinition.getKey(), secondDefinition.getKey());
//     final IdResponseDto response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildCreateCombinedReportRequest(combinedReport)
//             .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());
//
//     // then
//     final CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
//         reportClient
//             .<List<MapResultEntryDto>>evaluateCombinedReportById(response.getId())
//             .getResult();
//     assertThat(result.getData().values())
//         .hasSize(2)
//         .allSatisfy(
//             singleReportResult -> {
//               assertThat(singleReportResult.getResult().getFirstMeasureData())
//                   // expecting the range to be from 1000ms (nearest lower base 10 to min value)
// to
//                   // 10000ms (max value)
//                   .hasSize(10)
//                   .extracting(MapResultEntryDto::getKey)
//                   .first()
//                   .isEqualTo(createDurationBucketKey(1000));
//               assertThat(singleReportResult.getResult().getFirstMeasureData())
//                   .extracting(MapResultEntryDto::getKey)
//                   .last()
//                   .isEqualTo(createDurationBucketKey(10_000));
//             });
//   }
//
//   @Test
//   public void inclusiveRangesOuterRangeIsKept() {
//     // given
//     final ProcessDefinitionEngineDto firstDefinition = deploySimpleOneUserTasksDefinition();
//     startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 60_000);
//     startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 80_000);
//
//     final ProcessDefinitionEngineDto secondDefinition = deploySimpleOneUserTasksDefinition();
//     startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 50_000);
//     startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 100_000);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final CombinedReportDefinitionRequestDto combinedReport =
//         createCombinedReport(firstDefinition.getKey(), secondDefinition.getKey());
//     final IdResponseDto response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildCreateCombinedReportRequest(combinedReport)
//             .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());
//
//     // then
//     final CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
//         reportClient
//             .<List<MapResultEntryDto>>evaluateCombinedReportById(response.getId())
//             .getResult();
//     assertThat(result.getData().values())
//         .hasSize(2)
//         .allSatisfy(
//             singleReportResult -> {
//               assertThat(singleReportResult.getResult().getFirstMeasureData())
//                   // expecting the range to be from 10_000ms (nearest lower base 10 to minimum)
// to
//                   // 100_000ms (max value)
//                   .hasSize(10)
//                   .extracting(MapResultEntryDto::getKey)
//                   .first()
//                   .isEqualTo(createDurationBucketKey(10_000));
//               assertThat(singleReportResult.getResult().getFirstMeasureData())
//                   .extracting(MapResultEntryDto::getKey)
//                   .last()
//                   .isEqualTo(createDurationBucketKey(100_000));
//             });
//   }
//
//   private CombinedReportDefinitionRequestDto createCombinedReport(
//       final String firstReportDefinitionKey, final String secondReportDefinitionKey) {
//     final String reportId1 = createAndStoreDefaultReportDefinition(firstReportDefinitionKey);
//     final String reportId2 = createAndStoreDefaultReportDefinition(secondReportDefinitionKey);
//
//     final CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
//     final List<CombinedReportItemDto> reportIds = new ArrayList<>();
//     reportIds.add(new CombinedReportItemDto(reportId1));
//     reportIds.add(new CombinedReportItemDto(reportId2));
//
//     combinedReportData.setReports(reportIds);
//     final CombinedReportDefinitionRequestDto combinedReport =
//         new CombinedReportDefinitionRequestDto();
//     combinedReport.setData(combinedReportData);
//     return combinedReport;
//   }
//
//   private String createDurationBucketKey(final int durationInMs) {
//     return durationInMs + ".0";
//   }
//
//   private String createAndStoreDefaultReportDefinition(String processDefinitionKey) {
//     final ProcessReportDataDto reportData =
//         createReport(processDefinitionKey, ReportConstants.ALL_VERSIONS);
//     return createNewReport(reportData);
//   }
//
//   private ProcessReportDataDto createReport(
//       final String processKey, final String definitionVersion) {
//     return TemplatedProcessReportDataBuilder.createReportData()
//         .setProcessDefinitionKey(processKey)
//         .setProcessDefinitionVersion(definitionVersion)
//         .setReportDataType(getReportDataType())
//         .build();
//   }
// }
