/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process.variable;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.filter.process.AbstractFilterIT;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;

public class MultiVariableQueryFilterIT extends AbstractFilterIT {

  public final String STRING_VAR_NAME = "stringVar";
  public final String INT_VAR_NAME = "intVar";
  public final String BOOL_VAR_NAME = "boolVar";
  public final String DOUBLE_VAR_NAME = "doubleVar";

  @Test
  public void multipleVariableFilter_bothConditionsOfOrLogicAreSatisfied() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(STRING_VAR_NAME, "value"));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(BOOL_VAR_NAME, true));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(STRING_VAR_NAME, "otherValue"));
    importAllEngineEntitiesFromScratch();

    final List<VariableFilterDto> processFilterDtos = ProcessFilterBuilder.filter()
      .variable()
      .name(STRING_VAR_NAME)
      .stringType()
      .values(Collections.singletonList("value"))
      .operator(IN)
      .add()
      .variable()
      .name(STRING_VAR_NAME)
      .stringType()
      .values(Collections.singletonList("otherValue"))
      .operator(IN)
      .add()
      .buildList();

    List<ProcessFilterDto<?>> filter = buildFilter(processFilterDtos);

    // when
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(2);
  }

  @Test
  public void multipleVariableFilter_oneConditionOfOrLogicIsSatisfied() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(DOUBLE_VAR_NAME, 1.0));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(BOOL_VAR_NAME, true));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(INT_VAR_NAME, 2));
    importAllEngineEntitiesFromScratch();

    final List<VariableFilterDto> processFilterDtos = ProcessFilterBuilder.filter()
      .variable()
      .name(INT_VAR_NAME)
      .integerType()
      .operator(IN)
      .values(Collections.singletonList("2"))
      .add()
      .variable()
      .name(DOUBLE_VAR_NAME)
      .operator(IN)
      .doubleType()
      .values(Collections.singletonList("2.0"))
      .add()
      .buildList();

    List<ProcessFilterDto<?>> filter = buildFilter(processFilterDtos);

    // when
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(1);
  }

  @Test
  public void multipleVariableFilter_noConditionsOfOrLogicAreSatisfied() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(INT_VAR_NAME, 1));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(BOOL_VAR_NAME, true));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(STRING_VAR_NAME, "otherValue"));
    importAllEngineEntitiesFromScratch();

    final List<VariableFilterDto> processFilterDtos = ProcessFilterBuilder.filter()
      .variable()
      .name(STRING_VAR_NAME)
      .stringType()
      .values(Collections.singletonList("someCondition"))
      .operator(IN)
      .add()
      .variable()
      .name(INT_VAR_NAME)
      .operator(IN)
      .integerType()
      .values(Collections.singletonList("2"))
      .add()
      .buildList();

    List<ProcessFilterDto<?>> filter = buildFilter(processFilterDtos);

    // when
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).isEmpty();
  }

  @Test
  public void multipleVariableFilter_onlyOneConditionExistsInTheMultiVariableFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(INT_VAR_NAME, 1));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(BOOL_VAR_NAME, true));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(STRING_VAR_NAME, "otherValue"));
    importAllEngineEntitiesFromScratch();

    final List<VariableFilterDto> processFilterDtos = ProcessFilterBuilder.filter()
      .variable()
      .name(INT_VAR_NAME)
      .operator(IN)
      .integerType()
      .values(Collections.singletonList("1"))
      .add()
      .buildList();

    List<ProcessFilterDto<?>> filter = buildFilter(processFilterDtos);

    // when
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(1);
  }

  @Test
  public void multipleVariableFilter_filtersCorrectlyWhenCombinedWithOtherFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(INT_VAR_NAME, 1));
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks("group");
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
    importAllEngineEntitiesFromScratch();

    final List<VariableFilterDto> processFilterDtos = ProcessFilterBuilder.filter()
      .variable()
      .name(INT_VAR_NAME)
      .operator(IN)
      .integerType()
      .values(Collections.singletonList("1"))
      .add()
      .buildList();

    List<ProcessFilterDto<?>> filter = ProcessFilterBuilder
      .filter()
      .candidateGroups()
      .id("group")
      .inOperator()
      .add()
      .multipleVariable()
      .variableFilters(processFilterDtos)
      .add()
      .buildList();

    // when
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).isEmpty();
  }

  @Test
  public void multipleVariableFilter_appliedToReportWithNoData() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    final List<VariableFilterDto> processFilterDtos = ProcessFilterBuilder.filter()
      .variable()
      .name(INT_VAR_NAME)
      .operator(IN)
      .integerType()
      .values(Collections.singletonList("1"))
      .add()
      .buildList();

    List<ProcessFilterDto<?>> filter = buildFilter(processFilterDtos);

    // when
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).isEmpty();
  }

  @Test
  public void multipleVariableFilter_variableInTheFilterDoesNotExist() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(INT_VAR_NAME, 1));
    importAllEngineEntitiesFromScratch();

    final List<VariableFilterDto> processFilterDtos = ProcessFilterBuilder.filter()
      .variable()
      .name("varDoesntExist")
      .operator(IN)
      .integerType()
      .values(Collections.singletonList("1"))
      .add()
      .buildList();

    List<ProcessFilterDto<?>> filter = buildFilter(processFilterDtos);

    // when
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).isEmpty();
  }

  @Test
  public void multipleVariableFilter_twoMultipleVariableFilters() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(INT_VAR_NAME, 1));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), Map.of(STRING_VAR_NAME, "stringValue"));
    engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      Map.of(STRING_VAR_NAME, "stringValue2", BOOL_VAR_NAME, true)
    );
    engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      Map.of(
        STRING_VAR_NAME,
        "stringValue",
        BOOL_VAR_NAME,
        true,
        INT_VAR_NAME,
        1,
        "stringVar2",
        "stringValue2"
      )
    );

    importAllEngineEntitiesFromScratch();

    final List<VariableFilterDto> processFilterDtos1 = ProcessFilterBuilder.filter()
      .variable()
      .name(INT_VAR_NAME)
      .operator(IN)
      .integerType()
      .values(Collections.singletonList("1"))
      .add()
      .variable()
      .operator(IN)
      .name(STRING_VAR_NAME)
      .stringType()
      .values(Collections.singletonList("stringValue"))
      .add()
      .buildList();

    final List<VariableFilterDto> processFilterDtos2 = ProcessFilterBuilder.filter()
      .variable()
      .name(BOOL_VAR_NAME)
      .operator(IN)
      .booleanTrue()
      .add()
      .variable()
      .name("stringVar2")
      .stringType()
      .values(Collections.singletonList("stringValue2"))
      .operator(IN)
      .add()
      .buildList();

    List<ProcessFilterDto<?>> filter = ProcessFilterBuilder
      .filter()
      .multipleVariable()
      .variableFilters(processFilterDtos1)
      .add()
      .multipleVariable()
      .variableFilters(processFilterDtos2)
      .add()
      .buildList();

    // when
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(
      processDefinition,
      filter
    );

    // then
    assertThat(result.getData()).hasSize(1);
  }

  public List<ProcessFilterDto<?>> buildFilter(final List<VariableFilterDto> processFilterDtos) {
    return ProcessFilterBuilder
      .filter()
      .multipleVariable()
      .variableFilters(processFilterDtos)
      .add()
      .buildList();
  }
}
