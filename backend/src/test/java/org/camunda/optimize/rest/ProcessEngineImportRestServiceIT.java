package org.camunda.optimize.rest;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.CredentialsDto;
import org.camunda.optimize.dto.optimize.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/es-it-applicationContext.xml" })
public class ProcessEngineImportRestServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private static final String PROCESS_ID = "aProcessId";

  @Test
  public void importDataFromEngine() throws Exception {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .endEvent()
      .done();
    engineRule.deployAndStartProcess(processModel);

    //when
    embeddedOptimizeRule.importEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String token = embeddedOptimizeRule.authenticateAdmin();

    //when
    Response response = embeddedOptimizeRule.target("process-definition")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get();

    //then
    assertThat(response.getStatus(),is(200));
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>(){});

    assertThat(definitions,is(notNullValue()));
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(),is(notNullValue()));
    assertThat(definitions.get(0).getKey(),is(PROCESS_ID));
    assertThat(definitions.get(0).getVersion(),is(notNullValue()));
  }

}