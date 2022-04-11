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
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.CONTAINS;

public class StringContainsVariableQueryFilterIT extends AbstractFilterIT {

  private static final String STRING_VARIABLE_NAME = "secretSauce";

  @Test
  public void containsFilter_possibleMatchingScenarios() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    // exact match
    variables.put(STRING_VARIABLE_NAME, "ketchup");
    final ProcessInstanceEngineDto exactMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    // matches the beginning
    variables.put(STRING_VARIABLE_NAME, "ketchupMustard");
    final ProcessInstanceEngineDto startMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    // matches the end
    variables.put(STRING_VARIABLE_NAME, "mustardKetchup");
    final ProcessInstanceEngineDto endMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    // a match in between
    variables.put(STRING_VARIABLE_NAME, "mustardKetchupMayonnaise");
    final ProcessInstanceEngineDto middleMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    // case insensitive
    variables.put(STRING_VARIABLE_NAME, "(asokndf249814kETchUpa;rinioanbrair01-34");
    final ProcessInstanceEngineDto caseInsensitive =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "noMatch");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createContainsFilterForValues("ketchup");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(5)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        exactMatch.getId(),
        startMatch.getId(),
        endMatch.getId(),
        middleMatch.getId(),
        caseInsensitive.getId()
      );
  }

  @Test
  public void containsFilter_matchesNoValue() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "mayonnaise");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "mustard");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createContainsFilterForValues("ketchup");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData()).isEmpty();
    assertThat(result.getInstanceCount()).isZero();
  }

  @Test
  public void containsFilter_otherVariableWithMatchingValue() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "ketchup");
    final ProcessInstanceEngineDto shouldMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "mustard");
    variables.put("anotherVar", "ketchup");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createContainsFilterForValues("ketchup");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactly(shouldMatch.getId());
  }

  @Test
  public void containsFilter_nullValueOrNotDefined() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, null);
    final ProcessInstanceEngineDto nullValue =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.remove(STRING_VARIABLE_NAME);
    final ProcessInstanceEngineDto notDefined =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "ketchup");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createContainsFilterForValues((String) null);
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(2)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(nullValue.getId(), notDefined.getId());
  }

  @Test
  public void containsFilter_combineSeveralValues() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "ketchup");
    final ProcessInstanceEngineDto ketchupMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, null);
    final ProcessInstanceEngineDto nullMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "mustard");
    final ProcessInstanceEngineDto mustMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "mayonnaise");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createContainsFilterForValues("ketchup", null, "must");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(3)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(ketchupMatch.getId(), nullMatch.getId(), mustMatch.getId());
  }

  @Test
  public void containsFilter_worksWithVeryLongValues() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "12345678910111213");
    final ProcessInstanceEngineDto shouldMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, "12345678910");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createContainsFilterForValues("1234567891011");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(shouldMatch.getId());
  }

  @Test
  public void containsFilter_missingValueIsNotAllowed() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "ketchup");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createContainsFilterForValues();
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
  public void containsFilter_variableWithDifferentTypeIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "123");
    final ProcessInstanceEngineDto shouldMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put(STRING_VARIABLE_NAME, 123);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createContainsFilterForValues("12");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(shouldMatch.getId());
  }

  @Test
  public void containsFilter_variableWithDifferentNameIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(STRING_VARIABLE_NAME, "123");
    final ProcessInstanceEngineDto shouldMatch =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.remove(STRING_VARIABLE_NAME);
    variables.put(STRING_VARIABLE_NAME + "2", "123");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filter = createContainsFilterForValues("12");
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getData())
      .hasSize(1)
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(shouldMatch.getId());
  }

  private List<ProcessFilterDto<?>> createContainsFilterForValues(final String... valueToFilterFor) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .name(STRING_VARIABLE_NAME)
      .stringType()
      .values(Arrays.asList(valueToFilterFor))
      .operator(CONTAINS)
      .add()
      .buildList();
  }

}
