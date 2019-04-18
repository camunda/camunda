/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.VariableRestService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static junit.framework.TestCase.assertTrue;
import static org.camunda.optimize.rest.VariableRestService.NAME;
import static org.camunda.optimize.rest.VariableRestService.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.rest.VariableRestService.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.rest.VariableRestService.TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class VariableValueRetrievalIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();


  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void getVariableValues() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.contains("value1"), is(true));
    assertThat(variableResponse.contains("value2"), is(true));
    assertThat(variableResponse.contains("value3"), is(true));
  }

  @Test
  public void getMoreThan10VariableValues() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    IntStream.range(0, 15).forEach(
      i -> {
        variables.put("var", "value" + i);
        engineRule.startProcessInstance(processDefinition.getId(), variables);
      }
    );
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size(), is(15));
  }

  @Test
  public void onlyValuesToSpecifiedVariableAreReturned() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var1");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value1"), is(true));
  }

  @Test
  public void noValuesFromAnotherProcessDefinition() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineRule.startProcessInstance(processDefinition2.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value1"), is(true));
  }

  @Test
  public void sameVariableNameWithDifferentType() throws Exception {
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
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value1"), is(true));
  }

  @Test
  public void valuesDoNotContainDuplicates() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value1"), is(true));
  }

  @Test
  public void retrieveValuesForAllPrimitiveTypes() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, String> varNameToTypeMap = createVarNameToTypeMap();
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now().withOffsetSameLocal(ZoneOffset.UTC));
    variables.put("boolVar", true);
    variables.put("shortVar", (short)2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    for (String name : variables.keySet()) {
      // when
      String type = varNameToTypeMap.get(name);
      Map<String, Object> queryParams = new HashMap<>();
      queryParams.put(NAME, name);
      queryParams.put(TYPE, type);
      queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
      queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
      List<String> variableResponse = getVariableValues(queryParams);

      // then
      String expectedValue;
      if (name.equals("dateVar")) {
        OffsetDateTime temporal = (OffsetDateTime) variables.get(name);
        expectedValue = embeddedOptimizeRule.getDateTimeFormatter().format(temporal);
      } else {
        expectedValue = variables.get(name).toString();
      }
      assertThat(variableResponse.size(), is(1));
      assertThat("contains [" + expectedValue + "]", variableResponse.contains(expectedValue), is(true));
    }

  }

  private Map<String, String> createVarNameToTypeMap() {
    Map<String, String> varToType = new HashMap<>();
    varToType.put("dateVar", "date");
    varToType.put("boolVar", "boolean");
    varToType.put("shortVar", "short");
    varToType.put("intVar", "integer");
    varToType.put("longVar", "long");
    varToType.put("doubleVar", "double");
    varToType.put("stringVar", "string");
    return varToType;
  }


  @Test
  public void valuesListIsCutByMaxResults() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("numResults", 2);
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", "var");
    queryParams.put("type", "string");
    List<String> variableResponse = getVariableValues(queryParams);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.contains("value1"), is(true));
    assertThat(variableResponse.contains("value2"), is(true));
  }

  @Test
  public void valuesListIsCutByAnOffset() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("resultOffset", 1);
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", "var");
    queryParams.put("type", "string");
    List<String> variableResponse = getVariableValues(queryParams);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.contains("value2"), is(true));
    assertThat(variableResponse.contains("value3"), is(true));
  }

  @Test
  public void valuesListIsCutByAnOffsetAndMaxResults() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("numResults", 1);
    queryParams.put("resultOffset", 1);
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", "var");
    queryParams.put("type", "string");
    List<String> variableResponse = getVariableValues(queryParams);

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value2"), is(true));
  }

  @Test
  public void getOnlyValuesWithSpecifiedPrefix() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "fooo");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "bar");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "ball");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put(VariableRestService.VALUE_FILTER, "ba");
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", "var");
    queryParams.put("type", "string");
    List<String> variableResponse = getVariableValues(queryParams);

    // then
    assertThat(variableResponse.size(), is(2));
    assertTrue(variableResponse.contains("bar"));
    assertTrue(variableResponse.contains("ball"));
  }

  @Test
  public void variableValueFromDifferentVariablesDoNotAffectPrefixQueryParam() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "callThem");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "doSomething");
    variables.put("foo", "oooo");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put(VariableRestService.VALUE_FILTER, "o");
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", "var");
    queryParams.put("type", "String");
    List<String> variableResponse = getVariableValues(queryParams);

    // then
    assertThat(variableResponse.size(), is(1));
  }

  @Test
  public void variableValuesFilteredBySubstring() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foobarko");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "doSooomething");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "oooo");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put(VariableRestService.VALUE_FILTER, "ooo");
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", "var");
    queryParams.put("type", "String");
    List<String> variableResponse = getVariableValues(queryParams);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.contains("doSooomething"), is(true));
    assertThat(variableResponse.contains("oooo"), is(true));
  }

  @Test
  public void variableValuesFilteredBySubstringCaseInsensitive() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "fooBArich");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "dobarski");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "oobaRtenderoo");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "tsoi-zhiv");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put(VariableRestService.VALUE_FILTER, "bAr");
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", "var");
    queryParams.put("type", "String");
    List<String> variableResponse = getVariableValues(queryParams);

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.contains("fooBArich"), is(true));
    assertThat(variableResponse.contains("dobarski"), is(true));
    assertThat(variableResponse.contains("oobaRtenderoo"), is(true));
  }

  @Test
  public void variableValuesFilteredByLargeSubstrings() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foobarbarbarbarin");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "dobarbaRBarbarng");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "oobaro");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put(VariableRestService.VALUE_FILTER, "barbarbarbar");
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", "var");
    queryParams.put("type", "String");
    List<String> variableResponse = getVariableValues(queryParams);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.contains("foobarbarbarbarin"), is(true));
    assertThat(variableResponse.contains("dobarbaRBarbarng"), is(true));
  }

  @Test
  public void unknownPrefixReturnsEmptyResult() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "fooo");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put(VariableRestService.VALUE_FILTER, "bar");
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", "var");
    queryParams.put("type", "string");
    List<String> variableResponse = getVariableValues(queryParams);

    // then
    assertThat(variableResponse.size(), is(0));
  }

  @Test
  public void valuePrefixForNonStringVariablesIsIgnored() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", 2);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put(VariableRestService.VALUE_FILTER, "bar");
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", "var");
    queryParams.put("type", "integer");
    List<String> variableResponse = getVariableValues(queryParams);

    // then
    assertThat(variableResponse.size(), is(1));
  }

  @Test
  public void nullPrefixIsIgnored() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foo");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put(VariableRestService.VALUE_FILTER, null);
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", "var");
    queryParams.put("type", "string");
    List<String> variableResponse = getVariableValues(queryParams);

    // then
    assertThat(variableResponse.size(), is(1));
  }

  @Test
  public void emptyStringPrefixIsIgnored() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foo");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put(VariableRestService.VALUE_FILTER, "");
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", "var");
    queryParams.put("type", "string");
    List<String> variableResponse = getVariableValues(queryParams);

    // then
    assertThat(variableResponse.size(), is(1));
  }

  @Test
  public void missingNameQueryParamThrowsError() {
    // given
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("type", "string");
    queryParams.put(PROCESS_DEFINITION_KEY, "aKey");
    queryParams.put(PROCESS_DEFINITION_VERSION, "aVersion");

    //when
    Response response = getVariableValueResponse(queryParams);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingTypeQueryParamThrowsError() {
    // given
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("name", "var");
    queryParams.put(PROCESS_DEFINITION_KEY, "aKey");
    queryParams.put(PROCESS_DEFINITION_VERSION, "aVersion");

    //when
    Response response = getVariableValueResponse(queryParams);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingProcessDefinitionKeyQueryParamThrowsError() {
    // given
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("name", "var");
    queryParams.put("type", "string");
    queryParams.put(PROCESS_DEFINITION_VERSION, "aVersion");

    //when
    Response response = getVariableValueResponse(queryParams);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingProcessDefinitionVersionQueryParamThrowsError() {
    // given
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("name", "var");
    queryParams.put("type", "string");
    queryParams.put(PROCESS_DEFINITION_KEY, "aKey");

    //when
    Response response = getVariableValueResponse(queryParams);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private List<String> getVariableValues(ProcessDefinitionEngineDto processDefinition, String name) {
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put(PROCESS_DEFINITION_KEY, processDefinition.getKey());
    queryParams.put(PROCESS_DEFINITION_VERSION, processDefinition.getVersion());
    queryParams.put("name", name);
    queryParams.put("type", "String");
    return getVariableValues(queryParams);
  }

  private List<String> getVariableValues(Map<String, Object> queryParams) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetVariableValuesRequest()
            .addQueryParams(queryParams)
            .executeAndReturnList(String.class, 200);
  }

  private Response getVariableValueResponse(Map<String, Object> queryParams) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetVariableValuesRequest()
            .addQueryParams(queryParams)
            .execute();
  }
}
