package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.definition.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.ALL_VERSIONS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


public class ProcessDefinitionRetrievalIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private final static String PROCESS_DEFINITION_KEY = "aProcess";

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Before
  public void setUp() {
  }


  @Test
  public void getProcessDefinitionsWithMoreThenTen() throws Exception {
    for (int i = 0; i < 11; i++) {
      // given
      deploySimpleServiceTaskProcessDefinition(PROCESS_DEFINITION_KEY + System.currentTimeMillis());
    }
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>() {
        });

    assertThat(definitions.size(), is(11));
  }

  @Test
  public void getProcessDefinitions() throws Exception {

    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    String processDefinitionId = deploySimpleServiceTaskProcessDefinition(processId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>() {
        });

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(processDefinitionId));
    assertThat(definitions.get(0).getKey(), is(processId));
  }

  @Test
  public void getProcessDefinitionsWithXml() throws Exception {

    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
        .startEvent()
          .serviceTask()
            .camundaExpression("${true}")
        .endEvent()
        .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .queryParam("includeXml", true)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>() {
        });

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(processDefinitionId));
    assertThat(definitions.get(0).getKey(), is(processId));
    assertThat(definitions.get(0).getBpmn20Xml(), is(Bpmn.convertToString(modelInstance)));
  }

  @Test
  public void getProcessDefinitionsWithSeveralEventsForSameDefinitionDeployed() throws Exception {
    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    String processDefinitionId = deploySimpleServiceTaskProcessDefinition(processId);
    engineRule.startProcessInstance(processDefinitionId);
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>() {
        });

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(processDefinitionId));
    assertThat(definitions.get(0).getKey(), is(processId));
  }

  @Test
  public void getProcessDefinitionXmlByKeyAndVersion() throws Exception {
    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
        .startEvent()
          .serviceTask()
            .camundaExpression("${true}")
        .endEvent()
        .done();
    ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(modelInstance);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/xml")
            .queryParam("processDefinitionKey", processDefinition.getKey())
            .queryParam("processDefinitionVersion", processDefinition.getVersion())
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    String actualXml =
        response.readEntity(String.class);

    // then
    assertThat(actualXml, is(Bpmn.convertToString(modelInstance)));
  }

  @Test
  public void getProcessDefinitionXmlByKeyAndAllVersion() throws Exception {
    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
        .startEvent()
          .serviceTask()
            .camundaExpression("${true}")
        .endEvent()
        .done();
    engineRule.deployProcessAndGetProcessDefinition(modelInstance);
    modelInstance = Bpmn.createExecutableProcess(processId)
        .startEvent()
          .name("Add name to ensure that this is the latest version!")
          .serviceTask()
            .camundaExpression("${true}")
        .endEvent()
        .done();
    ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(modelInstance);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/xml")
            .queryParam("processDefinitionKey", processDefinition.getKey())
            .queryParam("processDefinitionVersion", ALL_VERSIONS)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    String actualXml =
        response.readEntity(String.class);

    // then
    assertThat(actualXml, is(Bpmn.convertToString(modelInstance)));
  }

  @Test
  public void testGetProcessDefinitionsXml() throws Exception {
    // given
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 11; i++) {
      String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
      String processDefinitionId = deploySimpleServiceTaskProcessDefinition(processId);
      ids.add(processDefinitionId);
    }
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/xmlsToIds")
            .queryParam("ids", ids.toArray())
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    Map<String, String> map = response.readEntity(new GenericType<Map<String, String>>() {});

    // then
    assertThat(map.size(), is(11));
  }

  private String deploySimpleServiceTaskProcessDefinition(String processId) throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
        .startEvent()
          .serviceTask()
            .camundaExpression("${true}")
        .endEvent()
        .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    return processDefinitionId;
  }

}
