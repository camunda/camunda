package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.rest.VariableRestService.NAME;
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
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "value2");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "value3");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<String> variableResponse = getVariableValues(processDefinitionId, "var");

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.contains("value1"), is(true));
    assertThat(variableResponse.contains("value2"), is(true));
    assertThat(variableResponse.contains("value3"), is(true));
  }

  @Test
  public void onlyValuesToSpecifiedVariableAreReturned() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<String> variableResponse = getVariableValues(processDefinitionId, "var1");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value1"), is(true));
  }

  @Test
  public void noValuesFromAnotherProcessDefinition() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    String processDefinitionId2 = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "value2");
    engineRule.startProcessInstance(processDefinitionId2, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<String> variableResponse = getVariableValues(processDefinitionId, "var");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value1"), is(true));
  }

  @Test
  public void sameVariableNameWithDifferentType() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", true);
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<String> variableResponse = getVariableValues(processDefinitionId, "var");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value1"), is(true));
  }

  @Test
  public void valuesDoNotContainDuplicates() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<String> variableResponse = getVariableValues(processDefinitionId, "var");

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value1"), is(true));
  }

  @Test
  public void retrieveValuesForAllPrimitiveTypes() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, String> varNameToTypeMap = createVarNameToTypeMap();
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now());
    variables.put("boolVar", true);
    variables.put("shortVar", (short)2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    for (String name : variables.keySet()) {
      // when
      String type = varNameToTypeMap.get(name);
      Map<String, Object> queryParams = new HashMap<>();
      queryParams.put(NAME, name);
      queryParams.put(TYPE, type);
      List<String> variableResponse = getVariableValues(processDefinitionId, queryParams);

      // then
      String expectedValue;
      if (name.equals("dateVar")) {
        OffsetDateTime temporal = (OffsetDateTime) variables.get(name);
        expectedValue = embeddedOptimizeRule.format(
            temporal.withOffsetSameInstant(ZoneOffset.UTC)
        );
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
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "value2");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "value3");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("numResults", 2);
    queryParam.put("name", "var");
    queryParam.put("type", "string");
    List<String> variableResponse = getVariableValues(processDefinitionId, queryParam);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.contains("value1"), is(true));
    assertThat(variableResponse.contains("value2"), is(true));
  }

  @Test
  public void valuesListIsCutByAnOffset() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "value2");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "value3");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("resultOffset", 1);
    queryParam.put("name", "var");
    queryParam.put("type", "string");
    List<String> variableResponse = getVariableValues(processDefinitionId, queryParam);

    // then
    assertThat(variableResponse.size(), is(2));
    assertThat(variableResponse.contains("value2"), is(true));
    assertThat(variableResponse.contains("value3"), is(true));
  }

  @Test
  public void valuesListIsCutByAnOffsetAndMaxResults() throws Exception {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "value2");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", "value3");
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("numResults", 1);
    queryParam.put("resultOffset", 1);
    queryParam.put("name", "var");
    queryParam.put("type", "string");
    List<String> variableResponse = getVariableValues(processDefinitionId, queryParam);

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.contains("value2"), is(true));
  }

  @Test
  public void missingNameQueryParamThrowsError() throws Exception {
    // given
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("type", "string");

    //when
    Response response = getVariableValueResponse("aProcDefId", queryParam);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingTypeQueryParamThrowsError() throws Exception {
    // given
    Map<String, Object> queryParam = new HashMap<>();
    queryParam.put("name", "var");

    //when
    Response response = getVariableValueResponse("fooProcDefId", queryParam);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private String deploySimpleProcessDefinition() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .endEvent()
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    return processDefinitionId;
  }

  private List<String> getVariableValues(String processDefinitionId, String name) {
    Map<String, Object> queryParams = new HashMap<>();
    queryParams.put("name", name);
    queryParams.put("type", "String");
    return getVariableValues(processDefinitionId, queryParams);
  }

  private List<String> getVariableValues(String processDefinitionId, Map<String, Object> queryParams) {
    String token = embeddedOptimizeRule.getAuthenticationToken();

    WebTarget webTarget = embeddedOptimizeRule.target("variables/" + processDefinitionId + "/values");
    for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
      webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
    }
    Response response =
      webTarget
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .get();
    return response.readEntity(new GenericType<List<String>>() {
    });
  }

  private Response getVariableValueResponse(String processDefinitionId, Map<String, Object> queryParams) {
    String token = embeddedOptimizeRule.getAuthenticationToken();

    WebTarget webTarget = embeddedOptimizeRule.target("variables/" + processDefinitionId + "/values");
    for (Map.Entry<String, Object> queryParam : queryParams.entrySet()) {
      webTarget = webTarget.queryParam(queryParam.getKey(), queryParam.getValue());
    }
    Response response =
      webTarget
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .get();
    return response;
  }
}
