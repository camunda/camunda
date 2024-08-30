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
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.security.util.LocalDateUtil;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import java.time.OffsetDateTime;
// import java.util.List;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// public class RollingInstanceDateFilterIT extends AbstractInstanceDateFilterIT {
//
//   @Test
//   public void testStartDateRollingLogic() {
//     // given
//     embeddedOptimizeExtension.reloadConfiguration();
//     ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
//
//     OffsetDateTime processInstanceStartTime =
//         engineIntegrationExtension
//             .getHistoricProcessInstance(processInstance.getId())
//             .getStartTime();
//
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
//
//     importAllEngineEntitiesFromScratch();
//
//     LocalDateUtil.setCurrentTime(processInstanceStartTime);
//
//     AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> result =
//         createAndEvaluateReportWithStartDateFilter(
//             processInstance.getProcessDefinitionKey(),
//             processInstance.getProcessDefinitionVersion(),
//             DateUnit.DAYS,
//             1L,
//             false,
//             DateFilterType.ROLLING);
//
//     assertResults(processInstance, result, 1);
//
//     // when
//     LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusDays(2L));
//
//     // token has to be refreshed, as the old one expired already after moving the date
//     result =
//         createAndEvaluateReportWithStartDateFilter(
//             processInstance.getProcessDefinitionKey(),
//             processInstance.getProcessDefinitionVersion(),
//             DateUnit.DAYS,
//             1L,
//             true,
//             DateFilterType.ROLLING);
//
//     assertResults(processInstance, result, 0);
//   }
//
//   @Test
//   public void testEndDateRollingLogic() {
//     embeddedOptimizeExtension.reloadConfiguration();
//     ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
//
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
//
//     OffsetDateTime processInstanceEndTime =
//
// engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getEndTime();
//
//     importAllEngineEntitiesFromScratch();
//
//     LocalDateUtil.setCurrentTime(processInstanceEndTime);
//
//     // token has to be refreshed, as the old one expired already after moving the date
//     AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> result =
//         createAndEvaluateReportWithRollingEndDateFilter(
//             processInstance.getProcessDefinitionKey(),
//             processInstance.getProcessDefinitionVersion(),
//             DateUnit.DAYS,
//             true);
//
//     assertResults(processInstance, result, 1);
//
//     LocalDateUtil.setCurrentTime(processInstanceEndTime.plusDays(2L));
//
//     // token has to be refreshed, as the old one expired already after moving the date
//     result =
//         createAndEvaluateReportWithRollingEndDateFilter(
//             processInstance.getProcessDefinitionKey(),
//             processInstance.getProcessDefinitionVersion(),
//             DateUnit.DAYS,
//             true);
//
//     assertResults(processInstance, result, 0);
//   }
//
//   @ParameterizedTest
//   @MethodSource("simpleDateReportTypes")
//   public void dateReportsWithFilter_noDataReturnsEmptyResult(final ProcessReportDataType type) {
//     // given
//     ProcessDefinitionEngineDto engineDto = deployServiceTaskProcess();
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     ProcessReportDataDto reportData =
//         getAutomaticGroupByDateReportData(type, engineDto.getKey(),
// engineDto.getVersionAsString());
//     reportData.setFilter(
//         ProcessFilterBuilder.filter()
//             .rollingInstanceStartDate()
//             .start(1L, DateUnit.DAYS)
//             .add()
//             .rollingInstanceEndDate()
//             .start(1L, DateUnit.DAYS)
//             .add()
//             .buildList());
//     List<MapResultEntryDto> resultData =
// reportClient.evaluateReportAndReturnMapResult(reportData);
//
//     // then
//     assertThat(resultData).isEmpty();
//   }
// }
