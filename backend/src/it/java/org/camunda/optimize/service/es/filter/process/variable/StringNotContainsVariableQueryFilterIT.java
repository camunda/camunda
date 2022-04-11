/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process.variable;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.filter.process.AbstractFilterIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_CONTAINS;

public class StringNotContainsVariableQueryFilterIT extends AbstractFilterIT {

  private static final String STRING_VARIABLE_NAME = "secretSauce";

  @Test
  public void notContainsFilter_possibleMatchingScenarios() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    // exact match
    variables.put(STRING_VARIABLE_NAME, "ketchup");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    // matches the beginning
    variables.put(STRING_VARIABLE_NAME, "ketchupMustard");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    // matches the end
    variables.put(STRING_VARIABLE_NAME, "mustardKetchup");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    // a match in between
    variables.put(STRING_VARIABLE_NAME, "mustardKetchupMayonnaise");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    // case insensitive
    variables.put(STRING_VARIABLE_NAME, "(asokndf249814kETchUpa;rinioanbrair01-34");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "noMatch");
    final ProcessInstanceEngineDto doesNotMatchValue =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createNotContainsFilterForValues("ketchup");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(doesNotMatchValue.getId());
  }

  @Test
  public void notContainsFilter_matchesNoValue() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "Ketchup");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "123ketchup");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createNotContainsFilterForValues("ketchup");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData()).isEmpty();
    assertThat(result.getInstanceCount()).isZero();
  }

  @Test
  public void notContainsFilter_otherVariableWithMatchingValue() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "ketchup");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "mustard");
    variables.put("anotherVar", "ketchup");
    final ProcessInstanceEngineDto shouldNotMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createNotContainsFilterForValues("ketchup");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactly(shouldNotMatch.getId());
  }

  @Test
  public void notContainsFilter_nullValueOrNotDefined() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, null);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.remove(STRING_VARIABLE_NAME);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "ketchup");
    final ProcessInstanceEngineDto noNullValue =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createNotContainsFilterForValues((String) null);
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(noNullValue.getId());
  }

  @Test
  public void notContainsFilter_combineSeveralValues() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "ketchup");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, null);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "mustard");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    // variable not defined
    variables.remove(STRING_VARIABLE_NAME);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    variables.put(STRING_VARIABLE_NAME, "mayonnaise");
    final ProcessInstanceEngineDto noneOfTheGivenValuesMatches =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createNotContainsFilterForValues("ketchup", null, "must");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(noneOfTheGivenValuesMatches.getId());
  }

  @Test
  public void notContainsFilter_worksWithVeryLongValues() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "12345678910111213");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "12345678910");
    final ProcessInstanceEngineDto shouldMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createNotContainsFilterForValues("1234567891011");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(shouldMatch.getId());
  }

  @Test
  public void notContainsFilter_missingValueIsNotAllowed() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "ketchup");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createNotContainsFilterForValues();
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinition.getKey())
      .setProcessDefinitionVersion(processDefinition.getVersionAsString())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .setFilter(filter)
      .build();
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void notContainsFilter_variableWithDifferentTypeIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "123");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, 123);
    final ProcessInstanceEngineDto shouldNotMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createNotContainsFilterForValues("12");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(shouldNotMatch.getId());
  }

  @Test
  public void notContainsFilter_variableWithDifferentNameIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "123");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.remove(STRING_VARIABLE_NAME);
    variables.put(STRING_VARIABLE_NAME + "2", "123");
    final ProcessInstanceEngineDto shouldNotMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createNotContainsFilterForValues("12");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(shouldNotMatch.getId());
  }

  private List<ProcessFilterDto<?>> createNotContainsFilterForValues(final String... valueToFilterFor) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .name(STRING_VARIABLE_NAME)
      .stringType()
      .values(Arrays.asList(valueToFilterFor))
      .operator(NOT_CONTAINS)
      .add()
      .buildList();
  }

}
