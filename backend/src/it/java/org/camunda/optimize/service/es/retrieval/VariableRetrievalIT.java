package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.service.util.VariableHelper;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.service.util.VariableHelper.isVariableTypeSupported;
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
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

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
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

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
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<VariableRetrievalDto> variableResponse = getVariables(processDefinition);

    // then
    assertThat(variableResponse.size(), is(1));
    assertThat(variableResponse.get(0).getName(), is("var1"));
    assertThat(variableResponse.get(0).getType(), is(VariableHelper.STRING_TYPE));
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
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<VariableRetrievalDto> variableResponse = getVariables(processDefinition);

    // then
    assertThat(variableResponse.size(), is(3));
    assertThat(variableResponse.get(0).getName(), is("a"));
    assertThat(variableResponse.get(1).getName(), is("b"));
    assertThat(variableResponse.get(2).getName(), is("c"));
  }

  @Test
  public void variablesDoNotContainDuplicates() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

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
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

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
    variables.put("shortVar", (short)2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");

    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.resetImportStartIndexes();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<VariableRetrievalDto> variableResponse = getVariables(processDefinition);

    // then
    assertThat(variableResponse.size(), is(variables.size()));
    for (VariableRetrievalDto responseDto : variableResponse) {
      assertThat(variables.containsKey(responseDto.getName()), is(true));
      assertThat(isVariableTypeSupported(responseDto.getType()), is(true));
    }
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(A_PROCESS)
      .startEvent()
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private List<VariableRetrievalDto> getVariables(ProcessDefinitionEngineDto processDefinition) {
    String key = processDefinition.getKey();
    String version = String.valueOf(processDefinition.getVersion());
    return getVariables(key, version);
  }

  private List<VariableRetrievalDto> getVariables(String key, String version) {
    Response response =
        embeddedOptimizeRule.target("variables/")
            .queryParam("processDefinitionKey", key)
            .queryParam("processDefinitionVersion", version)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();
    return response.readEntity(new GenericType<List<VariableRetrievalDto>>() {});
  }

}
