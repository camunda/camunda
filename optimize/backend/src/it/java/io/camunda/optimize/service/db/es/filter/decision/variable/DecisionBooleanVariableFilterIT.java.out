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
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createBooleanInputVariableFilter;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createBooleanOutputVariableFilter;
// import static io.camunda.optimize.util.DmnModels.OUTPUT_AUDIT_ID;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.google.common.collect.ImmutableMap;
// import com.google.common.collect.Lists;
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.service.db.es.report.decision.AbstractDecisionDefinitionIT;
// import io.camunda.optimize.test.it.extension.EngineVariableValue;
// import io.camunda.optimize.test.util.decision.DecisionTypeRef;
// import java.util.Collections;
// import java.util.List;
// import java.util.stream.Stream;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.Arguments;
// import org.junit.jupiter.params.provider.MethodSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class DecisionBooleanVariableFilterIT extends AbstractDecisionDefinitionIT {
//
//   @Test
//   public void resultFilterByEqualBooleanOutputVariable() {
//     // given
//     final Boolean outputAuditValueToFilterFor = true;
//     final String outputVariableIdToFilterOn = OUTPUT_AUDIT_ID;
//
//     final DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition();
//     // results in Audit=false
//     startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(100.0,
// "Misc"));
//     // results in Audit=true
//     startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(2000.0,
// "Misc"));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
//     reportData.setFilter(
//         Lists.newArrayList(
//             createBooleanOutputVariableFilter(
//                 outputVariableIdToFilterOn,
//                 Collections.singletonList(outputAuditValueToFilterFor))));
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(1L);
//     assertThat(result.getData()).hasSize(1);
//
//     assertThat(
//             Boolean.valueOf(
//                 result
//                     .getData()
//                     .get(0)
//                     .getOutputVariables()
//                     .get(outputVariableIdToFilterOn)
//                     .getFirstValue()
//                     .toString()))
//         .isEqualTo(outputAuditValueToFilterFor);
//   }
//
//   public static Stream<Arguments> nullFilterScenarios() {
//     return Stream.of(
//         Arguments.of(Collections.singletonList(null), 2),
//         Arguments.of(Lists.newArrayList(null, true), 3),
//         Arguments.of(Collections.singletonList(null), 2),
//         Arguments.of(Lists.newArrayList(null, false), 4),
//         Arguments.of(Lists.newArrayList(null, false, true), 5));
//   }
//
//   @ParameterizedTest
//   @MethodSource("nullFilterScenarios")
//   public void resultFilterBooleanInputVariableSupportsNullValue(
//       final List<Boolean> filterValues, final Integer expectedInstanceCount) {
//     // given
//     final String inputClauseId = "booleanVar";
//     final String booleanVarName = inputClauseId;
//     final DecisionDefinitionEngineDto decisionDefinitionDto =
//         deploySimpleInputDecisionDefinition(inputClauseId, booleanVarName,
// DecisionTypeRef.STRING);
//
//     // instance where the variable is not defined
//     engineIntegrationExtension.startDecisionInstance(
//         decisionDefinitionDto.getId(), Collections.singletonMap(booleanVarName, null));
//     // instance where the variable has the value null
//     engineIntegrationExtension.startDecisionInstance(
//         decisionDefinitionDto.getId(),
//         Collections.singletonMap(booleanVarName, new EngineVariableValue(null, "Boolean")));
//     engineIntegrationExtension.startDecisionInstance(
//         decisionDefinitionDto.getId(), ImmutableMap.of(booleanVarName, true));
//     engineIntegrationExtension.startDecisionInstance(
//         decisionDefinitionDto.getId(), ImmutableMap.of(booleanVarName, false));
//     engineIntegrationExtension.startDecisionInstance(
//         decisionDefinitionDto.getId(), ImmutableMap.of(booleanVarName, false));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
//     reportData.setFilter(
//         Lists.newArrayList(createBooleanInputVariableFilter(inputClauseId, filterValues)));
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getData()).hasSize(expectedInstanceCount);
//   }
// }
