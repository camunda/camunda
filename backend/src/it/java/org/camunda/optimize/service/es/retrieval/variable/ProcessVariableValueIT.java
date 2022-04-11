/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableReportValuesRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.VariableDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.BOOLEAN;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DATE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DOUBLE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.INTEGER;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.LONG;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.OBJECT;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.SHORT;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;

public class ProcessVariableValueIT extends AbstractVariableIT {

  @Test
  public void getVariableValues() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<String> variableResponse = variablesClient.getProcessVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size()).isEqualTo(3);
    assertThat(variableResponse.contains("value1")).isTrue();
    assertThat(variableResponse.contains("value2")).isTrue();
    assertThat(variableResponse.contains("value3")).isTrue();
  }

  @Test
  public void getVariableValueForInstance() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    final Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    final ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId(),
      variables
    );
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    final List<String> variableResponse = variablesClient.getProcessVariableValues(
      instance.getId(),
      processDefinition,
      "var"
    );

    // then
    assertThat(variableResponse).singleElement().isEqualTo("value1");
  }

  @Test
  public void getVariableValuesForReport_reportWithNoDefinitionKey() {
    // given
    final String reportId = reportClient.createEmptySingleProcessReport();
    final ProcessVariableReportValuesRequestDto requestDto = new ProcessVariableReportValuesRequestDto();
    requestDto.setReportIds(Collections.singletonList(reportId));
    requestDto.setType(BOOLEAN);
    requestDto.setName("varName");

    // when
    final List<String> values = embeddedOptimizeExtension.getRequestExecutor()
      .buildProcessVariableValuesForReportsRequest(requestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(values).isEmpty();
  }

  @Test
  public void getVariableValuesSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Lists.newArrayList(tenantId1);
    String variableName = "aVariableName";
    String processDefinition = deployAndStartMultiTenantUserTaskProcess(
      variableName,
      Lists.newArrayList(null, tenantId1, tenantId2)
    );
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto valueRequestDto = new ProcessVariableValueRequestDto();
    valueRequestDto.setProcessDefinitionKey(processDefinition);
    valueRequestDto.setProcessDefinitionVersion(ALL_VERSIONS);
    valueRequestDto.setTenantIds(selectedTenants);
    valueRequestDto.setName(variableName);
    valueRequestDto.setType(STRING);
    List<String> variableResponse = variablesClient.getProcessVariableValues(valueRequestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void getVariableNamesForMultipleDefinitionVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition2.getId(), variables);
    ProcessDefinitionEngineDto processDefinition3 = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition3.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto valueRequestDto = new ProcessVariableValueRequestDto();
    valueRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    valueRequestDto.setProcessDefinitionVersions(ImmutableList.of(
      processDefinition.getVersionAsString(),
      processDefinition3.getVersionAsString()
    ));
    valueRequestDto.setName("var");
    valueRequestDto.setType(STRING);
    List<String> variableResponse = variablesClient.getProcessVariableValues(valueRequestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.get(0)).isEqualTo("value1");
    assertThat(variableResponse.get(1)).isEqualTo("value3");
  }

  @Test
  public void getVariablesForAllVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    processDefinition = deploySimpleProcessDefinition();
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto valueRequestDto = new ProcessVariableValueRequestDto();
    valueRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    valueRequestDto.setProcessDefinitionVersion(ALL_VERSIONS);
    valueRequestDto.setName("var");
    valueRequestDto.setType(STRING);
    List<String> variableResponse = variablesClient.getProcessVariableValues(valueRequestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.get(0)).isEqualTo("value1");
    assertThat(variableResponse.get(1)).isEqualTo("value2");
  }

  @Test
  public void getVariableNamesForLatestVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "first");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    processDefinition = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var", "latest");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto valueRequestDto = new ProcessVariableValueRequestDto();
    valueRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    valueRequestDto.setProcessDefinitionVersion(LATEST_VERSION);
    valueRequestDto.setName("var");
    valueRequestDto.setType(STRING);
    List<String> variableResponse = variablesClient.getProcessVariableValues(valueRequestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
    assertThat(variableResponse.get(0)).isEqualTo("latest");
  }

  @Test
  public void getMoreThan10VariableValues() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    IntStream.range(0, 15).forEach(
      i -> {
        variables.put("var", "value" + i);
        engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      }
    );
    importAllEngineEntitiesFromScratch();

    // when
    List<String> variableResponse = variablesClient.getProcessVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size()).isEqualTo(15);
  }

  @Test
  public void onlyValuesToSpecifiedVariableAreReturned() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<String> variableResponse = variablesClient.getProcessVariableValues(processDefinition, "var1");

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
    assertThat(variableResponse.contains("value1")).isTrue();
  }

  @Test
  public void noValuesFromAnotherProcessDefinition() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition2.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<String> variableResponse = variablesClient.getProcessVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
    assertThat(variableResponse.contains("value1")).isTrue();
  }

  @Test
  public void sameVariableNameWithDifferentType() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", true);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<String> variableResponse = variablesClient.getProcessVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
    assertThat(variableResponse.contains("value1")).isTrue();
  }

  @Test
  public void valuesDoNotContainDuplicates() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<String> variableResponse = variablesClient.getProcessVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
    assertThat(variableResponse.contains("value1")).isTrue();
  }

  @Test
  public void retrieveValuesForAllPrimitiveTypes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, VariableType> varNameToTypeMap = createVarNameToTypeMap();
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now().withOffsetSameLocal(ZoneOffset.UTC));
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    for (String name : variables.keySet()) {
      // when
      VariableType type = varNameToTypeMap.get(name);
      ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
      requestDto.setProcessDefinitionKey(processDefinition.getKey());
      requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
      requestDto.setName(name);
      requestDto.setType(type);
      List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

      // then
      String expectedValue;
      if (name.equals("dateVar")) {
        OffsetDateTime temporal = (OffsetDateTime) variables.get(name);
        expectedValue = embeddedOptimizeExtension.getDateTimeFormatter().format(temporal);
      } else {
        expectedValue = variables.get(name).toString();
      }
      assertThat(variableResponse.size()).isEqualTo(1);
      assertThat(variableResponse.contains(expectedValue)).isTrue();
    }

  }

  @Test
  public void retrieveValuesForObjectVariable() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    final Map<String, Object> kermitVar = new HashMap<>();
    kermitVar.put("name", "Kermit");
    kermitVar.put("likes", List.of("optimize", "Miss Piggy"));
    VariableDto kermitObjectDto = variablesClient.createMapJsonObjectVariableDto(kermitVar);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("objectVar", kermitObjectDto);
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    final Map<String, Object> missPiggyVar = new HashMap<>();
    missPiggyVar.put("name", "Miss Piggy");
    missPiggyVar.put("likes", List.of("Kermit"));
    VariableDto missPiggyObjectDto = variablesClient.createMapJsonObjectVariableDto(missPiggyVar);
    variables.put("objectVar", missPiggyObjectDto);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    final List<String> variableResponse =
      variablesClient.getProcessVariableValues(instance.getId(), processDefinition, "objectVar", OBJECT);

    // then
    assertThat(variableResponse)
      .singleElement()
      .isEqualTo(kermitObjectDto.getValue());
  }

  private Map<String, VariableType> createVarNameToTypeMap() {
    Map<String, VariableType> varToType = new HashMap<>();
    varToType.put("dateVar", DATE);
    varToType.put("boolVar", BOOLEAN);
    varToType.put("shortVar", SHORT);
    varToType.put("intVar", INTEGER);
    varToType.put("longVar", LONG);
    varToType.put("doubleVar", DOUBLE);
    varToType.put("stringVar", STRING);
    return varToType;
  }

  @Test
  public void valuesListIsCutByMaxResults() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setNumResults(2);
    List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.contains("value1")).isTrue();
    assertThat(variableResponse.contains("value2")).isTrue();
  }

  @Test
  public void valuesListIsCutByAnOffset() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setResultOffset(1);
    List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.contains("value2")).isTrue();
    assertThat(variableResponse.contains("value3")).isTrue();
  }

  @Test
  public void valuesListIsCutByAnOffsetAndMaxResults() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setNumResults(1);
    requestDto.setResultOffset(1);
    List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
    assertThat(variableResponse.contains("value2")).isTrue();
  }

  @Test
  public void getOnlyValuesWithSpecifiedPrefix() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "fooo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "bar");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "ball");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("ba");
    List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.contains("bar")).isTrue();
    assertThat(variableResponse.contains("ball")).isTrue();
  }

  @Test
  public void variableValueFromDifferentVariablesDoNotAffectPrefixQueryParam() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "callThem");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "doSomething");
    variables.put("foo", "oooo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("o");
    List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
  }

  @Test
  public void variableValuesFilteredBySubstring() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foobarko");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "doSooomething");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "oooo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("ooo");
    List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.contains("doSooomething")).isTrue();
    assertThat(variableResponse.contains("oooo")).isTrue();
  }

  @Test
  public void variableValuesFilteredBySubstringCaseInsensitive() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "fooBArich");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "dobarski");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "oobaRtenderoo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "tsoi-zhiv");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("bAr");
    List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(3);
    assertThat(variableResponse.contains("fooBArich")).isTrue();
    assertThat(variableResponse.contains("dobarski")).isTrue();
    assertThat(variableResponse.contains("oobaRtenderoo")).isTrue();
  }

  @Test
  public void variableValuesFilteredByLargeSubstrings() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foobarbarbarbarin");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "dobarbaRBarbarng");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "oobaro");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("barbarbarbar");
    List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.contains("foobarbarbarbarin")).isTrue();
    assertThat(variableResponse.contains("dobarbaRBarbarng")).isTrue();
  }

  @Test
  public void unknownPrefixReturnsEmptyResult() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "fooo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("bar");
    List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(0);
  }

  @Test
  public void valuePrefixForNonStringVariablesIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", 2);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(INTEGER);
    requestDto.setValueFilter("bar");
    List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
  }

  @Test
  public void nullPrefixIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter(null);
    List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
  }

  @Test
  public void emptyStringPrefixIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("");
    List<String> variableResponse = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
  }

  @Test
  public void missingNameQueryParamThrowsError() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey("aKey");
    requestDto.setProcessDefinitionVersion("aVersion");
    requestDto.setType(STRING);

    // when
    Response response = getVariableValueResponse(requestDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void missingTypeQueryParamThrowsError() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey("aKey");
    requestDto.setProcessDefinitionVersion("aVersion");
    requestDto.setName("var");

    // when
    Response response = getVariableValueResponse(requestDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void missingProcessDefinitionKeyQueryParamThrowsError() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionVersion("aVersion");
    requestDto.setType(STRING);
    requestDto.setName("var");

    // when
    Response response = getVariableValueResponse(requestDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void missingProcessDefinitionVersionQueryParamDoesNotThrowError() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey("aKey");
    requestDto.setType(STRING);
    requestDto.setName("var");

    // when
    Response response = getVariableValueResponse(requestDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void getAllVariableValues_variableExistsInSingleReport() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", 5L);
    variables.put("var3", 1.5);
    startInstanceAndImportEngineEntities(processDefinition, variables);
    final String reportId = createSingleReport(processDefinition);

    // when
    List<String> variableValues = variablesClient.getProcessVariableValuesForReports(
      createVariableValuesForReportsRequest(
        Collections.singletonList(reportId),
        "var1",
        STRING
      ));

    // then
    assertThat(variableValues).containsExactly("value1");
  }

  @Test
  public void getAllVariableValues_variableNameDoesNotExistForReport() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", 5L);
    variables.put("var3", 1.5);
    startInstanceAndImportEngineEntities(processDefinition, variables);
    final String reportId = createSingleReport(processDefinition);

    // when
    List<String> variableValues = variablesClient.getProcessVariableValuesForReports(
      createVariableValuesForReportsRequest(
        Collections.singletonList(reportId),
        "var4",
        STRING
      ));

    // then
    assertThat(variableValues).isEmpty();
  }

  @Test
  public void getAllVariableValues_variableNameExistsButNoTypeMatchForReport() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", 5L);
    variables.put("var3", 1.5);
    startInstanceAndImportEngineEntities(processDefinition, variables);
    final String reportId = createSingleReport(processDefinition);

    // when
    List<String> variableValues = variablesClient.getProcessVariableValuesForReports(
      createVariableValuesForReportsRequest(Collections.singletonList(reportId), "var1", BOOLEAN));

    // then
    assertThat(variableValues).isEmpty();
  }

  @Test
  public void getAllVariableValues_variableNameAndTypeExistsInMultipleReports() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition1, ImmutableMap.of("var1", "value1"));
    final String reportId1 = createSingleReport(processDefinition1);

    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition2, ImmutableMap.of("var1", "value2"));
    final String reportId2 = createSingleReport(processDefinition2);

    // when
    List<String> variableValues = variablesClient.getProcessVariableValuesForReports(
      createVariableValuesForReportsRequest(List.of(reportId1, reportId2), "var1", STRING));

    // then
    assertThat(variableValues).containsExactly("value1", "value2");
  }

  @Test
  public void getAllVariableValues_variableNameAndTypeExistsInSingleReportWithMultipleDefinitions() {
    // given
    final String key1 = "key1";
    final ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition(key1, null);
    startInstanceAndImportEngineEntities(processDefinition1, ImmutableMap.of("var1", "value1"));
    final String key2 = "key2";
    final ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition(key2, null);
    startInstanceAndImportEngineEntities(processDefinition2, ImmutableMap.of("var1", "value2"));

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .definitions(List.of(
        new ReportDataDefinitionDto(key1), new ReportDataDefinitionDto(key2)
      ))
      .build();
    final String reportId = reportClient.createSingleProcessReport(reportData);

    // when
    List<String> variableValues = variablesClient.getProcessVariableValuesForReports(
      createVariableValuesForReportsRequest(List.of(reportId), "var1", STRING));

    // then
    assertThat(variableValues).containsExactly("value1", "value2");
  }

  @Test
  public void getAllVariableValues_variableNameInMultipleReportsButOnlySingleTypeMatch() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition1, ImmutableMap.of("var1", "value1"));
    final String reportId1 = createSingleReport(processDefinition1);

    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition2, ImmutableMap.of("var1", true));
    final String reportId2 = createSingleReport(processDefinition2);

    // when
    List<String> variableValues = variablesClient.getProcessVariableValuesForReports(
      createVariableValuesForReportsRequest(List.of(reportId1, reportId2), "var1", STRING));

    // then
    assertThat(variableValues).containsExactly("value1");
  }

  @Test
  public void getAllVariableValues_variableNameExistsInSingleReportOfCombinedReport() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition1, ImmutableMap.of("var1", "value1"));
    final String reportId1 = createSingleReport(processDefinition1);

    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition2, ImmutableMap.of("var2", "value2"));
    final String reportId2 = createSingleReport(processDefinition2);

    final String combinedReportId = reportClient.createCombinedReport(null, List.of(reportId1, reportId2));

    // when
    List<String> variableValues = variablesClient.getProcessVariableValuesForReports(
      createVariableValuesForReportsRequest(List.of(combinedReportId), "var1", STRING));

    // then
    assertThat(variableValues).containsExactly("value1");
  }

  @Test
  public void getAllVariableValues_variableNameExistsInMultipleReportsOfCombinedReport() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition1, ImmutableMap.of("var1", "value1"));
    final String reportId1 = createSingleReport(processDefinition1);

    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition2, ImmutableMap.of("var1", "value2"));
    final String reportId2 = createSingleReport(processDefinition2);

    final String combinedReportId = reportClient.createCombinedReport(null, List.of(reportId1, reportId2));

    // when
    List<String> variableValues = variablesClient.getProcessVariableValuesForReports(
      createVariableValuesForReportsRequest(Collections.singletonList(combinedReportId), "var1", STRING));

    // then
    assertThat(variableValues).containsExactly("value1", "value2");
  }

  @Test
  public void getAllVariableValues_variableExistsAndResultsRespectSearchTerm() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition, ImmutableMap.of("var1", "vladimir"));
    startInstanceAndImportEngineEntities(processDefinition, ImmutableMap.of("var1", "putin"));
    startInstanceAndImportEngineEntities(processDefinition, ImmutableMap.of("var1", "LAD"));
    final String reportId = createSingleReport(processDefinition);
    final ProcessVariableReportValuesRequestDto requestDto = createVariableValuesForReportsRequest(
      Collections.singletonList(reportId),
      "var1",
      STRING
    );

    // when no search term used
    List<String> variableValues = variablesClient.getProcessVariableValuesForReports(requestDto);

    // then all values are returned
    assertThat(variableValues).containsExactly("LAD", "putin", "vladimir");

    // when no search term used
    requestDto.setValueFilter("LaD");
    variableValues = variablesClient.getProcessVariableValuesForReports(requestDto);

    // then only case-insensitively matching values containing search term are returned
    assertThat(variableValues).containsExactly("LAD", "vladimir");
  }

  @Test
  public void getAllVariableValues_matchingDecisionVariableValuesAreNotReturned() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    startInstanceAndImportEngineEntities(processDefinition, variables);
    final String reportId1 = createSingleReport(processDefinition);

    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = startDecisionInstanceAndImportEngineEntities(
      ImmutableMap.of("var1", "value2")
    );

    final String reportId2 = reportClient.createSingleDecisionReportDefinitionDto(
      decisionDefinitionEngineDto.getKey()).getId();

    // when
    List<String> variableValues = variablesClient.getProcessVariableValuesForReports(
      createVariableValuesForReportsRequest(
        List.of(reportId1, reportId2),
        "var1",
        STRING
      ));

    // then
    assertThat(variableValues).containsExactly("value1");
  }

  @Test
  public void getAllVariableValues_resultPaginationRespectsLimitAndOffset() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition1, ImmutableMap.of("var1", "value1"));
    final String reportId1 = createSingleReport(processDefinition1);

    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition2, ImmutableMap.of("var1", "value2"));
    final String reportId2 = createSingleReport(processDefinition2);

    final ProcessVariableReportValuesRequestDto requestDto = createVariableValuesForReportsRequest(
      List.of(reportId1, reportId2),
      "var1",
      STRING
    );
    requestDto.setNumResults(0);

    // when the max result size is 0
    List<String> variableValues = variablesClient.getProcessVariableValuesForReports(requestDto);

    // then no results are returned
    assertThat(variableValues).isEmpty();

    // when the max result size is 1
    requestDto.setNumResults(1);
    variableValues = variablesClient.getProcessVariableValuesForReports(requestDto);

    // then the first page of results is returned
    assertThat(variableValues).containsExactly("value1");

    // when the offset matches the number of results per page
    requestDto.setResultOffset(1);
    variableValues = variablesClient.getProcessVariableValuesForReports(requestDto);

    // then the second page of results is returned
    assertThat(variableValues).containsExactly("value2");
  }

  private ProcessVariableReportValuesRequestDto createVariableValuesForReportsRequest(final List<String> reportIds,
                                                                                      final String name,
                                                                                      final VariableType type) {
    ProcessVariableReportValuesRequestDto dto = new ProcessVariableReportValuesRequestDto();
    dto.setReportIds(reportIds);
    dto.setName(name);
    dto.setType(type);
    return dto;
  }

  private Response getVariableValueResponse(ProcessVariableValueRequestDto valueRequestDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableValuesRequest(valueRequestDto)
      .execute();
  }

}
