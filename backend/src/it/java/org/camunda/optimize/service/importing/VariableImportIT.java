package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapSingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.ReportDataHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class VariableImportIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);


  @Test
  public void deletionOfProcessInstancesDoesNotDistortVariableInstanceImport() throws IOException {
    // given
    ProcessInstanceEngineDto firstProcInst = createImportAndDeleteTwoProcessInstances();

    // when
    engineRule.startProcessInstance(firstProcInst.getDefinitionId());
    engineRule.startProcessInstance(firstProcInst.getDefinitionId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then
    assertThatEveryFlowNodeWasExecuted4Times(firstProcInst.getProcessDefinitionKey());
  }

  @Test
  public void variableImportWorks() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
        .startEvent()
        .serviceTask()
          .camundaExpression("${true}")
        .endEvent()
      .done();

    Map<String, Object> variables = createPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    List<VariableRetrievalDto> variablesResponseDtos =
      embeddedOptimizeRule.target("variables")
        .queryParam("processDefinitionKey", instanceDto.getProcessDefinitionKey())
        .queryParam("processDefinitionVersion", instanceDto.getProcessDefinitionVersion())
        .request()
        .header(HttpHeaders.AUTHORIZATION,embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<VariableRetrievalDto>>(){});

    //then
    assertThat(variablesResponseDtos.size(),is(variables.size()));
  }

  @Test
  public void variablesWithComplexTypeAreNotImported() {
    // given
    ComplexVariableDto complexVariableDto = new ComplexVariableDto();
    complexVariableDto.setType("Object");
    complexVariableDto.setValue(null);
    ComplexVariableDto.ValueInfo info = new ComplexVariableDto.ValueInfo();
    info.setObjectTypeName("java.util.ArrayList");
    info.setSerializationDataFormat("application/json");
    complexVariableDto.setValueInfo(info);
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", complexVariableDto);
    ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceTaskWithVariables(variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<VariableRetrievalDto> variablesResponseDtos =
      embeddedOptimizeRule.target("variables")
        .queryParam("processDefinitionKey", instanceDto.getProcessDefinitionKey())
        .queryParam("processDefinitionVersion", instanceDto.getProcessDefinitionVersion())
        .request()
        .header(HttpHeaders.AUTHORIZATION,embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<VariableRetrievalDto>>(){});

    //then
    assertThat(variablesResponseDtos.size(),is(0));
  }

  private Map<String, Object> createPrimitiveTypeVariables() {
    Map<String, Object> variables = new HashMap<>();
    Integer integer = 1;
    variables.put("stringVar", "aStringValue");
    variables.put("boolVar", true);
    variables.put("integerVar", integer);
    variables.put("shortVar", integer.shortValue());
    variables.put("longVar", 1L);
    variables.put("doubleVar", 1.1);
    variables.put("dateVar", new Date());
    return variables;
  }

  @Test
  public void variableUpdateImport() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();

    Map<String, Object> variables = createPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    List<VariableRetrievalDto> variablesResponseDtos =
      embeddedOptimizeRule.target("variables")
        .queryParam("processDefinitionKey", instanceDto.getProcessDefinitionKey())
        .queryParam("processDefinitionVersion", instanceDto.getProcessDefinitionVersion())
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<VariableRetrievalDto>>() {
        });

    //then
    assertThat(variablesResponseDtos.size(), is(variables.size()));
  }

  @Test
  public void variableUpdatesOnSameVariableDoNotCreateSeveralVariables() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "foo")
      .userTask()
      .endEvent()
      .done();

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    List<VariableRetrievalDto> variablesResponseDtos =
      embeddedOptimizeRule.target("variables")
        .queryParam("processDefinitionKey", instanceDto.getProcessDefinitionKey())
        .queryParam("processDefinitionVersion", instanceDto.getProcessDefinitionVersion())
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<VariableRetrievalDto>>() {
        });

    //then
    assertThat(variablesResponseDtos.size(), is(1));
  }

  @Test
  public void onlyTheLatestVariableValueUpdateIsImported() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "foo")
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "foo1")
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "bar")
      .endEvent()
      .done();

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    List<String> variableValues =
      embeddedOptimizeRule.target("variables/values")
        .queryParam("processDefinitionKey", instanceDto.getProcessDefinitionKey())
        .queryParam("processDefinitionVersion", instanceDto.getProcessDefinitionVersion())
        .queryParam("name", "stringVar")
        .queryParam("type", "String")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<String>>() {
        });

    //then
    assertThat(variableValues.size(), is(1));
    assertThat(variableValues.get(0), is("bar"));
  }

  @Test
  public void variablesForFinishedProcessInstancesAreFinalResult() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "foo")
        .camundaOutputParameter("anotherVar", "1")
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "bar")
        .camundaOutputParameter("anotherVar", "2")
      .endEvent()
      .done();

    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcess(processModel);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    List<String> variableValues =
      embeddedOptimizeRule.target("variables/values")
        .queryParam("processDefinitionKey", instanceDto.getProcessDefinitionKey())
        .queryParam("processDefinitionVersion", instanceDto.getProcessDefinitionVersion())
        .queryParam("name", "stringVar")
        .queryParam("type", "String")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<String>>() {
        });

    //then
    assertThat(variableValues.size(), is(1));
    assertThat(variableValues.get(0), is("bar"));

    // when
    variableValues =
      embeddedOptimizeRule.target("variables/values")
        .queryParam("processDefinitionKey", instanceDto.getProcessDefinitionKey())
        .queryParam("processDefinitionVersion", instanceDto.getProcessDefinitionVersion())
        .queryParam("name", "anotherVar")
        .queryParam("type", "String")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<String>>() {
        });

    //then
    assertThat(variableValues.size(), is(1));
    assertThat(variableValues.get(0), is("2"));
  }


  @Test
  public void oldVariableUpdatesAreOverwritten() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
        .userTask()
        .serviceTask()
          .camundaExpression("${true}")
          .camundaOutputParameter("stringVar", "foo")
        .userTask()
      .endEvent()
      .done();

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    engineRule.finishAllUserTasks(instanceDto.getId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    List<String> variableValues =
      embeddedOptimizeRule.target("variables/values")
        .queryParam("processDefinitionKey", instanceDto.getProcessDefinitionKey())
        .queryParam("processDefinitionVersion", instanceDto.getProcessDefinitionVersion())
        .queryParam("name", "stringVar")
        .queryParam("type", "String")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<String>>() {
        });

    //then
    assertThat(variableValues.size(), is(1));
    assertThat(variableValues.get(0), is("foo"));
  }

  @Test
  public void deletingARuntimeVariableAlsoRemovesItFromOptimize() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    engineRule.deleteVariableInstanceForProcessInstance("stringVar", instanceDto.getId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    List<String> variableValues =
      embeddedOptimizeRule.target("variables/values")
        .queryParam("processDefinitionKey", instanceDto.getProcessDefinitionKey())
        .queryParam("processDefinitionVersion", instanceDto.getProcessDefinitionVersion())
        .queryParam("name", "stringVar")
        .queryParam("type", "String")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<String>>() {
        });

    //then
    assertThat(variableValues.size(), is(0));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
        .startEvent()
        .serviceTask()
          .camundaExpression("${true}")
        .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstances() throws IOException {
    return createImportAndDeleteTwoProcessInstancesWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstancesWithVariables(Map<String, Object> variables) throws IOException {
    ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTaskWithVariables(variables);
    ProcessInstanceEngineDto secondProcInst = engineRule.startProcessInstance(firstProcInst.getDefinitionId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    engineRule.deleteHistoricProcessInstance(firstProcInst.getId());
    engineRule.deleteHistoricProcessInstance(secondProcInst.getId());
    return firstProcInst;
  }

  private void assertThatEveryFlowNodeWasExecuted4Times(String processDefinitionKey) {
    SingleReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processDefinitionKey, ALL_VERSIONS
    );
    MapSingleReportResultDto result = evaluateReport(reportData);
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    for (Long frequency : flowNodeIdToExecutionFrequency.values()) {
      assertThat(frequency, is(4L));
    }
  }

  private MapSingleReportResultDto evaluateReport(SingleReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(MapSingleReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(SingleReportDataDto reportData) {
    return embeddedOptimizeRule.target("report/evaluate/single")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .post(Entity.json(reportData));
  }

}
