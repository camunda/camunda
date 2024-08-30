/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.report.decision;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.util.DmnModels.createDefaultDmnModel;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.fasterxml.jackson.core.type.TypeReference;
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
// import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
// import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
// import io.camunda.optimize.dto.optimize.query.variable.VariableType;
// import
// io.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.exception.OptimizeIntegrationTestException;
// import io.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
// import io.camunda.optimize.test.util.decision.DecisionReportDataType;
// import jakarta.ws.rs.core.Response;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.EnumSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class SingleDecisionReportHandlingIT extends AbstractPlatformIT {
//
//   @Test
//   public void updateDecisionReportWithGroupByInputVariableName() {
//     // given
//     String id = reportClient.createEmptySingleDecisionReport();
//
//     final String variableName = "variableName";
//     DecisionReportDataDto expectedReportData =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey("ID")
//             .setDecisionDefinitionVersion("1")
//
// .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
//             .setVariableId("id")
//             .setVariableName(variableName)
//             .build();
//
//     SingleDecisionReportDefinitionRequestDto report =
//         new SingleDecisionReportDefinitionRequestDto();
//     report.setData(expectedReportData);
//
//     // when
//     reportClient.updateDecisionReport(id, report);
//     List<ReportDefinitionDto> reports = getAllPrivateReports();
//
//     // then
//     assertThat(reports.size()).isEqualTo(1);
//     SingleDecisionReportDefinitionRequestDto reportFromApi =
//         (SingleDecisionReportDefinitionRequestDto) reports.get(0);
//     final DecisionGroupByVariableValueDto value =
//         (DecisionGroupByVariableValueDto) reportFromApi.getData().getGroupBy().getValue();
//     assertThat(value.getName().isPresent()).isEqualTo(true);
//     assertThat(value.getName().get()).isEqualTo(variableName);
//   }
//
//   @ParameterizedTest
//   @EnumSource(DecisionReportDataType.class)
//   public void evaluateReport_missingInstancesReturnsEmptyResult(DecisionReportDataType
// reportType) {
//     // given
//     final String reportId = deployDefinitionAndCreateReport(reportType);
//
//     // when
//     final ReportResultResponseDto<?> result =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildEvaluateSavedReportRequest(reportId)
//             .execute(new TypeReference<AuthorizedDecisionReportEvaluationResponseDto<?>>() {})
//             .getResult();
//
//     // then
//     assertEmptyResult(result);
//   }
//
//   private void assertEmptyResult(final ReportResultResponseDto<?> result) {
//     assertThat(result.getInstanceCount()).isZero();
//     assertThat(result.getInstanceCountWithoutFilters()).isZero();
//     if (result.getFirstMeasureData() instanceof List) {
//       assertThat((List<?>) result.getFirstMeasureData()).isEmpty();
//     } else if (result.getFirstMeasureData() instanceof Double) {
//       assertThat((Double) result.getFirstMeasureData()).isZero();
//     } else {
//       throw new OptimizeIntegrationTestException(
//           "Unexpected result type: " + result.getFirstMeasureData().getClass());
//     }
//   }
//
//   private String deployDefinitionAndCreateReport(final DecisionReportDataType reportType) {
//     final DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition(
//             createDefaultDmnModel(
//                 "TestDecision_evaluateReport_missingInstanceIndicesReturnsEmptyResult"));
//
//     final DecisionReportDataDto expectedReportData =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
//             .setDecisionDefinitionVersion("1")
//             .setReportDataType(reportType)
//             .setVariableId("variableId")
//             .setVariableName("variableName")
//             .setVariableType(VariableType.STRING)
//             .setDateInterval(AggregateByDateUnit.DAY)
//             .build();
//     final SingleDecisionReportDefinitionRequestDto report =
//         new SingleDecisionReportDefinitionRequestDto();
//     report.setData(expectedReportData);
//     return reportClient.createSingleDecisionReport(report);
//   }
//
//   private List<ReportDefinitionDto> getAllPrivateReports() {
//     return getAllPrivateReportsWithQueryParam(new HashMap<>());
//   }
//
//   private List<ReportDefinitionDto> getAllPrivateReportsWithQueryParam(
//       Map<String, Object> queryParams) {
//     return embeddedOptimizeExtension
//         .getRequestExecutor()
//         .buildGetAllPrivateReportsRequest()
//         .addQueryParams(queryParams)
//         .executeAndReturnList(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
//   }
// }
