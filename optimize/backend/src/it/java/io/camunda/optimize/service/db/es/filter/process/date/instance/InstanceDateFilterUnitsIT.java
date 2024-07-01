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
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
// import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.security.util.LocalDateUtil;
// import jakarta.ws.rs.core.Response;
// import java.time.OffsetDateTime;
// import java.util.List;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class InstanceDateFilterUnitsIT extends AbstractInstanceDateFilterIT {
//
//   @ParameterizedTest
//   @MethodSource("getRollingSupportedFilterUnits")
//   public void rollingDateFilterInReport(DateUnit dateUnit) {
//     // given
//     ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
//     OffsetDateTime processInstanceStartTime =
//         engineIntegrationExtension
//             .getHistoricProcessInstance(processInstance.getId())
//             .getStartTime();
//     importAllEngineEntitiesFromScratch();
//
//     // the clock of the engine and the clock of the computer running
//     // the tests might not be aligned. Therefore we want to simulate
//     // that the process instance is not started later than now.
//     LocalDateUtil.setCurrentTime(processInstanceStartTime);
//
//     // when
//     AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> result =
//         createAndEvaluateReportWithStartDateFilter(
//             processInstance.getProcessDefinitionKey(),
//             processInstance.getProcessDefinitionVersion(),
//             dateUnit,
//             1L,
//             true,
//             DateFilterType.ROLLING);
//
//     // then
//     assertResults(processInstance, result, 1);
//   }
//
//   @Test
//   public void rollingDateFilterInReport_unsupportedUnitQuarters() {
//     // given
//     ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     ProcessReportDataDto reportData =
//         createReport(
//             processInstance.getProcessDefinitionKey(),
//             processInstance.getProcessDefinitionVersion());
//     List<ProcessFilterDto<?>> rollingStartDateFilter =
//         createRollingStartDateFilter(DateUnit.QUARTERS, 1L);
//     reportData.setFilter(rollingStartDateFilter);
//
//     // then
//     Response response = reportClient.evaluateReportAndReturnResponse(reportData);
//     assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("getRelativeSupportedFilterUnits")
//   public void relativeDateFilterInReportCurrentInterval(DateUnit dateUnit) {
//     // given
//     ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
//     OffsetDateTime processInstanceStartTime =
//         engineIntegrationExtension
//             .getHistoricProcessInstance(processInstance.getId())
//             .getStartTime();
//     importAllEngineEntitiesFromScratch();
//     LocalDateUtil.setCurrentTime(processInstanceStartTime);
//
//     // when
//     AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> result =
//         createAndEvaluateReportWithStartDateFilter(
//             processInstance.getProcessDefinitionKey(),
//             processInstance.getProcessDefinitionVersion(),
//             dateUnit,
//             1L,
//             true,
//             DateFilterType.RELATIVE);
//
//     // then
//     assertResults(processInstance, result, 0);
//   }
//
//   @ParameterizedTest
//   @MethodSource("getRelativeSupportedFilterUnits")
//   public void relativeDateFilterInReportPreviousInterval(DateUnit dateUnit) {
//     // given
//     ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
//     OffsetDateTime processInstanceStartTime =
//         engineIntegrationExtension
//             .getHistoricProcessInstance(processInstance.getId())
//             .getStartTime();
//     importAllEngineEntitiesFromScratch();
//     LocalDateUtil.setCurrentTime(processInstanceStartTime);
//
//     // when
//     AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> result =
//         createAndEvaluateReportWithStartDateFilter(
//             processInstance.getProcessDefinitionKey(),
//             processInstance.getProcessDefinitionVersion(),
//             dateUnit,
//             0L,
//             true,
//             DateFilterType.RELATIVE);
//
//     // then
//     assertResults(processInstance, result, 1);
//   }
// }
