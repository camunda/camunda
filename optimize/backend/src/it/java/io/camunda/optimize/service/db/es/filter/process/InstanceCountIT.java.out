/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.filter.process;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
// import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
// import static
// io.camunda.optimize.service.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_END_DATE;
// import static
// io.camunda.optimize.service.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE;
// import static io.camunda.optimize.service.util.ProcessReportDataType.RAW_DATA;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createFixedEvaluationDateFilter;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.google.common.collect.Lists;
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
// import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
// import io.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.es.report.process.AbstractProcessDefinitionIT;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import io.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
// import io.camunda.optimize.test.util.decision.DecisionReportDataType;
// import java.time.OffsetDateTime;
// import java.util.Collections;
// import java.util.List;
// import java.util.stream.Stream;
// import lombok.SneakyThrows;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class InstanceCountIT extends AbstractProcessDefinitionIT {
//
//   @SneakyThrows
//   @Test
//   public void instanceCountWithoutFilters_processReport() {
//     // given
//     final ProcessDefinitionEngineDto userTaskProcess = deploySimpleOneUserTasksDefinition();
//     final ProcessInstanceEngineDto firstProcInst =
//         engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
//     final ProcessInstanceEngineDto secondProcInst =
//         engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
//     engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
//
//     engineDatabaseExtension.changeProcessInstanceState(firstProcInst.getId(), SUSPENDED_STATE);
//     engineDatabaseExtension.changeProcessInstanceState(secondProcInst.getId(), SUSPENDED_STATE);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportWithFilter =
//         createReport(userTaskProcess.getKey(), userTaskProcess.getVersionAsString());
//     final ProcessReportDataDto reportWithoutFilter =
//         createReport(userTaskProcess.getKey(), userTaskProcess.getVersionAsString());
//     reportWithFilter.setFilter(
//         ProcessFilterBuilder.filter().suspendedInstancesOnly().add().buildList());
//
//     final ReportResultResponseDto<List<RawDataProcessInstanceDto>> resultWithFilter =
//         reportClient.evaluateRawReport(reportWithFilter).getResult();
//     final ReportResultResponseDto<List<RawDataProcessInstanceDto>> resultWithoutFilter =
//         reportClient.evaluateRawReport(reportWithoutFilter).getResult();
//
//     // then
//     assertThat(resultWithFilter.getInstanceCount()).isEqualTo(2L);
//     assertThat(resultWithFilter.getInstanceCountWithoutFilters()).isEqualTo(3L);
//
//     assertThat(resultWithoutFilter.getInstanceCount()).isEqualTo(3L);
//     assertThat(resultWithoutFilter.getInstanceCountWithoutFilters()).isEqualTo(3L);
//   }
//
//   @Test
//   public void instanceCountWithoutFilters_decisionReport() {
//     final DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition();
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final DecisionReportDataDto reportWithFilter =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
//             .setDecisionDefinitionVersion(ALL_VERSIONS)
//             .setReportDataType(DecisionReportDataType.RAW_DATA)
//             .build();
//     final DecisionReportDataDto reportWithoutFilter =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
//             .setDecisionDefinitionVersion(ALL_VERSIONS)
//             .setReportDataType(DecisionReportDataType.RAW_DATA)
//             .build();
//
//     reportWithFilter.setFilter(
//         Lists.newArrayList(
//             createFixedEvaluationDateFilter(OffsetDateTime.now().plusDays(1), null)));
//
//     final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> resultWithFilter =
//         reportClient.evaluateDecisionRawReport(reportWithFilter).getResult();
//     final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> resultWithoutFilter =
//         reportClient.evaluateDecisionRawReport(reportWithoutFilter).getResult();
//
//     // then
//     assertThat(resultWithFilter.getInstanceCount()).isEqualTo(0L);
//     assertThat(resultWithFilter.getInstanceCountWithoutFilters()).isEqualTo(3L);
//
//     assertThat(resultWithoutFilter.getInstanceCount()).isEqualTo(3L);
//     assertThat(resultWithoutFilter.getInstanceCountWithoutFilters()).isEqualTo(3L);
//   }
//
//   @SneakyThrows
//   @Test
//   @Tag(OPENSEARCH_SINGLE_TEST_FAIL_OK)
//   public void instanceCount_combinedReport_endDateReportsExcludeRunningInstances() {
//     // given
//     final ProcessDefinitionEngineDto runningInstanceDef =
//         deploySimpleOneUserTasksDefinition("runningInstanceDef", null);
//     engineIntegrationExtension.startProcessInstance(runningInstanceDef.getId());
//     engineIntegrationExtension.startProcessInstance(runningInstanceDef.getId());
//
//     final SingleProcessReportDefinitionRequestDto singleReport1 =
//         createDateReport(PROC_INST_FREQ_GROUP_BY_END_DATE);
//     singleReport1.getData().setProcessDefinitionKey("runningInstanceDef");
//     final SingleProcessReportDefinitionRequestDto singleReport2 =
//         createDateReport(PROC_INST_FREQ_GROUP_BY_START_DATE);
//     singleReport2.getData().setProcessDefinitionKey("runningInstanceDef");
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final List<String> reportIds =
//         Stream.of(singleReport1, singleReport2)
//             .map(reportClient::createSingleProcessReport)
//             .toList();
//     final CombinedProcessReportResultDataDto<?> combinedResult =
//         reportClient.saveAndEvaluateCombinedReport(reportIds);
//
//     // then
//     assertThat(combinedResult.getInstanceCount()).isEqualTo(2);
//   }
//
//   @SneakyThrows
//   @Test
//   public void instanceCount_emptyCombinedReport() {
//     // given
//     final ProcessDefinitionEngineDto runningInstanceDef =
//         deploySimpleOneUserTasksDefinition("runningInstanceDef", null);
//     engineIntegrationExtension.startProcessInstance(runningInstanceDef.getId());
//     engineIntegrationExtension.startProcessInstance(runningInstanceDef.getId());
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final CombinedProcessReportResultDataDto<?> combinedResult =
//         reportClient.saveAndEvaluateCombinedReport(Collections.emptyList());
//
//     // then
//     assertThat(combinedResult.getInstanceCount()).isEqualTo(0);
//   }
//
//   private ProcessReportDataDto createReport(
//       final String definitionKey, final String definitionVersion) {
//     return TemplatedProcessReportDataBuilder.createReportData()
//         .setProcessDefinitionKey(definitionKey)
//         .setProcessDefinitionVersion(definitionVersion)
//         .setReportDataType(RAW_DATA)
//         .build();
//   }
//
//   private SingleProcessReportDefinitionRequestDto createDateReport(
//       final ProcessReportDataType reportDataType) {
//     final SingleProcessReportDefinitionRequestDto reportDefinitionDto =
//         new SingleProcessReportDefinitionRequestDto();
//     final ProcessReportDataDto runningReportData =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(TEST_PROCESS)
//             .setProcessDefinitionVersion("1")
//             .setGroupByDateInterval(AggregateByDateUnit.DAY)
//             .setReportDataType(reportDataType)
//             .build();
//     reportDefinitionDto.setData(runningReportData);
//     return reportDefinitionDto;
//   }
// }
