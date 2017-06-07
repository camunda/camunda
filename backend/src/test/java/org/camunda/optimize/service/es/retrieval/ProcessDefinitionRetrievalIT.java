package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.ExtendedProcessDefinitionOptimizeDto;
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

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class ProcessDefinitionRetrievalIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private final static String PROCESS_DEFINITION_KEY = "aProcess";

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void getProcessDefinitionsWithMoreThenTen() throws Exception {
    for (int i = 0; i < 11; i++) {
      // given
      deploySimpleServiceTaskProcessDefinition();
    }
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String token = embeddedOptimizeRule.getAuthenticationToken();
    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>(){});

    assertThat(definitions.size(), is(11));
  }

  @Test
  public void getProcessDefinitions() throws Exception {

    // given
    String processDefinitionId = deploySimpleServiceTaskProcessDefinition();
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String token = embeddedOptimizeRule.getAuthenticationToken();
    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>(){});

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(processDefinitionId));
    assertThat(definitions.get(0).getKey(), is(PROCESS_DEFINITION_KEY));
  }

  @Test
  public void getProcessDefinitionsWithXml() throws Exception {

    // given
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String token = embeddedOptimizeRule.getAuthenticationToken();
    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .queryParam("includeXml", true)
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>(){});

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(processDefinitionId ));
    assertThat(definitions.get(0).getKey(), is(PROCESS_DEFINITION_KEY));
    assertThat(definitions.get(0).getBpmn20Xml(), is(Bpmn.convertToString(modelInstance)));
  }

  @Test
  public void getProcessDefinitionsWithSeveralEventsForSameDefinitionDeployed() throws IOException, OptimizeException {
    // given
    String processDefinitionId = deploySimpleServiceTaskProcessDefinition();
    engineRule.startProcessInstance(processDefinitionId);
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String token = embeddedOptimizeRule.getAuthenticationToken();
    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>(){});

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(processDefinitionId));
    assertThat(definitions.get(0).getKey(), is(PROCESS_DEFINITION_KEY));
  }

  @Test
  public void getProcessDefinitionXml() throws Exception {
    // given
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("process-definition/" + processDefinitionId + "/xml")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .get();
    String actualXml =
        response.readEntity(String.class);

    // then
    assertThat(actualXml, is(Bpmn.convertToString(modelInstance)));
  }

  @Test
  public void testGetProcessDefinitionsXml () throws IOException, OptimizeException {
    // given
    List <String> ids = new ArrayList<>();
    for (int i = 0; i < 22; i++) {
      String processDefinitionId = deploySimpleServiceTaskProcessDefinition();
      ids.add(processDefinitionId);
    }
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Entity<List<String>> toPost = Entity.entity(ids, MediaType.APPLICATION_JSON);
    Response response =
        embeddedOptimizeRule.target("process-definition/xml")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .post(toPost);
    Map<String,String> map =
        response.readEntity(new GenericType<Map<String,String>>(){});

    // then
    assertThat(map.size(), is(22));
  }

  private String deploySimpleServiceTaskProcessDefinition() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    return processDefinitionId;
  }

}
