/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.filter.process.date.instance;
//
// import static io.camunda.optimize.service.util.ProcessReportDataType.RAW_DATA;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.fasterxml.jackson.core.type.TypeReference;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
// import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.es.filter.process.AbstractFilterIT;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.stream.Stream;
// import org.camunda.bpm.model.bpmn.Bpmn;
// import org.camunda.bpm.model.bpmn.BpmnModelInstance;
//
// public abstract class AbstractInstanceDateFilterIT extends AbstractFilterIT {
//
//   protected ProcessInstanceEngineDto deployAndStartSimpleProcess() {
//     return deployAndStartSimpleProcessWithVariables(new HashMap<>());
//   }
//
//   private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(
//       Map<String, Object> variables) {
//     // @formatter:off
//     BpmnModelInstance processModel =
//         Bpmn.createExecutableProcess("aProcess")
//             .name("aProcessName")
//             .startEvent()
//             .serviceTask()
//             .camundaExpression("${true}")
//             .userTask()
//             .endEvent()
//             .done();
//     // @formatter:on
//     return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel,
// variables);
//   }
//
//   protected void assertResults(
//       ProcessInstanceEngineDto processInstance,
//       AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
//           evaluationResult,
//       int expectedPiCount) {
//
//     final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
//     assertThat(resultDataDto.getDefinitionVersions())
//         .contains(processInstance.getProcessDefinitionVersion());
//     assertThat(resultDataDto.getProcessDefinitionKey())
//         .isEqualTo(processInstance.getProcessDefinitionKey());
//     assertThat(resultDataDto.getView()).isNotNull();
//     final List<RawDataProcessInstanceDto> relativeDateResult =
//         evaluationResult.getResult().getData();
//     assertThat(relativeDateResult).isNotNull();
//     assertThat(relativeDateResult).hasSize(expectedPiCount);
//
//     if (expectedPiCount > 0) {
//       RawDataProcessInstanceDto rawDataProcessInstanceDto = relativeDateResult.get(0);
//       assertThat(rawDataProcessInstanceDto.getProcessInstanceId())
//           .isEqualTo(processInstance.getId());
//     }
//   }
//
//   protected AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
//       createAndEvaluateReportWithStartDateFilter(
//           String processDefinitionKey,
//           String processDefinitionVersion,
//           DateUnit unit,
//           Long value,
//           boolean newToken,
//           DateFilterType filterType) {
//     ProcessReportDataDto reportData =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(processDefinitionKey)
//             .setProcessDefinitionVersion(processDefinitionVersion)
//             .setReportDataType(RAW_DATA)
//             .build();
//
//     if (filterType.equals(DateFilterType.RELATIVE)) {
//       reportData.setFilter(createRelativeStartDateFilter(unit, value));
//     } else if (filterType.equals(DateFilterType.ROLLING)) {
//       reportData.setFilter(createRollingStartDateFilter(unit, value));
//     }
//
//     return evaluateReport(reportData, newToken);
//   }
//
//   protected List<ProcessFilterDto<?>> createRollingStartDateFilter(
//       final DateUnit unit, final Long value) {
//     return ProcessFilterBuilder.filter()
//         .rollingInstanceStartDate()
//         .start(value, unit)
//         .add()
//         .buildList();
//   }
//
//   protected List<ProcessFilterDto<?>> createRelativeStartDateFilter(
//       final DateUnit unit, final Long value) {
//     return ProcessFilterBuilder.filter()
//         .relativeInstanceStartDate()
//         .start(value, unit)
//         .add()
//         .buildList();
//   }
//
//   protected AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
//       createAndEvaluateReportWithRollingEndDateFilter(
//           String processDefinitionKey,
//           String processDefinitionVersion,
//           DateUnit unit,
//           boolean newToken) {
//     ProcessReportDataDto reportData =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(processDefinitionKey)
//             .setProcessDefinitionVersion(processDefinitionVersion)
//             .setReportDataType(RAW_DATA)
//             .build();
//     List<ProcessFilterDto<?>> rollingDateFilter =
//         ProcessFilterBuilder.filter().rollingInstanceEndDate().start(1L, unit).add().buildList();
//
//     reportData.setFilter(rollingDateFilter);
//     return evaluateReport(reportData, newToken);
//   }
//
//   private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
//       evaluateReport(ProcessReportDataDto reportData, boolean newToken) {
//     if (newToken) {
//       return evaluateReportWithNewToken(reportData);
//     } else {
//       return reportClient.evaluateRawReport(reportData);
//     }
//   }
//
//   private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>
//       evaluateReportWithNewToken(ProcessReportDataDto reportData) {
//     return embeddedOptimizeExtension
//         .getRequestExecutor()
//         .withGivenAuthToken(embeddedOptimizeExtension.getNewAuthenticationToken())
//         .buildEvaluateSingleUnsavedReportRequest(reportData)
//         // @formatter:off
//         .execute(
//             new TypeReference<
//                 AuthorizedProcessReportEvaluationResponseDto<
//                     List<RawDataProcessInstanceDto>>>() {});
//     // @formatter:on
//   }
//
//   protected static Stream<DateUnit> getRollingSupportedFilterUnits() {
//     return Stream.of(
//         DateUnit.MINUTES,
//         DateUnit.DAYS,
//         DateUnit.HOURS,
//         DateUnit.WEEKS,
//         DateUnit.MONTHS,
//         DateUnit.YEARS);
//   }
//
//   protected static Stream<DateUnit> getRelativeSupportedFilterUnits() {
//     return Stream.concat(Stream.of(DateUnit.QUARTERS), getRollingSupportedFilterUnits());
//   }
// }
