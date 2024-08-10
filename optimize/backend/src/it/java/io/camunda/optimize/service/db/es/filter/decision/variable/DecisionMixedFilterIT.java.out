/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.filter.decision.variable;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createBooleanOutputVariableFilter;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createFixedDateInputVariableFilter;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createNumericInputVariableFilter;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRollingEvaluationDateFilter;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createStringInputVariableFilter;
// import static io.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;
// import static io.camunda.optimize.util.DmnModels.INPUT_CATEGORY_ID;
// import static io.camunda.optimize.util.DmnModels.INPUT_INVOICE_DATE_ID;
// import static io.camunda.optimize.util.DmnModels.OUTPUT_AUDIT_ID;
// import static io.camunda.optimize.util.DmnModels.createDecisionDefinitionWithDate;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.google.common.collect.Lists;
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.service.db.es.report.decision.AbstractDecisionDefinitionIT;
// import io.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
// import io.camunda.optimize.test.util.decision.DecisionReportDataType;
// import java.time.OffsetDateTime;
// import java.util.Collections;
// import java.util.List;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class DecisionMixedFilterIT extends AbstractDecisionDefinitionIT {
//
//   @Test
//   public void createAndEvaluateReportWithAllFilterTypes() {
//     // given
//     final OffsetDateTime dateTimeInputFilterStart =
//         OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
//     final double expectedAmountValue = 200.0;
//     final String expectedCategory = "Misc";
//
//     final DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition(createDecisionDefinitionWithDate());
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(), createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00"));
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(),
//         createInputsWithDate(expectedAmountValue, "2019-06-06T00:00:00+00:00"));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final DecisionReportDataDto reportData =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
//             .setDecisionDefinitionVersion(ALL_VERSIONS)
//             .setReportDataType(DecisionReportDataType.RAW_DATA)
//             .build();
//
//     final InputVariableFilterDto fixedDateInputVariableFilter =
//         createFixedDateInputVariableFilter(INPUT_INVOICE_DATE_ID, dateTimeInputFilterStart,
// null);
//     final InputVariableFilterDto doubleInputVariableFilter =
//         createNumericInputVariableFilter(
//             INPUT_AMOUNT_ID, FilterOperator.IN, String.valueOf(expectedAmountValue));
//
//     final InputVariableFilterDto stringInputVariableFilter =
//         createStringInputVariableFilter(INPUT_CATEGORY_ID, FilterOperator.IN, expectedCategory);
//     final OutputVariableFilterDto booleanOutputVariableFilter =
//         createBooleanOutputVariableFilter(OUTPUT_AUDIT_ID, Collections.singletonList(false));
//     final EvaluationDateFilterDto rollingEvaluationDateFilter =
//         createRollingEvaluationDateFilter(1L, DateUnit.DAYS);
//
//     reportData.setFilter(
//         Lists.newArrayList(
//             fixedDateInputVariableFilter,
//             doubleInputVariableFilter,
//             stringInputVariableFilter,
//             booleanOutputVariableFilter,
//             rollingEvaluationDateFilter));
//     final String reportId = reportClient.createSingleDecisionReport(reportData);
//     final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReportById(reportId).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(1L);
//     assertThat(result.getData()).hasSize(1);
//     assertThat(
//             (String)
//
// result.getData().get(0).getInputVariables().get(INPUT_INVOICE_DATE_ID).getValue())
//         .startsWith("2019-06-06T00:00:00");
//   }
// }
