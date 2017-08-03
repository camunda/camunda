package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.variable.GetVariablesResponseDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class VariableRetrievalIT {
  
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private SimpleDateFormat simpleDateFormat;

  @Before
  public void init() {
    simpleDateFormat = new SimpleDateFormat(elasticSearchRule.getDateFormat());
  }

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private String deploySimpleProcessDefinition() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .endEvent()
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    return processDefinitionId;
  }

  @Test
  public void maxVariableValueListSize() throws IOException, OptimizeException {

    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    int exceededMaxVariableValue = embeddedOptimizeRule.getMaxVariableValueListSize() + 1;
    for (int i = 0; i < exceededMaxVariableValue; i++) {
      variables.put("var", i);
      engineRule.startProcessInstance(processDefinitionId, variables);
    }
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variableResponse = getGetVariablesResponseDtos(processDefinitionId);

    // then
    assertThat(variableResponse.size(), is(1));
    GetVariablesResponseDto responseDto = variableResponse.get(0);
    assertThat(responseDto.getValues().size(), is(15));
    assertThat(responseDto.isValuesAreComplete(), is(false));
  }


  @Test
  public void getVariables() throws IOException, OptimizeException {
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
    List<GetVariablesResponseDto> variableResponse = getGetVariablesResponseDtos(processDefinitionId);

    // then
    assertThat(variableResponse.size(), is(3));
    assertVariableNameValueRelation(variableResponse, "var1", "value1");
    assertVariableNameValueRelation(variableResponse, "var2", "value2");
    assertVariableNameValueRelation(variableResponse, "var3", "value3");
  }

  private void assertVariableNameValueRelation(List<GetVariablesResponseDto> variables, String name, String value) {
    boolean found = false;
    for (GetVariablesResponseDto variable : variables) {
      if (variable.getName().equals(name)) {
        assertThat(variable.getValues().get(0), is(value));
        assertThat(variable.isValuesAreComplete(), is(true));
        assertThat(variable.getType(), is("String"));
        found = true;
      }
    }
    assertThat(found, is(true));
  }

  @Test
  public void variableWithSameNameAndDifferentType() throws IOException, OptimizeException {

    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "stringValue");
    engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("var", 12345);
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variableResponse = getGetVariablesResponseDtos(processDefinitionId);

    // then
    assertThat(variableResponse.size(), is(2));
    for (GetVariablesResponseDto variable : variableResponse) {
      assertThat(variable.getName(), is("var"));
      assertThat(variable.getValues().size(), is(1));
      assertThat(variable.isValuesAreComplete(), is(true));
    }
  }

  @Test
  public void variablesInDifferentProcessDefinitionDoesNotAffectResult() throws IOException, OptimizeException {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "stringValue");
    engineRule.startProcessInstance(processDefinitionId, variables);
    String processDefinitionId2 = deploySimpleProcessDefinition();
    variables.put("boolVar", 12345);
    engineRule.startProcessInstance(processDefinitionId2, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variableResponse = getGetVariablesResponseDtos(processDefinitionId);

    // then
    assertThat(variableResponse.size(), is(1));
    GetVariablesResponseDto responseDto = variableResponse.get(0);
    assertThat(responseDto.getValues().size(), is(1));
    assertThat(responseDto.getName(), is("stringVar"));
    assertThat(responseDto.isValuesAreComplete(), is(true));
  }

  @Test
  public void allPrimitiveTypesCanBeRead() throws ParseException, IOException, OptimizeException {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", new Date());
    variables.put("boolVar", true);
    variables.put("shortVar", (short)2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");

    String processDefinitionId = deploySimpleProcessDefinition();
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.resetImportStartIndexes();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variableResponse = getGetVariablesResponseDtos(processDefinitionId);

    // then
    assertThat(variableResponse.size(), is(variables.size()));
    for (GetVariablesResponseDto responseDto : variableResponse) {
      assertThat(variables.containsKey(responseDto.getName()), is(true));
      assertThat(responseDto.getValues().size(), is(1));
      String expectedValue;
      if (responseDto.getType().toLowerCase().equals("date")) {
        expectedValue = simpleDateFormat.format(variables.get(responseDto.getName()));
      } else {
        expectedValue = variables.get(responseDto.getName()).toString();
      }
      assertThat(responseDto.getValues().get(0), is(expectedValue));
      assertThat(responseDto.isValuesAreComplete(), is(true));
    }
  }

  @Test
  public void variableValuesAreSortedByThereUsedFrequency() throws IOException, OptimizeException {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    int exceededMaxVariableValue = embeddedOptimizeRule.getMaxVariableValueListSize() + 1;
    Map<String, Object> variables = new HashMap<>();
    for (int variableValue = 0; variableValue < exceededMaxVariableValue; variableValue++) {
      for(int ithRepetition=variableValue; ithRepetition<exceededMaxVariableValue; ithRepetition++) {
        variables.put("var", variableValue);
        engineRule.startProcessInstance(processDefinitionId, variables);
      }
    }
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variableResponse = getGetVariablesResponseDtos(processDefinitionId);

    // then
    assertThat(variableResponse.size(), is(1));
    GetVariablesResponseDto responseDto = variableResponse.get(0);
    assertThat(responseDto.getValues().size(), is(15));
    assertThat(responseDto.isValuesAreComplete(), is(false));
    List<String> values = responseDto.getValues();
    for (int i=0; i<values.size(); i++) {
      assertThat(Integer.parseInt(values.get(i)), is(i));
    }
  }

  @Test
  public void variablesDoNotContainDuplicates() throws IOException, OptimizeException {
    // given
    String processDefinitionId = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "aValue");
    engineRule.startProcessInstance(processDefinitionId, variables);
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<GetVariablesResponseDto> variableResponse = getGetVariablesResponseDtos(processDefinitionId);

    // then
    assertThat(variableResponse.size(), is(1));
    GetVariablesResponseDto responseDto = variableResponse.get(0);
    assertThat(responseDto.getValues().size(), is(1));
    assertVariableNameValueRelation(variableResponse, "var", "aValue");
  }

  private List<GetVariablesResponseDto> getGetVariablesResponseDtos(String processDefinition) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
        embeddedOptimizeRule.target("process-definition/" + processDefinition + "/variables")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();
    return response.readEntity(new GenericType<List<GetVariablesResponseDto>>() {});
  }

}
