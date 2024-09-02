/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.processinstance.frequency.groupby.date.distributedby.none;
//
// import static io.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
// import
// io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import java.time.OffsetDateTime;
// import java.time.temporal.ChronoUnit;
// import java.util.Collections;
// import java.util.List;
// import java.util.Map;
// import org.junit.jupiter.api.Test;
//
// public class ProcessInstanceFrequencyByProcessInstanceStartDateReportEvaluationIT
//     extends AbstractProcessInstanceFrequencyByProcessInstanceDateReportEvaluationIT {
//
//   @Override
//   protected ProcessReportDataType getTestReportDataType() {
//     return ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE;
//   }
//
//   @Override
//   protected ProcessGroupByType getGroupByType() {
//     return ProcessGroupByType.START_DATE;
//   }
//
//   @Override
//   protected void changeProcessInstanceDate(
//       final String processInstanceId, final OffsetDateTime newDate) {
//     engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceId, newDate);
//   }
//
//   @Override
//   protected void updateProcessInstanceDates(final Map<String, OffsetDateTime> newIdToDates) {
//     engineDatabaseExtension.changeProcessInstanceStartDates(newIdToDates);
//   }
//
//   @Test
//   public void testEmptyBucketsAreReturnedForStartDateFilterPeriod() {
//     // given
//     final OffsetDateTime startDate = OffsetDateTime.now();
//     final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
//     final String definitionId = processInstanceDto.getDefinitionId();
//     final ProcessInstanceEngineDto processInstanceDto2 =
//         engineIntegrationExtension.startProcessInstance(definitionId);
//     changeProcessInstanceDate(processInstanceDto2.getId(), startDate.minusDays(2));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReportDataSortedDesc(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             getTestReportDataType(),
//             AggregateByDateUnit.DAY);
//     final RollingDateFilterDataDto dateFilterDataDto =
//         new RollingDateFilterDataDto(new RollingDateFilterStartDto(4L, DateUnit.DAYS));
//     final InstanceStartDateFilterDto startDateFilterDto = new InstanceStartDateFilterDto();
//     startDateFilterDto.setData(dateFilterDataDto);
//     startDateFilterDto.setFilterLevel(FilterApplicationLevel.INSTANCE);
//     reportData.setFilter(Collections.singletonList(startDateFilterDto));
//
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
//     assertThat(resultData).hasSize(5);
//
//     assertThat(resultData.get(0).getKey())
//         .isEqualTo(
//             embeddedOptimizeExtension.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS));
//     assertThat(resultData.get(0).getValue()).isEqualTo(1.);
//
//     assertThat(resultData.get(1).getKey())
//         .isEqualTo(
//             embeddedOptimizeExtension.formatToHistogramBucketKey(
//                 startDate.minusDays(1), ChronoUnit.DAYS));
//     assertThat(resultData.get(1).getValue()).isEqualTo(0.);
//
//     assertThat(resultData.get(2).getKey())
//         .isEqualTo(
//             embeddedOptimizeExtension.formatToHistogramBucketKey(
//                 startDate.minusDays(2), ChronoUnit.DAYS));
//     assertThat(resultData.get(2).getValue()).isEqualTo(1.);
//
//     assertThat(resultData.get(3).getKey())
//         .isEqualTo(
//             embeddedOptimizeExtension.formatToHistogramBucketKey(
//                 startDate.minusDays(3), ChronoUnit.DAYS));
//     assertThat(resultData.get(3).getValue()).isEqualTo(0.);
//
//     assertThat(resultData.get(4).getKey())
//         .isEqualTo(
//             embeddedOptimizeExtension.formatToHistogramBucketKey(
//                 startDate.minusDays(4), ChronoUnit.DAYS));
//     assertThat(resultData.get(4).getValue()).isEqualTo(0.);
//   }
//
//   @Test
//   public void evaluateReportWithSeveralRunningAndCompletedProcessInstances() {
//     // given 1 completed + 2 running process instances
//     final OffsetDateTime now = OffsetDateTime.now();
//
//     final ProcessDefinitionEngineDto processDefinition =
//         deployTwoRunningAndOneCompletedUserTaskProcesses(now);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     ProcessReportDataDto reportData =
//         createReportDataSortedDesc(
//             processDefinition.getKey(),
//             processDefinition.getVersionAsString(),
//             getTestReportDataType(),
//             AggregateByDateUnit.DAY);
//
//     AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
// evaluationResponse.getResult();
//     assertThat(result.getInstanceCount()).isEqualTo(3L);
//
//     final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
//
//     assertThat(resultData).isNotNull().hasSize(3);
//     assertThat(resultData.get(0).getKey())
//         .isEqualTo(localDateTimeToString(truncateToStartOfUnit(now, ChronoUnit.DAYS)));
//     assertThat(resultData.get(0).getValue()).isEqualTo(1.);
//
//     assertThat(resultData.get(1).getKey())
//         .isEqualTo(localDateTimeToString(truncateToStartOfUnit(now.minusDays(1),
// ChronoUnit.DAYS)));
//     assertThat(resultData.get(1).getValue()).isEqualTo(1.);
//
//     assertThat(resultData.get(2).getKey())
//         .isEqualTo(localDateTimeToString(truncateToStartOfUnit(now.minusDays(2),
// ChronoUnit.DAYS)));
//     assertThat(resultData.get(2).getValue()).isEqualTo(1.);
//   }
// }
