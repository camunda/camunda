/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.IN;

public class VariableQueryFilterIT extends AbstractFilterIT {

  @Test
  public void excludeUndefinedDoesNotOverwriteOtherFilterData() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    Map<String, Object> variables = new HashMap<>();
    variables.put("testVar", "withValue");
    final String expectedInstanceId1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      variables
    ).getId();
    final String expectedInstanceId2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      variables
    ).getId();

    variables.put("testVar", new EngineVariableValue(null, "String"));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    variables = new HashMap<>();
    variables.put("differentStringValue", "test");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filters =
      ProcessFilterBuilder
        .filter()
        .variable()
        .stringType()
        .excludeUndefined()
        .name("testVar")
        .values(Collections.singletonList("withValue"))
        .operator(FilterOperatorConstants.IN)
        .add()
        .buildList();

    RawDataProcessReportResultDto result =
      evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

    // then
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getData())
      .extracting(processInstanceDto -> processInstanceDto.getProcessInstanceId())
      .containsExactly(expectedInstanceId2, expectedInstanceId1);
  }

  @Test
  public void excludeUndefinedAndNullWorksWithAllVariableTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    final Map<String, VariableType> varNameToTypeMap = createVarNameToTypeMap();
    Map<String, Object> variables = new HashMap<>();

    variables.put("dateVar", null);
    variables.put("boolVar", null);
    variables.put("shortVar", null);
    variables.put("intVar", null);
    variables.put("longVar", null);
    variables.put("doubleVar", null);
    variables.put("stringVar", null);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    variables = new HashMap<>();
    variables.put("dateVar", new EngineVariableValue(null, "Date"));
    variables.put("boolVar", new EngineVariableValue(null, "Boolean"));
    variables.put("shortVar", new EngineVariableValue(null, "Short"));
    variables.put("intVar", new EngineVariableValue(null, "Integer"));
    variables.put("longVar", new EngineVariableValue(null, "Long"));
    variables.put("doubleVar", new EngineVariableValue(null, "Double"));
    variables.put("stringVar", new EngineVariableValue(null, "String"));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    variables = new HashMap<>();
    final OffsetDateTime now = OffsetDateTime.now();
    variables.put("dateVar", now);
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");

    final String expectedInstanceId = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      variables
    ).getId();

    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();


    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      VariableType variableType = varNameToTypeMap.get(entry.getKey());

      List<ProcessFilterDto<?>> filters = ProcessFilterBuilder.filter()
        .variable()
        .type(variableType)
        .excludeUndefined()
        .name(entry.getKey())
        .values(Collections.singletonList(entry.getValue().toString()))
        .fixedDate(now, now)
        .operator(FilterOperatorConstants.IN)
        .add()
        .buildList();

      RawDataProcessReportResultDto result =
        evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

      // then
      assertThat(result.getData()).hasSize(1);
      assertThat(result.getData())
        .extracting(instance -> instance.getProcessInstanceId())
        .containsExactly(expectedInstanceId);
    }
  }

  @Test
  public void filterForUndefinedOverwritesOtherFilterData() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    Map<String, Object> variables = new HashMap<>();
    variables.put("testVar", "withValue");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    variables.put("testVar", null);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    variables.put("testVar", new EngineVariableValue(null, "String"));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    variables = new HashMap<>();
    variables.put("differentStringValue", "test");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessFilterDto<?>> filters =
      ProcessFilterBuilder
        .filter()
        .variable()
        .stringType()
        .filterForUndefined()
        .name("testVar")
        .values(Collections.singletonList("withValue"))
        .operator(FilterOperatorConstants.IN)
        .add()
        .buildList();

    RawDataProcessReportResultDto result =
      evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

    // then
    assertThat(result.getData()).hasSize(4);
  }

  @Test
  public void filterForUndefinedAndNullWorksWithAllVariableTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();

    Map<String, VariableType> varNameToTypeMap = createVarNameToTypeMap();
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now());
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");

    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    variables = new HashMap<>();
    variables.put("dateVar", null);
    variables.put("boolVar", null);
    variables.put("shortVar", null);
    variables.put("intVar", null);
    variables.put("longVar", null);
    variables.put("doubleVar", null);
    variables.put("stringVar", null);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    variables = new HashMap<>();
    variables.put("dateVar", new EngineVariableValue(null, "Date"));
    variables.put("boolVar", new EngineVariableValue(null, "Boolean"));
    variables.put("shortVar", new EngineVariableValue(null, "Short"));
    variables.put("intVar", new EngineVariableValue(null, "Integer"));
    variables.put("longVar", new EngineVariableValue(null, "Long"));
    variables.put("doubleVar", new EngineVariableValue(null, "Double"));
    variables.put("stringVar", new EngineVariableValue(null, "String"));
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();


    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      VariableType variableType = varNameToTypeMap.get(entry.getKey());

      List<ProcessFilterDto<?>> filters =
        ProcessFilterBuilder
          .filter()
          .variable()
          .name(entry.getKey())
          .type(variableType)
          .filterForUndefined()
          .add()
          .buildList();

      RawDataProcessReportResultDto result =
        evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

      // then
      assertThat(result.getData()).hasSize(3);
    }
  }

  @Test
  public void validationExceptionOnNullValueField() {
    //given
    List<ProcessFilterDto<?>> variableFilterDto = ProcessFilterBuilder.filter()
      .variable()
      .booleanType()
      .values(null)
      .name("foo")
      .add()
      .buildList();

    // when
    Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void validationExceptionOnNullNumericValuesField() {
    //given
    List<ProcessFilterDto<?>> variableFilterDto = ProcessFilterBuilder
      .filter()
      .variable()
      .longType()
      .operator(IN)
      .values(null)
      .name("foo")
      .add()
      .buildList();

    // when
    Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void validationExceptionOnNullNameField() {
    //given
    List<ProcessFilterDto<?>> variableFilterDto = ProcessFilterBuilder.filter()
      .variable()
      .booleanTrue()
      .name(null)
      .add()
      .buildList();

    // when
    Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private Map<String, VariableType> createVarNameToTypeMap() {
    Map<String, VariableType> varToType = new HashMap<>();
    varToType.put("dateVar", VariableType.DATE);
    varToType.put("boolVar", VariableType.BOOLEAN);
    varToType.put("shortVar", VariableType.SHORT);
    varToType.put("intVar", VariableType.INTEGER);
    varToType.put("longVar", VariableType.LONG);
    varToType.put("doubleVar", VariableType.DOUBLE);
    varToType.put("stringVar", VariableType.STRING);
    return varToType;
  }

  private Response evaluateReportWithFilterAndGetResponse(List<ProcessFilterDto<?>> filterList) {
    final String TEST_DEFINITION_KEY = "testDefinition";
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(TEST_DEFINITION_KEY)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.setFilter(filterList);
    return reportClient.evaluateReportAndReturnResponse(reportData);
  }

}
