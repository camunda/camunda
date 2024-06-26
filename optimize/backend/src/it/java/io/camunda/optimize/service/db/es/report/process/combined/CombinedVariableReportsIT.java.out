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
// import static
// io.camunda.optimize.service.util.ProcessReportDataBuilderHelper.createCombinedReportData;
// import static
// io.camunda.optimize.service.util.ProcessReportDataType.VARIABLE_AGGREGATION_GROUP_BY_NONE;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.google.common.collect.ImmutableMap;
// import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
// import io.camunda.optimize.dto.optimize.query.variable.VariableType;
// import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
// import io.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
// import io.camunda.optimize.service.db.es.report.process.AbstractProcessDefinitionIT;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import jakarta.ws.rs.core.Response;
// import java.util.Map;
// import org.junit.jupiter.api.Test;
//
// public class CombinedVariableReportsIT extends AbstractProcessDefinitionIT {
//
//   private static final String TEST_VARIABLE = "var";
//
//   @Test
//   public void combineVariableAggregationReports() {
//     // given
//     Map<String, Object> variables = ImmutableMap.of(TEST_VARIABLE, 1);
//     deployAndStartSimpleProcessWithVariables(variables);
//     String singleReportId1 = createVariableReport();
//     String singleReportId2 = createVariableReport();
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     CombinedProcessReportResultDataDto<Double> result =
//         reportClient.evaluateUnsavedCombined(
//             createCombinedReportData(singleReportId1, singleReportId2));
//
//     // then
//     Map<String, AuthorizedProcessReportEvaluationResponseDto<Double>> resultMap =
// result.getData();
//     assertThat(resultMap).hasSize(2);
//     assertThat(resultMap.keySet()).contains(singleReportId1, singleReportId2);
//   }
//
//   @Test
//   public void variableAggregationReportIsNotCombinableWithOtherViewReports() {
//     // given
//     Map<String, Object> variables = ImmutableMap.of(TEST_VARIABLE, 1);
//     deployAndStartSimpleProcessWithVariables(variables);
//     String variableReport = createVariableReport();
//     String frequencyReport = createProcessInstanceFrequencyReport();
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final CombinedReportDataDto combinedReportData =
//         createCombinedReportData(variableReport, frequencyReport);
//     CombinedReportDefinitionRequestDto combinedReport = new CombinedReportDefinitionRequestDto();
//     combinedReport.setData(combinedReportData);
//
//     // when
//     Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildCreateCombinedReportRequest(combinedReport)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
//   }
//
//   @Test
//   public void combineDifferentAggregationTypes() {
//     // given
//     Map<String, Object> variables = ImmutableMap.of(TEST_VARIABLE, 1);
//     deployAndStartSimpleProcessWithVariables(variables);
//     String singleReportId1 = createVariableReport(new AggregationDto(AggregationType.SUM));
//     String singleReportId2 = createVariableReport(new AggregationDto(AggregationType.AVERAGE));
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     CombinedProcessReportResultDataDto<Double> result =
//         reportClient.evaluateUnsavedCombined(
//             createCombinedReportData(singleReportId1, singleReportId2));
//
//     // then
//     assertThat(result.getData()).containsOnlyKeys(singleReportId1, singleReportId2);
//   }
//
//   private String createVariableReport() {
//     return createVariableReport(new AggregationDto(AggregationType.AVERAGE));
//   }
//
//   private String createVariableReport(final AggregationDto aggregationType) {
//     ProcessReportDataDto data =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(TEST_PROCESS)
//             .setProcessDefinitionVersion("1")
//             .setVariableName(TEST_VARIABLE)
//             .setVariableType(VariableType.INTEGER)
//             .setReportDataType(VARIABLE_AGGREGATION_GROUP_BY_NONE)
//             .build();
//     data.getConfiguration().setAggregationTypes(aggregationType);
//     SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
//         new SingleProcessReportDefinitionRequestDto();
//     singleProcessReportDefinitionDto.setData(data);
//     return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
//   }
//
//   private String createProcessInstanceFrequencyReport() {
//     ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(TEST_PROCESS)
//             .setProcessDefinitionVersion("1")
//             .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
//             .build();
//     SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
//         new SingleProcessReportDefinitionRequestDto();
//     singleProcessReportDefinitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
//     return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
//   }
// }
