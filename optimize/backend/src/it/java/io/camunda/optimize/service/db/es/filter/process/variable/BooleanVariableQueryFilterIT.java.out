/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.filter.process.variable;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.google.common.collect.ImmutableMap;
// import com.google.common.collect.Lists;
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
// import io.camunda.optimize.dto.optimize.query.variable.VariableType;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.service.db.es.filter.process.AbstractFilterIT;
// import io.camunda.optimize.test.it.extension.EngineVariableValue;
// import java.util.Collections;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.stream.Stream;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.Arguments;
// import org.junit.jupiter.params.provider.MethodSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class BooleanVariableQueryFilterIT extends AbstractFilterIT {
//
//   private static final String BOOLEAN_VARIABLE_NAME = "var";
//
//   @Test
//   public void booleanTrueVariableFilter() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
//     Map<String, Object> variables = new HashMap<>();
//     variables.put(BOOLEAN_VARIABLE_NAME, true);
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
//     variables.put(BOOLEAN_VARIABLE_NAME, false);
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
//     variables.put(BOOLEAN_VARIABLE_NAME, false);
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     List<ProcessFilterDto<?>> filter =
//         ProcessFilterBuilder.filter()
//             .variable()
//             .name(BOOLEAN_VARIABLE_NAME)
//             .booleanFalse()
//             .add()
//             .buildList();
//
//     ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
//         evaluateReportWithFilter(processDefinition, filter);
//
//     // then
//     assertThat(result.getData()).hasSize(2);
//   }
//
//   @Test
//   public void booleanFalseVariableFilter() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
//     Map<String, Object> variables = new HashMap<>();
//     variables.put(BOOLEAN_VARIABLE_NAME, true);
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
//     variables.put(BOOLEAN_VARIABLE_NAME, true);
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
//     variables.put(BOOLEAN_VARIABLE_NAME, false);
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     List<ProcessFilterDto<?>> filter =
//         ProcessFilterBuilder.filter()
//             .variable()
//             .name(BOOLEAN_VARIABLE_NAME)
//             .booleanTrue()
//             .add()
//             .buildList();
//     ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
//         evaluateReportWithFilter(processDefinition, filter);
//
//     // then
//     assertThat(result.getData()).hasSize(2);
//   }
//
//   private static Stream<Arguments> nullFilterScenarios() {
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
//   public void booleanFilterSupportsNullValue(
//       final List<Boolean> filterValues, final Integer expectedInstanceCount) {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
//     // instance where the variable is undefined
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     // instance where the variable has the value null
//     engineIntegrationExtension.startProcessInstance(
//         processDefinition.getId(),
//         Collections.singletonMap(
//             BOOLEAN_VARIABLE_NAME, new EngineVariableValue(null, VariableType.BOOLEAN.getId())));
//     engineIntegrationExtension.startProcessInstance(
//         processDefinition.getId(), ImmutableMap.of(BOOLEAN_VARIABLE_NAME, true));
//     engineIntegrationExtension.startProcessInstance(
//         processDefinition.getId(), ImmutableMap.of(BOOLEAN_VARIABLE_NAME, false));
//     engineIntegrationExtension.startProcessInstance(
//         processDefinition.getId(), ImmutableMap.of(BOOLEAN_VARIABLE_NAME, false));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final List<ProcessFilterDto<?>> filter =
//         ProcessFilterBuilder.filter()
//             .variable()
//             .booleanType()
//             .name(BOOLEAN_VARIABLE_NAME)
//             .booleanValues(filterValues)
//             .add()
//             .buildList();
//
//     ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
//         evaluateReportWithFilter(processDefinition, filter);
//
//     // then
//     assertThat(result.getData()).hasSize(expectedInstanceCount);
//   }
// }
