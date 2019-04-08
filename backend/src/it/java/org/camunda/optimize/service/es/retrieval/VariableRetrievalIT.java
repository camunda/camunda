/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.rest.VariableRestService.NAME_PREFIX;
import static org.camunda.optimize.service.util.ProcessVariableHelper.isVariableTypeSupported;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class VariableRetrievalIT {

  private static final String A_PROCESS = "aProcess";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);


  @Test
  public void getVariables() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.clear();
    variables.put("var4", "value4");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getVariables(processDefinition);

    // then
    assertThat(variableResponse.size(), is(4));
    assertThat(variableResponse.get(0).getName(), is("var1"));
    assertThat(variableResponse.get(1).getName(), is("var2"));
    assertThat(variableResponse.get(2).getName(), is("var3"));
    assertThat(variableResponse.get(3).getName(), is("var4"));
  }

  @Test
  public void getMoreThan10Variables() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    IntStream.range(0,15).forEach(
      i -> variables.put("var" + i, "value" + i)
    );
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getVariables(processDefinition);

    // then
    assertThat(variableResponse.size(), is(15));
  }

  @Test
  public void getVariablesForAllVersions() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    processDefinition = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var4", "value4");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getVariables(processDefinition.getKey(), ALL_VERSIONS);

    // then
    assertThat(variableResponse.size(), is(4));
    assertThat(variableResponse.get(0).getName(), is("var1"));
    assertThat(variableResponse.get(1).getName(), is("var2"));
    assertThat(variableResponse.get(2).getName(), is("var3"));
    assertThat(variableResponse.get(3).getName(), is("var4"));
  }

  @Test
  public void noVariablesFromAnotherProcessDefinition() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.clear();
    variables.put("var2", "value2");
    engineRule.startProcessInstance(processDefinition2.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getVariables(processDefinition);

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.get(0).getName(), is("var1"));
    assertThat(variableResponse.get(0).getType(), is(VariableType.STRING));
  }

  @Test
  public void variablesAreSortedAlphabetically() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("b", "value1");
    variables.put("c", "value2");
    variables.put("a", "value3");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getSortedVariables(processDefinition, "asc", "name");


    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.get(0).getName(), is("a"));
    assertThat(variableResponse.get(1).getName(), is("b"));
    assertThat(variableResponse.get(2).getName(), is("c"));
  }
  @Test
  public void variablesAreSortedInDescendingOrder() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("b", "value1");
    variables.put("c", "value2");
    variables.put("a", "value3");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getSortedVariables(processDefinition, "desc", "name");

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.get(0).getName(), is("c"));
    assertThat(variableResponse.get(1).getName(), is("b"));
    assertThat(variableResponse.get(2).getName(), is("a"));
  }

  private List<VariableRetrievalDto> getSortedVariables(ProcessDefinitionEngineDto processDefinition, String sortOrder, String orderBy) {
        return embeddedOptimizeRule
                .getRequestExecutor()
                .buildGetVariablesRequest(processDefinition.getKey(), processDefinition.getVersion())
                .addSingleQueryParam("orderBy", orderBy)
                .addSingleQueryParam("sortOrder", sortOrder)
                .executeAndReturnList(VariableRetrievalDto.class, 200);
  }

  @Test
  public void variablesDoNotContainDuplicates() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getVariables(processDefinition);

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.get(0).getName(), is("var1"));
  }

  @Test
  public void variableWithSameNameAndDifferentType() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getVariables(processDefinition);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.get(0).getName(), is("var"));
    assertThat(variableResponse.get(1).getName(), is("var"));
  }

  @Test
  public void allPrimitiveTypesCanBeRead() throws Exception {
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
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.resetImportStartIndexes();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getVariables(processDefinition);

    // then
    assertThat(variableResponse.size(), is(variables.size()));
    for (VariableRetrievalDto responseDto : variableResponse) {
      assertThat(variables.containsKey(responseDto.getName()), is(true));
      assertThat(isVariableTypeSupported(responseDto.getType()), is(true));
    }
  }

   @Test
  public void getOnlyVariablesWithSpecifiedNamePrefix() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
     variables.put("a", "value3");
     variables.put("ab", "value1");
     variables.put("c", "value2");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getVariablesWithPrefix(processDefinition, "a");

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.get(0).getName(), is("a"));
    assertThat(variableResponse.get(1).getName(), is("ab"));
  }

  @Test
  public void unknownPrefixReturnsEmptyResult() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
     variables.put("a", "value3");
     variables.put("ab", "value1");
     variables.put("c", "value2");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getVariablesWithPrefix(processDefinition, "foo");

    // then
    assertThat(variableResponse.size(), is(0));
  }

  @Test
  public void nullPrefixIsIgnored() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
     variables.put("a", "value3");
     variables.put("ab", "value1");
     variables.put("c", "value2");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getVariablesWithPrefix(processDefinition, null);

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.get(0).getName(), is("a"));
    assertThat(variableResponse.get(1).getName(), is("ab"));
    assertThat(variableResponse.get(2).getName(), is("c"));
  }

  @Test
  public void emptyStringPrefixIsIgnored() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
     variables.put("a", "value3");
     variables.put("ab", "value1");
     variables.put("c", "value2");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getVariablesWithPrefix(processDefinition, "");

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.get(0).getName(), is("a"));
    assertThat(variableResponse.get(1).getName(), is("ab"));
    assertThat(variableResponse.get(2).getName(), is("c"));
  }

  @Test
  public void prefixCanBeAppliedToAllVariableTypes() throws Exception {
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
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<VariableRetrievalDto> variableResponse = getVariablesWithPrefix(processDefinition, "d");

    // then
    assertThat(variableResponse.size(), is(2));
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(A_PROCESS)
      .startEvent()
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private List<VariableRetrievalDto> getVariablesWithPrefix(ProcessDefinitionEngineDto processDefinition,
                                                            String namePrefix) {
    String key = processDefinition.getKey();
    String version = String.valueOf(processDefinition.getVersion());
    return getVariablesWithPrefix(key, version, namePrefix);
  }

  private List<VariableRetrievalDto> getVariables(ProcessDefinitionEngineDto processDefinition) {
    String key = processDefinition.getKey();
    String version = String.valueOf(processDefinition.getVersion());
    return getVariables(key, version);
  }

  private List<VariableRetrievalDto> getVariables(String key, String version) {
    return getVariablesWithPrefix(key, version, null);
  }

  private List<VariableRetrievalDto> getVariablesWithPrefix(String key, String version, String namePrefix) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetVariablesRequest(key, version)
            .addSingleQueryParam(NAME_PREFIX, namePrefix)
            .executeAndReturnList(VariableRetrievalDto.class, 200);
  }
}
