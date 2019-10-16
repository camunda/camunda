/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import static junit.framework.TestCase.assertTrue;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.BOOLEAN;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DATE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DOUBLE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.INTEGER;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.LONG;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.SHORT;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProcessVariableValueIT {

  private static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void getVariableValues() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.contains("value1"), is(true));
    assertThat(variableResponse.contains("value2"), is(true));
    assertThat(variableResponse.contains("value3"), is(true));
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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto valueRequestDto = new ProcessVariableValueRequestDto();
    valueRequestDto.setProcessDefinitionKey(processDefinition);
    valueRequestDto.setProcessDefinitionVersion(ALL_VERSIONS);
    valueRequestDto.setTenantIds(selectedTenants);
    valueRequestDto.setName(variableName);
    valueRequestDto.setType(STRING);
    List<String> variableResponse = getVariableValues(valueRequestDto);

    // then
    assertThat(variableResponse.size(), is(selectedTenants.size()));
  }

  @Test
  public void getVariableNamesForMultipleDefinitionVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var", "value2");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition2.getId(), variables);
    ProcessDefinitionEngineDto processDefinition3 = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var", "value3");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition3.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto valueRequestDto = new ProcessVariableValueRequestDto();
    valueRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    valueRequestDto.setProcessDefinitionVersions(ImmutableList.of(
      processDefinition.getVersionAsString(),
      processDefinition3.getVersionAsString()
    ));
    valueRequestDto.setName("var");
    valueRequestDto.setType(STRING);
    List<String> variableResponse = getVariableValues(valueRequestDto);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.get(0), is("value1"));
    assertThat(variableResponse.get(1), is("value3"));
  }

  @Test
  public void getVariablesForAllVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    processDefinition = deploySimpleProcessDefinition();
    variables.put("var", "value2");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto valueRequestDto = new ProcessVariableValueRequestDto();
    valueRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    valueRequestDto.setProcessDefinitionVersion(ALL_VERSIONS);
    valueRequestDto.setName("var");
    valueRequestDto.setType(STRING);
    List<String> variableResponse = getVariableValues(valueRequestDto);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.get(0), is("value1"));
    assertThat(variableResponse.get(1), is("value2"));
  }

  @Test
  public void getVariableNamesForLatestVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "first");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    processDefinition = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var", "latest");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto valueRequestDto = new ProcessVariableValueRequestDto();
    valueRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    valueRequestDto.setProcessDefinitionVersion(LATEST_VERSION);
    valueRequestDto.setName("var");
    valueRequestDto.setType(STRING);
    List<String> variableResponse = getVariableValues(valueRequestDto);

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.get(0), is("latest"));
  }

  @Test
  public void getMoreThan10VariableValues() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    IntStream.range(0, 15).forEach(
      i -> {
        variables.put("var", "value" + i);
        engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
      }
    );
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size(), is(15));
  }

  @Test
  public void onlyValuesToSpecifiedVariableAreReturned() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var1");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value1"), is(true));
  }

  @Test
  public void noValuesFromAnotherProcessDefinition() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition2.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value1"), is(true));
  }

  @Test
  public void sameVariableNameWithDifferentType() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", true);
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value1"), is(true));
  }

  @Test
  public void valuesDoNotContainDuplicates() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value1");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value1"), is(true));
  }

  @Test
  public void retrieveValuesForAllPrimitiveTypes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, VariableType> varNameToTypeMap = createVarNameToTypeMap();
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now().withOffsetSameLocal(ZoneOffset.UTC));
    variables.put("boolVar", true);
    variables.put("shortVar", (short)2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    for (String name : variables.keySet()) {
      // when
      VariableType type = varNameToTypeMap.get(name);
      ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
      requestDto.setProcessDefinitionKey(processDefinition.getKey());
      requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
      requestDto.setName(name);
      requestDto.setType(type);
      List<String> variableResponse = getVariableValues(requestDto);

      // then
      String expectedValue;
      if (name.equals("dateVar")) {
        OffsetDateTime temporal = (OffsetDateTime) variables.get(name);
        expectedValue = embeddedOptimizeExtensionRule.getDateTimeFormatter().format(temporal);
      } else {
        expectedValue = variables.get(name).toString();
      }
      assertThat(variableResponse.size(), is(1));
      assertThat("contains [" + expectedValue + "]", variableResponse.contains(expectedValue), is(true));
    }

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
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setNumResults(2);
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.contains("value1"), is(true));
    assertThat(variableResponse.contains("value2"), is(true));
  }

  @Test
  public void valuesListIsCutByAnOffset() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setResultOffset(1);
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.contains("value2"), is(true));
    assertThat(variableResponse.contains("value3"), is(true));
  }

  @Test
  public void valuesListIsCutByAnOffsetAndMaxResults() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setNumResults(1);
    requestDto.setResultOffset(1);
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value2"), is(true));
  }

  @Test
  public void getOnlyValuesWithSpecifiedPrefix() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "fooo");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "bar");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "ball");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("ba");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size(), is(2));
    assertTrue(variableResponse.contains("bar"));
    assertTrue(variableResponse.contains("ball"));
  }

  @Test
  public void variableValueFromDifferentVariablesDoNotAffectPrefixQueryParam() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "callThem");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "doSomething");
    variables.put("foo", "oooo");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("o");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size(), is(1));
  }

  @Test
  public void variableValuesFilteredBySubstring() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foobarko");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "doSooomething");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "oooo");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("ooo");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.contains("doSooomething"), is(true));
    assertThat(variableResponse.contains("oooo"), is(true));
  }

  @Test
  public void variableValuesFilteredBySubstringCaseInsensitive() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "fooBArich");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "dobarski");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "oobaRtenderoo");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "tsoi-zhiv");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("bAr");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.contains("fooBArich"), is(true));
    assertThat(variableResponse.contains("dobarski"), is(true));
    assertThat(variableResponse.contains("oobaRtenderoo"), is(true));
  }

  @Test
  public void variableValuesFilteredByLargeSubstrings() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foobarbarbarbarin");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "dobarbaRBarbarng");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "oobaro");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("barbarbarbar");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.contains("foobarbarbarbarin"), is(true));
    assertThat(variableResponse.contains("dobarbaRBarbarng"), is(true));
  }

  @Test
  public void unknownPrefixReturnsEmptyResult() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "fooo");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("bar");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size(), is(0));
  }

  @Test
  public void valuePrefixForNonStringVariablesIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", 2);
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(INTEGER);
    requestDto.setValueFilter("bar");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size(), is(1));
  }

  @Test
  public void nullPrefixIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foo");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter(null);
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size(), is(1));
  }

  @Test
  public void emptyStringPrefixIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foo");
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size(), is(1));
  }

  @Test
  public void missingNameQueryParamThrowsError() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey("aKey");
    requestDto.setProcessDefinitionVersion("aVersion");
    requestDto.setType(STRING);

    //when
    Response response = getVariableValueResponse(requestDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingTypeQueryParamThrowsError() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey("aKey");
    requestDto.setProcessDefinitionVersion("aVersion");
    requestDto.setName("var");

    //when
    Response response = getVariableValueResponse(requestDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingProcessDefinitionKeyQueryParamThrowsError() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionVersion("aVersion");
    requestDto.setType(STRING);
    requestDto.setName("var");

    //when
    Response response = getVariableValueResponse(requestDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingProcessDefinitionVersionQueryParamThrowsError() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey("aKey");
    requestDto.setType(STRING);
    requestDto.setName("var");

    //when
    Response response = getVariableValueResponse(requestDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
    return deploySimpleProcessDefinition(null);
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String tenant) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(modelInstance, tenant);
  }

  private List<String> getVariableValues(ProcessDefinitionEngineDto processDefinition, String name) {
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName(name);
    requestDto.setType(STRING);
    List<String> variableResponse = getVariableValues(requestDto);
    return getVariableValues(requestDto);
  }

  private List<String> getVariableValues(ProcessVariableValueRequestDto valueRequestDto) {
    return embeddedOptimizeExtensionRule
            .getRequestExecutor()
            .buildProcessVariableValuesRequest(valueRequestDto)
            .executeAndReturnList(String.class, 200);
  }

  private Response getVariableValueResponse(ProcessVariableValueRequestDto valueRequestDto) {
    return embeddedOptimizeExtensionRule
            .getRequestExecutor()
            .buildProcessVariableValuesRequest(valueRequestDto)
            .execute();
  }

  private String deployAndStartMultiTenantUserTaskProcess(final String variableName,
                                                          final List<String> deployedTenants) {
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtensionRule.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {

        final ProcessDefinitionEngineDto processDefinitionEngineDto = deploySimpleProcessDefinition(tenant);
        String randomValue = RandomStringUtils.random(10);
        engineIntegrationExtensionRule.startProcessInstance(processDefinitionEngineDto.getId(), ImmutableMap.of(variableName, randomValue));
      });

    return PROCESS_DEFINITION_KEY;
  }
}
