/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.processinstance.frequency.groupby.variable.distributedby.none;
//
// import static com.google.common.collect.ImmutableMap.of;
// import static com.google.common.collect.Lists.newArrayList;
// import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
// import static io.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
// import static
// io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
// import static io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
// import static io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
// import static
// io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
// import static java.util.stream.Collectors.toList;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.google.common.collect.ImmutableMap;
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.IdResponseDto;
// import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
// import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
// import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
// import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
// import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
// import io.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
// import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
// import io.camunda.optimize.dto.optimize.query.variable.VariableType;
// import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
// import io.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.rest.optimize.dto.VariableDto;
// import io.camunda.optimize.service.db.es.report.process.AbstractProcessDefinitionIT;
// import io.camunda.optimize.service.db.es.report.util.MapResultUtil;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import io.camunda.optimize.test.it.extension.EngineVariableValue;
// import jakarta.ws.rs.core.Response;
// import java.time.OffsetDateTime;
// import java.time.ZoneId;
// import java.time.format.DateTimeFormatter;
// import java.time.temporal.ChronoUnit;
// import java.util.ArrayList;
// import java.util.Collections;
// import java.util.Comparator;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.stream.Collectors;
// import lombok.SneakyThrows;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// public class ProcessInstanceFrequencyByVariableReportEvaluationIT
//     extends AbstractProcessDefinitionIT {
//
//   @Test
//   public void simpleReportEvaluation() {
//     // given
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put("foo", "bar");
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             "foo",
//             VariableType.STRING);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final ProcessReportDataDto resultReportDataDto =
//         evaluationResponse.getReportDefinition().getData();
//     assertThat(resultReportDataDto.getProcessDefinitionKey())
//         .isEqualTo(processInstanceDto.getProcessDefinitionKey());
//     assertThat(resultReportDataDto.getDefinitionVersions())
//         .contains(processInstanceDto.getProcessDefinitionVersion());
//     assertThat(resultReportDataDto.getView()).isNotNull();
//     assertThat(resultReportDataDto.getView().getEntity())
//         .isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
//
// assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
//
// assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
//     final VariableGroupByDto variableGroupByDto =
//         (VariableGroupByDto) resultReportDataDto.getGroupBy();
//     assertThat(variableGroupByDto.getValue().getName()).isEqualTo("foo");
//     assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);
//
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
// evaluationResponse.getResult();
//     assertThat(result.getInstanceCount()).isEqualTo(1L);
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(1);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(),
// "bar").get().getValue())
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void simpleReportEvaluationById() {
//     // given
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put("foo", "bar");
//     final ProcessInstanceEngineDto processInstance =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     importAllEngineEntitiesFromScratch();
//     final String reportId =
//         createAndStoreDefaultReportDefinition(
//             processInstance.getProcessDefinitionKey(),
//             processInstance.getProcessDefinitionVersion(),
//             "foo",
//             VariableType.STRING);
//
//     // when
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReportById(reportId);
//
//     // then
//     final ProcessReportDataDto resultReportDataDto =
//         evaluationResponse.getReportDefinition().getData();
//     assertThat(resultReportDataDto.getProcessDefinitionKey())
//         .isEqualTo(processInstance.getProcessDefinitionKey());
//     assertThat(resultReportDataDto.getDefinitionVersions())
//         .contains(processInstance.getProcessDefinitionVersion());
//     assertThat(resultReportDataDto.getView()).isNotNull();
//     assertThat(resultReportDataDto.getView().getEntity())
//         .isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
//
// assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
//
// assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
//     final VariableGroupByDto variableGroupByDto =
//         (VariableGroupByDto) resultReportDataDto.getGroupBy();
//     assertThat(variableGroupByDto.getValue().getName()).isEqualTo("foo");
//     assertThat(variableGroupByDto.getValue().getType()).isEqualTo(VariableType.STRING);
//
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
// evaluationResponse.getResult();
//     assertThat(result.getInstanceCount()).isEqualTo(1L);
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(1);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(),
// "bar").get().getValue())
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void otherProcessDefinitionsDoNotAffectResult() {
//     // given
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put("foo", "bar");
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     variables.put("foo", "bar2");
//     deployAndStartSimpleServiceTaskProcess(variables);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             "foo",
//             VariableType.STRING);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
// evaluationResponse.getResult();
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(1);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(),
// "bar").get().getValue())
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void reportEvaluationSingleBucketFilteredBySingleTenant() {
//     // given
//     final String tenantId1 = "tenantId1";
//     final String tenantId2 = "tenantId2";
//     final List<String> selectedTenants = newArrayList(tenantId1);
//     final String processKey =
//         deployAndStartMultiTenantSimpleServiceTaskProcess(newArrayList(null, tenantId1,
// tenantId2));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(processKey, ALL_VERSIONS, DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_TYPE);
//     reportData.setTenantIds(selectedTenants);
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
//   }
//
//   @Test
//   public void multipleProcessInstances() {
//     // given
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put("foo", "bar1");
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     variables.put("foo", "bar2");
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             "foo",
//             VariableType.STRING);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final ReportResultResponseDto<List<MapResultEntryDto>> resultDto =
//         evaluationResponse.getResult();
//     assertThat(resultDto.getFirstMeasureData()).isNotNull();
//     assertThat(resultDto.getFirstMeasureData()).hasSize(2);
//     assertThat(
//             MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(),
// "bar1").get().getValue())
//         .isEqualTo(1.);
//     assertThat(
//             MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(),
// "bar2").get().getValue())
//         .isEqualTo(2.);
//   }
//
//   @Test
//   public void multipleVariableValuesInOneInstance() {
//     // given
//     final VariableDto listVar =
//         variablesClient.createListJsonObjectVariableDto(List.of("value1", "value2"));
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put("listVar", listVar);
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             "listVar",
//             VariableType.STRING);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final ReportResultResponseDto<List<MapResultEntryDto>> resultDto =
//         evaluationResponse.getResult();
//     assertThat(resultDto.getFirstMeasureData()).isNotNull();
//     assertThat(resultDto.getFirstMeasureData()).hasSize(2);
//     assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), "value1"))
//         .isPresent()
//         .map(MapResultEntryDto::getValue)
//         .contains(1.);
//     assertThat(MapResultUtil.getEntryForKey(resultDto.getFirstMeasureData(), "value2"))
//         .isPresent()
//         .map(MapResultEntryDto::getValue)
//         .contains(1.);
//   }
//
//   @Test
//   public void numberVariable_customBuckets() {
//     // given
//     final String varName = "doubleVar";
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put(varName, 100.0);
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//
//     variables.put(varName, 200.0);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     variables.put(varName, 300.0);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             varName,
//             VariableType.DOUBLE,
//             10.0,
//             100.0);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final List<MapResultEntryDto> resultData =
// evaluationResponse.getResult().getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//     assertThat(resultData).hasSize(3);
//     assertThat(resultData.stream().map(MapResultEntryDto::getKey).collect(toList()))
//         .containsExactly("10.00", "110.00", "210.00");
//     assertThat(resultData.get(0).getValue()).isEqualTo(1L);
//     assertThat(resultData.get(1).getValue()).isEqualTo(1L);
//     assertThat(resultData.get(2).getValue()).isEqualTo(1L);
//   }
//
//   @SneakyThrows
//   @Test
//   public void combinedNumberVariableReport_distinctRanges() {
//     // given
//     final String varName = "doubleVar";
//     final Map<String, Object> variables = new HashMap<>();
//
//     variables.put(varName, 10.0);
//     final ProcessInstanceEngineDto processInstanceDto1 =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     variables.put(varName, 20.0);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto1.getDefinitionId(), variables);
//
//     variables.put(varName, 50.0);
//     final ProcessInstanceEngineDto processInstanceDto2 =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     variables.put(varName, 100.0);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto2.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     final ProcessReportDataDto reportData1 =
//         createReport(
//             processInstanceDto1.getProcessDefinitionKey(),
//             processInstanceDto1.getProcessDefinitionVersion(),
//             varName,
//             VariableType.DOUBLE,
//             10.0,
//             10.0);
//
//     final ProcessReportDataDto reportData2 =
//         createReport(
//             processInstanceDto2.getProcessDefinitionKey(),
//             processInstanceDto2.getProcessDefinitionVersion(),
//             varName,
//             VariableType.DOUBLE,
//             5.0,
//             10.0);
//
//     final CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
//
//     final List<CombinedReportItemDto> reportIds = new ArrayList<>();
//     reportIds.add(
//         new CombinedReportItemDto(
//             reportClient.createSingleProcessReport(
//                 new SingleProcessReportDefinitionRequestDto(reportData1))));
//     reportIds.add(
//         new CombinedReportItemDto(
//             reportClient.createSingleProcessReport(
//                 new SingleProcessReportDefinitionRequestDto(reportData2))));
//
//     combinedReportData.setReports(reportIds);
//     final CombinedReportDefinitionRequestDto combinedReport =
//         new CombinedReportDefinitionRequestDto();
//     combinedReport.setData(combinedReportData);
//
//     // when
//     final IdResponseDto response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildCreateCombinedReportRequest(combinedReport)
//             .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());
//
//     // then
//     final CombinedProcessReportResultDataDto result =
//         reportClient.evaluateCombinedReportById(response.getId()).getResult();
//     assertCombinedDoubleVariableResultsAreInCorrectRanges(10.0, 100.0, 10, 2, result.getData());
//   }
//
//   @SneakyThrows
//   @Test
//   public void combinedNumberVariableReport_intersectingRanges() {
//     // given
//     final String varName = "doubleVar";
//     final Map<String, Object> variables = new HashMap<>();
//
//     variables.put(varName, 10.0);
//     final ProcessInstanceEngineDto processInstanceDto1 =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     variables.put(varName, 20.0);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto1.getDefinitionId(), variables);
//
//     variables.put(varName, 15.0);
//     final ProcessInstanceEngineDto processInstanceDto2 =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     variables.put(varName, 25.0);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto2.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     final ProcessReportDataDto reportData1 =
//         createReport(
//             processInstanceDto1.getProcessDefinitionKey(),
//             processInstanceDto1.getProcessDefinitionVersion(),
//             varName,
//             VariableType.DOUBLE,
//             10.0,
//             5.0);
//
//     final ProcessReportDataDto reportData2 =
//         createReport(
//             processInstanceDto2.getProcessDefinitionKey(),
//             processInstanceDto2.getProcessDefinitionVersion(),
//             varName,
//             VariableType.DOUBLE,
//             5.0,
//             5.0);
//
//     final CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
//
//     final List<CombinedReportItemDto> reportIds = new ArrayList<>();
//     reportIds.add(
//         new CombinedReportItemDto(
//             reportClient.createSingleProcessReport(
//                 new SingleProcessReportDefinitionRequestDto(reportData1))));
//     reportIds.add(
//         new CombinedReportItemDto(
//             reportClient.createSingleProcessReport(
//                 new SingleProcessReportDefinitionRequestDto(reportData2))));
//
//     combinedReportData.setReports(reportIds);
//     final CombinedReportDefinitionRequestDto combinedReport =
//         new CombinedReportDefinitionRequestDto();
//     combinedReport.setData(combinedReportData);
//
//     // when
//     final IdResponseDto response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildCreateCombinedReportRequest(combinedReport)
//             .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());
//
//     // then
//     final CombinedProcessReportResultDataDto result =
//         reportClient.evaluateCombinedReportById(response.getId()).getResult();
//     assertCombinedDoubleVariableResultsAreInCorrectRanges(10.0, 25.0, 4, 2, result.getData());
//   }
//
//   @SneakyThrows
//   @Test
//   public void combinedNumberVariableReport_inclusiveRanges() {
//     // given
//     final String varName = "doubleVar";
//     final Map<String, Object> variables = new HashMap<>();
//
//     variables.put(varName, 10.0);
//     final ProcessInstanceEngineDto processInstanceDto1 =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     variables.put(varName, 30.0);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto1.getDefinitionId(), variables);
//
//     variables.put(varName, 15.0);
//     final ProcessInstanceEngineDto processInstanceDto2 =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     variables.put(varName, 25.0);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto2.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     final ProcessReportDataDto reportData1 =
//         createReport(
//             processInstanceDto1.getProcessDefinitionKey(),
//             processInstanceDto1.getProcessDefinitionVersion(),
//             varName,
//             VariableType.DOUBLE,
//             10.0,
//             5.0);
//
//     final ProcessReportDataDto reportData2 =
//         createReport(
//             processInstanceDto2.getProcessDefinitionKey(),
//             processInstanceDto2.getProcessDefinitionVersion(),
//             varName,
//             VariableType.DOUBLE,
//             5.0,
//             5.0);
//
//     final CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
//
//     final List<CombinedReportItemDto> reportIds = new ArrayList<>();
//     reportIds.add(
//         new CombinedReportItemDto(
//             reportClient.createSingleProcessReport(
//                 new SingleProcessReportDefinitionRequestDto(reportData1))));
//     reportIds.add(
//         new CombinedReportItemDto(
//             reportClient.createSingleProcessReport(
//                 new SingleProcessReportDefinitionRequestDto(reportData2))));
//
//     combinedReportData.setReports(reportIds);
//     final CombinedReportDefinitionRequestDto combinedReport =
//         new CombinedReportDefinitionRequestDto();
//     combinedReport.setData(combinedReportData);
//
//     // when
//     final IdResponseDto response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildCreateCombinedReportRequest(combinedReport)
//             .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());
//
//     // then
//     final CombinedProcessReportResultDataDto result =
//         reportClient.evaluateCombinedReportById(response.getId()).getResult();
//     assertCombinedDoubleVariableResultsAreInCorrectRanges(10.0, 30.0, 5, 2, result.getData());
//   }
//
//   @Test
//   public void numberVariable_invalidBaseline_returnsEmptyResult() {
//     // given
//     final String varName = "doubleVar";
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put(varName, 10.0);
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//
//     variables.put(varName, 20.0);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when the baseline is larger than the max. variable value
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             varName,
//             VariableType.DOUBLE,
//             30.0,
//             5.0);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then the report returns an empty result
//     final List<MapResultEntryDto> resultData =
// evaluationResponse.getResult().getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//     assertThat(resultData).isEmpty();
//   }
//
//   @Test
//   public void numberVariable_negativeValues_defaultBaselineWorks() {
//     // given
//     final String varName = "intVar";
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put(varName, -1);
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//
//     variables.put(varName, -5);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when there is no baseline set
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             varName,
//             VariableType.INTEGER);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then the result includes all instances
//     final List<MapResultEntryDto> resultData =
// evaluationResponse.getResult().getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//
// assertThat(resultData.stream().mapToDouble(MapResultEntryDto::getValue).sum()).isEqualTo(2.0);
//   }
//
//   @Test
//   public void shortVariable_valuesSmallerThanBaseline() {
//     // given
//     final String varName = "shortVar";
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put(varName, (short) -10);
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//
//     variables.put(varName, (short) 8);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     variables.put(varName, (short) 20);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when there is a baseline set
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             varName,
//             VariableType.SHORT,
//             -1.0,
//             10.0);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then buckets start from the baseline and values below it are not included
//     final List<MapResultEntryDto> resultData =
// evaluationResponse.getResult().getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//     assertThat(resultData.get(0).getKey()).isEqualTo("-1");
//     assertThat(resultData.get(0).getValue()).isEqualTo(1.0);
//     assertThat(resultData.get(1).getKey()).isEqualTo("9");
//     assertThat(resultData.get(1).getValue()).isEqualTo(0.0);
//     assertThat(resultData.get(2).getKey()).isEqualTo("19");
//     assertThat(resultData.get(2).getValue()).isEqualTo(1.0);
//   }
//
//   @Test
//   public void intVariable_valuesSmallerThanBaseline() {
//     // given
//     final String varName = "intVar";
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put(varName, -10);
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//
//     variables.put(varName, 8);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     variables.put(varName, 20);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when there is a baseline set
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             varName,
//             VariableType.INTEGER,
//             -1.0,
//             10.0);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then buckets start from the baseline and values below it are not included
//     final List<MapResultEntryDto> resultData =
// evaluationResponse.getResult().getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//     assertThat(resultData.get(0).getKey()).isEqualTo("-1");
//     assertThat(resultData.get(0).getValue()).isEqualTo(1.0);
//     assertThat(resultData.get(1).getKey()).isEqualTo("9");
//     assertThat(resultData.get(1).getValue()).isEqualTo(0.0);
//     assertThat(resultData.get(2).getKey()).isEqualTo("19");
//     assertThat(resultData.get(2).getValue()).isEqualTo(1.0);
//   }
//
//   @Test
//   public void doubleVariable_valuesSmallerThanBaseline() {
//     // given
//     final String varName = "doubleVar";
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put(varName, -10.0);
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//
//     variables.put(varName, 8.0);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     variables.put(varName, 20.0);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when there is a baseline set
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             varName,
//             VariableType.DOUBLE,
//             -1.0,
//             10.0);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then buckets start from the baseline and values below it are not included
//     final List<MapResultEntryDto> resultData =
// evaluationResponse.getResult().getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//     assertThat(resultData.get(0).getKey()).isEqualTo("-1.00");
//     assertThat(resultData.get(0).getValue()).isEqualTo(1.0);
//     assertThat(resultData.get(1).getKey()).isEqualTo("9.00");
//     assertThat(resultData.get(1).getValue()).isEqualTo(0.0);
//     assertThat(resultData.get(2).getKey()).isEqualTo("19.00");
//     assertThat(resultData.get(2).getValue()).isEqualTo(1.0);
//   }
//
//   @Test
//   public void longVariable_valuesSmallerThanBaseline() {
//     // given
//     final String varName = "longVar";
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put(varName, (long) -10);
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//
//     variables.put(varName, (long) 8);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     variables.put(varName, (long) 20);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when there is a baseline set
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             varName,
//             VariableType.LONG,
//             -1.0,
//             10.0);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then buckets start from the baseline and values below it are not included
//     final List<MapResultEntryDto> resultData =
// evaluationResponse.getResult().getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//     assertThat(resultData.get(0).getKey()).isEqualTo("-1");
//     assertThat(resultData.get(0).getValue()).isEqualTo(1.0);
//     assertThat(resultData.get(1).getKey()).isEqualTo("9");
//     assertThat(resultData.get(1).getValue()).isEqualTo(0.0);
//     assertThat(resultData.get(2).getKey()).isEqualTo("19");
//     assertThat(resultData.get(2).getValue()).isEqualTo(1.0);
//   }
//
//   @Test
//   public void numberVariable_negativeValues_negativeBaseline() {
//     // given
//     final String varName = "intVar";
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put(varName, -1);
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//
//     variables.put(varName, -5);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when there is no baseline set
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             varName,
//             VariableType.INTEGER,
//             -10.0,
//             null);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then the baseline is correct and the result includes all instances
//     final List<MapResultEntryDto> resultData =
// evaluationResponse.getResult().getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//     assertThat(resultData.get(0).getKey()).isEqualTo("-10");
//     assertThat(
//             resultData.stream()
//                 .map(MapResultEntryDto::getValue)
//                 .filter(value -> value != 0.0)
//                 .collect(toList()))
//         .containsExactly(1.0, 1.0);
//   }
//
//   @Test
//   public void doubleVariable_bucketKeysHaveTwoDecimalPlaces() {
//     // given
//     final String varName = "doubleVar";
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put(varName, 1.0);
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//
//     variables.put(varName, 5.0);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             varName,
//             VariableType.DOUBLE);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final List<MapResultEntryDto> resultData =
// evaluationResponse.getResult().getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//     assertThat(resultData)
//         .extracting(MapResultEntryDto::getKey)
//         .allMatch(
//             key ->
//                 key.length() - key.indexOf(".") - 1
//                     == 2); // key should have two chars after the decimal
//   }
//
//   @Test
//   public void numberVariable_largeValues_notTooManyAutomaticBuckets() {
//     // given
//     final String varName = "longVar";
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put(varName, 9_100_000_000_000_000_000L);
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//
//     variables.put(varName, -920_000_000_000_000_000L);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             varName,
//             VariableType.LONG);
//     final List<MapResultEntryDto> resultData =
//         reportClient.evaluateMapReport(reportData).getResult().getFirstMeasureData();
//
//     // then the amount of buckets does not exceed
//     // NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION
//     // (a precaution to avoid too many buckets for distributed reports)
//     assertThat(resultData)
//         .isNotNull()
//         .isNotEmpty()
//         .hasSizeLessThanOrEqualTo(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
//   }
//
//   @Test
//   public void testCustomOrderOnResultKeyIsApplied() {
//     // given
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put("foo", "bar1");
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     variables.put("foo", "bar2");
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//     variables.put("foo", "bar3");
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             "foo",
//             VariableType.STRING);
//     reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
//     assertThat(resultData).hasSize(3);
//     final List<String> resultKeys =
//         resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
//     assertThat(resultKeys).isSortedAccordingTo(Comparator.reverseOrder());
//   }
//
//   @Test
//   public void testCustomOrderOnResultValueIsApplied() {
//     // given
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put("foo", "bar1");
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     variables.put("foo", "bar2");
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//     variables.put("foo", "bar3");
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             "foo",
//             VariableType.STRING);
//     reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
//     assertThat(resultData).hasSize(3);
//     final List<Double> bucketValues =
//         resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
//     assertThat(bucketValues).isSortedAccordingTo(Comparator.naturalOrder());
//   }
//
//   @Test
//   public void variableTypeIsImportant() {
//     // given
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put("foo", "1");
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     variables.put("foo", 1);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             "foo",
//             VariableType.STRING);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
// evaluationResponse.getResult();
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(2);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "1").get().getValue())
//         .isEqualTo(1.);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), MISSING_VARIABLE_KEY)
//                 .get()
//                 .getValue())
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void otherVariablesDoNotDistortTheResult() {
//     // given
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put("foo1", "bar1");
//     variables.put("foo2", "bar1");
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             "foo1",
//             VariableType.STRING);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
// evaluationResponse.getResult();
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(1);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(),
// "bar1").get().getValue())
//         .isEqualTo(2.);
//   }
//
//   @Test
//   public void worksWithAllVariableTypes() {
//     // given
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put("dateVar", OffsetDateTime.now());
//     variables.put("boolVar", true);
//     variables.put("shortVar", (short) 2);
//     variables.put("intVar", 3);
//     variables.put("longVar", 4L);
//     variables.put("doubleVar", 5.5);
//     variables.put("stringVar", "aString");
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     importAllEngineEntitiesFromScratch();
//
//     for (final Map.Entry<String, Object> entry : variables.entrySet()) {
//       // when
//       final VariableType variableType = varNameToTypeMap.get(entry.getKey());
//       final ProcessReportDataDto reportData =
//           createReport(
//               processInstanceDto.getProcessDefinitionKey(),
//               processInstanceDto.getProcessDefinitionVersion(),
//               entry.getKey(),
//               variableType);
//       final List<MapResultEntryDto> resultData =
//           reportClient.evaluateMapReport(reportData).getResult().getFirstMeasureData();
//
//       // then
//
//       assertThat(resultData).isNotNull();
//       final String expectedKey;
//       if (VariableType.DATE.equals(variableType)) {
//         final OffsetDateTime temporal = (OffsetDateTime) variables.get(entry.getKey());
//         expectedKey =
//             embeddedOptimizeExtension.formatToHistogramBucketKey(
//                 temporal.atZoneSimilarLocal(ZoneId.systemDefault()).toOffsetDateTime(),
//                 ChronoUnit.MONTHS);
//         assertThat(resultData).hasSize(1);
//         assertThat(resultData.get(0).getKey()).isEqualTo(expectedKey);
//         assertThat(resultData.get(0).getValue()).isEqualTo(1.);
//       } else if (VariableType.getNumericTypes().contains(variableType)) {
//         assertThat(
//                 resultData.stream()
//                     .mapToDouble(
//                         resultEntry ->
//                             resultEntry.getValue() == null ? 0.0 : resultEntry.getValue())
//                     .sum())
//             .isEqualTo(1.0);
//       } else {
//         assertThat(resultData).hasSize(1);
//         expectedKey = String.valueOf(entry.getValue());
//         assertThat(resultData.get(0).getKey()).isEqualTo(expectedKey);
//       }
//     }
//   }
//
//   @Test
//   public void multipleVariablesWithSameNameInOneProcessInstanceAreCountedOnlyOnce() {
//     // given
//     final Map<String, Object> variables =
//         ImmutableMap.of("testVar", "withValue", "testVarTemp", "withValue");
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     engineDatabaseExtension.changeVariableName(
//         processInstanceDto.getId(), "testVarTemp", "testVar");
//
//     importAllEngineEntitiesFromScratch();
//
//     assertThat(getVariableInstanceCount("testVar")).isEqualTo(2);
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             "testVar",
//             VariableType.STRING);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
// evaluationResponse.getResult();
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(1);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "withValue")
//                 .get()
//                 .getValue())
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void missingVariablesAggregationWorksForUndefinedAndNullVariables() {
//     // given
//     // 1 process instance with 'testVar'
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(of("testVar", "withValue"));
//
//     // 4 process instances without 'testVar'
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), Collections.singletonMap("testVar", null));
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(),
//         Collections.singletonMap("testVar", new EngineVariableValue(null, "String")));
//     engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), of("differentStringValue", "test"));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             "testVar",
//             VariableType.STRING);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
// evaluationResponse.getResult();
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(2);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "withValue")
//                 .get()
//                 .getValue())
//         .isEqualTo(1.);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(),
// "missing").get().getValue())
//         .isEqualTo(4.);
//   }
//
//   @Test
//   public void
//       missingVariablesAggregationForNullInputVariableOfTypeDouble_sortingByKeyDoesNotFail() {
//     // given a process instance with variable with non null value and one instance with variable
//     // with null value
//     final String varName = "doubleVar";
//     final Double varValue = 1.0;
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(of(varName, varValue));
//
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(),
//         Collections.singletonMap(varName, new EngineVariableValue(null, "Double")));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             varName,
//             VariableType.DOUBLE);
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>
// evaluationResponse =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
// evaluationResponse.getResult();
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(2);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(),
// "1.00").get().getValue())
//         .isEqualTo(1.);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(),
// "missing").get().getValue())
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void dateVariablesAreSortedAscByDefault() {
//     // given
//     Map<String, Object> variables = new HashMap<>();
//     variables.put("dateVar", OffsetDateTime.now());
//
//     final ProcessInstanceEngineDto processInstanceDto =
//         deployAndStartSimpleServiceTaskProcess(variables);
//
//     variables = Collections.singletonMap("dateVar", OffsetDateTime.now().minusDays(2));
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     variables = Collections.singletonMap("dateVar", OffsetDateTime.now().minusDays(1));
//     engineIntegrationExtension.startProcessInstance(
//         processInstanceDto.getDefinitionId(), variables);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstanceDto.getProcessDefinitionKey(),
//             processInstanceDto.getProcessDefinitionVersion(),
//             "dateVar",
//             VariableType.DATE);
//
//     final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> response =
//         reportClient.evaluateMapReport(reportData);
//
//     // then
//     final List<MapResultEntryDto> resultData = response.getResult().getFirstMeasureData();
//     final List<String> resultKeys =
//         resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
//     assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
//   }
//
//   @Test
//   public void dateFilterInReport() {
//     // given
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put("foo", "bar");
//     final ProcessInstanceEngineDto processInstance =
//         deployAndStartSimpleServiceTaskProcess(variables);
//     final OffsetDateTime past =
//         engineIntegrationExtension
//             .getHistoricProcessInstance(processInstance.getId())
//             .getStartTime();
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(
//             processInstance.getProcessDefinitionKey(),
//             processInstance.getProcessDefinitionVersion(),
//             "foo",
//             VariableType.STRING);
//     reportData.setFilter(
//         ProcessFilterBuilder.filter()
//             .fixedInstanceStartDate()
//             .start(null)
//             .end(past.minusSeconds(1L))
//             .add()
//             .buildList());
//     ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).isEmpty();
//
//     // when
//     reportData.setFilter(
//         ProcessFilterBuilder.filter()
//             .fixedInstanceStartDate()
//             .start(past)
//             .end(null)
//             .add()
//             .buildList());
//     result = reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(1);
//     assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(),
// "bar").get().getValue())
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void optimizeExceptionOnViewEntityIsNull() {
//     // given
//     final ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
//     dataDto.getView().setEntity(null);
//
//     // when
//     final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
//   }
//
//   @Test
//   public void optimizeExceptionOnViewPropertyIsNull() {
//     // given
//     final ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
//     dataDto.getView().setProperties((ViewProperty) null);
//
//     // when
//     final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
//   }
//
//   @Test
//   public void optimizeExceptionOnGroupByTypeIsNull() {
//     // given
//     final ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
//     dataDto.getGroupBy().setType(null);
//
//     // when
//     final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
//   }
//
//   @Test
//   public void optimizeExceptionOnGroupByValueNameIsNull() {
//     // given
//     final ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
//     final VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
//     groupByDto.getValue().setName(null);
//
//     // when
//     final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);
//
//     // then
//     assertThat(response.getStatus())
//         .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
//   }
//
//   @Test
//   public void optimizeExceptionOnGroupByValueTypeIsNull() {
//     // given
//     final ProcessReportDataDto dataDto = createReport("123", "1", "foo", VariableType.STRING);
//     final VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
//     groupByDto.getValue().setType(null);
//
//     // when
//     final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);
//
//     // then
//     assertThat(response.getStatus())
//         .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
//   }
//
//   @SneakyThrows
//   @ParameterizedTest
//   @MethodSource("staticAggregateByDateUnits")
//   public void groupByDateVariableWorksForAllStaticUnits(final AggregateByDateUnit unit) {
//     // given
//     final ChronoUnit chronoUnit = mapToChronoUnit(unit);
//     final int numberOfInstances = 3;
//     final String dateVarName = "dateVar";
//     final ProcessDefinitionEngineDto def = deploySimpleServiceTaskProcessAndGetDefinition();
//     final Map<String, Object> variables = new HashMap<>();
//     OffsetDateTime dateVariableValue = OffsetDateTime.parse("2020-06-15T00:00:00+02:00");
//
//     for (int i = 0; i < numberOfInstances; i++) {
//       dateVariableValue = dateVariableValue.plus(1, chronoUnit);
//       variables.put(dateVarName, dateVariableValue);
//       engineIntegrationExtension.startProcessInstance(def.getId(), variables);
//     }
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(def.getKey(), def.getVersionAsString(), dateVarName, VariableType.DATE);
//     reportData.getConfiguration().setGroupByDateVariableUnit(unit);
//     final List<MapResultEntryDto> resultData =
//         reportClient.evaluateMapReport(reportData).getResult().getFirstMeasureData();
//
//     // then
//     assertThat(resultData).isNotNull();
//     // there is one bucket per instance since the date variables are each one bucket span apart
//     assertThat(resultData).hasSize(numberOfInstances);
//     // buckets are in ascending order, so the first bucket is based on the date variable of the
//     // first instance
//     dateVariableValue = dateVariableValue.minus(numberOfInstances - 1, chronoUnit);
//     for (int i = 0; i < numberOfInstances; i++) {
//       final String expectedBucketKey =
//           embeddedOptimizeExtension.formatToHistogramBucketKey(
//               dateVariableValue.plus(i, chronoUnit), chronoUnit);
//       assertThat(resultData.get(i).getValue()).isEqualTo(1);
//       assertThat(resultData.get(i).getKey()).isEqualTo(expectedBucketKey);
//     }
//   }
//
//   @SneakyThrows
//   @Test
//   public void groupByDateVariableWorksForAutomaticInterval() {
//     // given
//     final int numberOfInstances = 3;
//     final String dateVarName = "dateVar";
//     final ProcessDefinitionEngineDto def = deploySimpleServiceTaskProcessAndGetDefinition();
//     OffsetDateTime dateVariableValue = OffsetDateTime.now();
//     final Map<String, Object> variables = new HashMap<>();
//
//     for (int i = 0; i < numberOfInstances; i++) {
//       dateVariableValue = dateVariableValue.plusMinutes(i);
//       variables.put(dateVarName, dateVariableValue);
//       engineIntegrationExtension.startProcessInstance(def.getId(), variables);
//     }
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(def.getKey(), def.getVersionAsString(), dateVarName, VariableType.DATE);
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//     assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
//     // the bucket span covers the earliest and the latest date variable value
//     final DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
//     final OffsetDateTime startOfFirstBucket =
//         OffsetDateTime.from(formatter.parse(resultData.get(0).getKey()));
//     final OffsetDateTime startOfLastBucket =
//         OffsetDateTime.from(
//             formatter.parse(
//                 resultData
//                     .get(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1)
//                     .getKey()));
//     final OffsetDateTime firstTruncatedDateVariableValue =
//         dateVariableValue.truncatedTo(ChronoUnit.MILLIS);
//     final OffsetDateTime lastTruncatedDateVariableValue =
//         dateVariableValue.minusMinutes(numberOfInstances).truncatedTo(ChronoUnit.MILLIS);
//
//     assertThat(startOfFirstBucket).isBeforeOrEqualTo(firstTruncatedDateVariableValue);
//     assertThat(startOfLastBucket).isAfterOrEqualTo(lastTruncatedDateVariableValue);
//
// assertThat(result.getFirstMeasureData().stream().mapToDouble(MapResultEntryDto::getValue).sum())
//         .isEqualTo(3.0); // each instance falls into one bucket
//   }
//
//   @SneakyThrows
//   @Test
//   public void groupByDateVariableForAutomaticInterval_MissingInstancesReturnsEmptyResult() {
//     // given
//     final String dateVarName = "dateVar";
//     final ProcessDefinitionEngineDto def = deploySimpleServiceTaskProcessAndGetDefinition();
//
//     // when
//     final ProcessReportDataDto reportData =
//         createReport(def.getKey(), def.getVersionAsString(), dateVarName, VariableType.DATE);
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//     assertThat(resultData).isEmpty();
//   }
//
//   private String createAndStoreDefaultReportDefinition(
//       final String processDefinitionKey,
//       final String processDefinitionVersion,
//       final String variableName,
//       final VariableType variableType) {
//     final ProcessReportDataDto reportData =
//         createReport(processDefinitionKey, processDefinitionVersion, variableName, variableType);
//     return createNewReport(reportData);
//   }
//
//   private ProcessReportDataDto createReport(
//       final String processDefinitionKey,
//       final String processDefinitionVersion,
//       final String variableName,
//       final VariableType variableType) {
//     return TemplatedProcessReportDataBuilder.createReportData()
//         .setProcessDefinitionKey(processDefinitionKey)
//         .setProcessDefinitionVersion(processDefinitionVersion)
//         .setVariableName(variableName)
//         .setVariableType(variableType)
//         .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE)
//         .build();
//   }
//
//   private ProcessReportDataDto createReport(
//       final String processDefinitionKey,
//       final String processDefinitionVersion,
//       final String variableName,
//       final VariableType variableType,
//       final Double baseline,
//       final Double groupByNumberVariableBucketSize) {
//     final ProcessReportDataDto reportDataDto =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(processDefinitionKey)
//             .setProcessDefinitionVersion(processDefinitionVersion)
//             .setVariableName(variableName)
//             .setVariableType(variableType)
//             .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE)
//             .build();
//     reportDataDto.getConfiguration().getCustomBucket().setActive(true);
//     reportDataDto.getConfiguration().getCustomBucket().setBaseline(baseline);
//     reportDataDto
//         .getConfiguration()
//         .getCustomBucket()
//         .setBucketSize(groupByNumberVariableBucketSize);
//     return reportDataDto;
//   }
//
//   private Integer getVariableInstanceCount(final String variableName) {
//     return databaseIntegrationTestExtension.getVariableInstanceCount(variableName);
//   }
// }
