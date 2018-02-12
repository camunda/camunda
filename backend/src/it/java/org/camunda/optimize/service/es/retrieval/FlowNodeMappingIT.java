package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesDto;
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
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class FlowNodeMappingIT {
  public static final String A_START = "aStart";
  public static final String A_TASK = "aTask";
  public static final String AN_END = "anEnd";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private final static String PROCESS_DEFINITION_KEY = "aProcess";

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);


  @Test
  public void mapFlowNodeIdsToNames() throws Exception {
    // given
    BpmnModelInstance modelInstance = getNamedBpmnModelInstance();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Response response =
        embeddedOptimizeRule.target("flow-node/" + processDefinitionId + "/flowNodeNames")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .post(null);

    // then
    FlowNodeNamesDto result = response.readEntity(FlowNodeNamesDto.class);
    assertThat(result, is(notNullValue()));
    assertThat(result.getFlowNodeNames(), is(notNullValue()));

    assertThat(result.getFlowNodeNames().values().size(), is(3));
    assertThat(result.getFlowNodeNames().values().contains(A_START), is(true));
    assertThat(result.getFlowNodeNames().values().contains(A_TASK), is(true));
    assertThat(result.getFlowNodeNames().values().contains(AN_END), is(true));
  }

  private BpmnModelInstance getNamedBpmnModelInstance() {
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .name(A_START)
          .serviceTask()
          .name(A_TASK)
          .camundaExpression("${true}")
        .endEvent()
          .name(AN_END)
        .done();
  }

  @Test
  public void mapFilteredFlowNodeIdsToNames() throws Exception {
    // given
    BpmnModelInstance modelInstance = getNamedBpmnModelInstance();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    StartEvent start = modelInstance.getModelElementsByType(StartEvent.class).iterator().next();


    // when
    List<String> ids = new ArrayList<>();
    ids.add(start.getId());
    Response response =
        embeddedOptimizeRule.target("flow-node/" + processDefinitionId + "/flowNodeNames")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .post(Entity.json(ids));

    // then
    FlowNodeNamesDto result = response.readEntity(FlowNodeNamesDto.class);
    assertThat(result, is(notNullValue()));
    assertThat(result.getFlowNodeNames(), is(notNullValue()));

    assertThat(result.getFlowNodeNames().values().size(), is(1));
    assertThat(result.getFlowNodeNames().values().contains(A_START), is(true));
  }
}
