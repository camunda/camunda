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
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.service.util.ProcessVariableHelper.isVariableTypeSupported;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProcessVariableNameIT extends AbstractIT {

  private static final String A_PROCESS = "aProcess";

  @Test
  public void getVariableNames() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.clear();
    variables.put("var4", "value4");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition);

    // then
    assertThat(variableResponse.size(), is(4));
    assertThat(variableResponse.get(0).getName(), is("var1"));
    assertThat(variableResponse.get(1).getName(), is("var2"));
    assertThat(variableResponse.get(2).getName(), is("var3"));
    assertThat(variableResponse.get(3).getName(), is("var4"));
  }

  @Test
  public void getVariableNamesSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Lists.newArrayList(tenantId1);
    String processDefinition = deployAndStartMultiTenantUserTaskProcess(
      Lists.newArrayList(null, tenantId1, tenantId2)
    );
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableNameRequestDto variableNameRequestDto = new ProcessVariableNameRequestDto();
    variableNameRequestDto.setProcessDefinitionKey(processDefinition);
    variableNameRequestDto.setProcessDefinitionVersion(ALL_VERSIONS);
    variableNameRequestDto.setTenantIds(selectedTenants);
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(variableNameRequestDto);

    // then
    assertThat(variableResponse.size(), is(selectedTenants.size()));
  }

  @Test
  public void getVariableNamesForMultipleDefinitionVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var2", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition2.getId(), variables);
    ProcessDefinitionEngineDto processDefinition3 = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var3", "value3");
    variables.put("var4", "value4");
    engineIntegrationExtension.startProcessInstance(processDefinition3.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse =
      getVariableNames(
        processDefinition.getKey(),
        ImmutableList.of(processDefinition.getVersionAsString(), processDefinition3.getVersionAsString())
      );

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.get(0).getName(), is("var1"));
    assertThat(variableResponse.get(1).getName(), is("var3"));
    assertThat(variableResponse.get(2).getName(), is("var4"));
  }


  @Test
  public void getMoreThan10Variables() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    IntStream.range(0,15).forEach(
      i -> variables.put("var" + i, "value" + i)
    );
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition);

    // then
    assertThat(variableResponse.size(), is(15));
  }

  @Test
  public void getVariablesForAllVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    processDefinition = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var4", "value4");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition.getKey(), ALL_VERSIONS);

    // then
    assertThat(variableResponse.size(), is(4));
    assertThat(variableResponse.get(0).getName(), is("var1"));
    assertThat(variableResponse.get(1).getName(), is("var2"));
    assertThat(variableResponse.get(2).getName(), is("var3"));
    assertThat(variableResponse.get(3).getName(), is("var4"));
  }

  @Test
  public void getVariableNamesForLatestVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    processDefinition = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var4", "value4");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition.getKey(), LATEST_VERSION);

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.get(0).getName(), is("var4"));
  }

  @Test
  public void noVariablesFromAnotherProcessDefinition() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.clear();
    variables.put("var2", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition2.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition);

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.get(0).getName(), is("var1"));
    assertThat(variableResponse.get(0).getType(), is(VariableType.STRING));
  }

  @Test
  public void variablesAreSortedAlphabetically() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("b", "value1");
    variables.put("c", "value2");
    variables.put("a", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.clear();
    variables.put("c", "anotherValue");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition);

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.get(0).getName(), is("a"));
    assertThat(variableResponse.get(1).getName(), is("b"));
    assertThat(variableResponse.get(2).getName(), is("c"));
  }

  @Test
  public void variablesDoNotContainDuplicates() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition);

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.get(0).getName(), is("var1"));
  }

  @Test
  public void variableWithSameNameAndDifferentType() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", true);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.get(0).getName(), is("var"));
    assertThat(variableResponse.get(1).getName(), is("var"));
  }

  @Test
  public void allPrimitiveTypesCanBeRead() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", new Date());
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");

    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.resetImportStartIndexes();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition);

    // then
    assertThat(variableResponse.size(), is(variables.size()));
    for (ProcessVariableNameResponseDto responseDto : variableResponse) {
      assertThat(variables.containsKey(responseDto.getName()), is(true));
      assertThat(isVariableTypeSupported(responseDto.getType()), is(true));
    }
  }

   @Test
  public void getOnlyVariablesWithSpecifiedNamePrefix() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
     variables.put("a", "value3");
     variables.put("ab", "value1");
     variables.put("c", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition, "a");

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.get(0).getName(), is("a"));
    assertThat(variableResponse.get(1).getName(), is("ab"));
  }

  @Test
  public void unknownPrefixReturnsEmptyResult() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
     variables.put("a", "value3");
     variables.put("ab", "value1");
     variables.put("c", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition, "foo");

    // then
    assertThat(variableResponse.size(), is(0));
  }

  @Test
  public void nullPrefixIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
     variables.put("a", "value3");
     variables.put("ab", "value1");
     variables.put("c", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition, null);

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.get(0).getName(), is("a"));
    assertThat(variableResponse.get(1).getName(), is("ab"));
    assertThat(variableResponse.get(2).getName(), is("c"));
  }

  @Test
  public void emptyStringPrefixIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
     variables.put("a", "value3");
     variables.put("ab", "value1");
     variables.put("c", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition, "");

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.get(0).getName(), is("a"));
    assertThat(variableResponse.get(1).getName(), is("ab"));
    assertThat(variableResponse.get(2).getName(), is("c"));
  }

  @Test
  public void prefixCanBeAppliedToAllVariableTypes() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", new Date());
    variables.put("boolVar", true);
    variables.put("shortVar", (short)2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");

    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = getVariableNames(processDefinition, "d");

    // then
    assertThat(variableResponse.size(), is(2));
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
    return deploySimpleProcessDefinition(null);
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(A_PROCESS)
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private List<ProcessVariableNameResponseDto> getVariableNames(ProcessDefinitionEngineDto processDefinition,
                                                                String namePrefix) {
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    variableRequestDto.setProcessDefinitionVersions(ImmutableList.of(processDefinition.getVersionAsString()));
    variableRequestDto.setNamePrefix(namePrefix);
    return this.getVariableNames(variableRequestDto);
  }

  private List<ProcessVariableNameResponseDto> getVariableNames(ProcessDefinitionEngineDto processDefinition) {
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    variableRequestDto.setProcessDefinitionVersions(ImmutableList.of(processDefinition.getVersionAsString()));
    return getVariableNames(processDefinition, null);
  }

  private List<ProcessVariableNameResponseDto> getVariableNames(String key, List<String> versions) {
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey(key);
    variableRequestDto.setProcessDefinitionVersions(versions);
    return getVariableNames(variableRequestDto);
  }

  private List<ProcessVariableNameResponseDto> getVariableNames(String key, String version) {
    return getVariableNames(key, ImmutableList.of(version));
  }

  private List<ProcessVariableNameResponseDto> getVariableNames(ProcessVariableNameRequestDto variableRequestDto) {
    return embeddedOptimizeExtension
            .getRequestExecutor()
            .buildProcessVariableNamesRequest(variableRequestDto)
            .executeAndReturnList(ProcessVariableNameResponseDto.class, 200);
  }

  private String deployAndStartMultiTenantUserTaskProcess(final List<String> deployedTenants) {
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {

        final ProcessDefinitionEngineDto processDefinitionEngineDto = deploySimpleProcessDefinition(tenant);
        String randomName = RandomStringUtils.random(10);
        String randomValue = RandomStringUtils.random(10);
        engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId(), ImmutableMap.of(randomName, randomValue));
      });

    return A_PROCESS;
  }

}
