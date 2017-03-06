package org.camunda.optimize.rest;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.CredentialsDto;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
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
@ContextConfiguration(locations = { "/it-applicationContext.xml" })
public class ProcessEngineImportRestServiceIT extends AbstractJerseyTest {

  @Autowired
  @Rule
  public EngineIntegrationRule engineRule;

  @Autowired
  @Rule
  public ElasticSearchIntegrationTestRule elasticSearchRule;

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
    Response response = target("import")
        .request()
        .get();

    //then
    assertThat(response.getStatus(),is(200));

    //given
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String token = authenticateAdmin();

    //when
    response = target("process-definition")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get();

    //then
    assertThat(response.getStatus(),is(200));
    List<ProcessDefinitionEngineDto> definitions =
        response.readEntity(new GenericType<List<ProcessDefinitionEngineDto>>(){});

    assertThat(definitions,is(notNullValue()));
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(),is(notNullValue()));
    assertThat(definitions.get(0).getKey(),is(PROCESS_ID));
  }

  private String authenticateAdmin() {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername("admin");
    entity.setPassword("admin");

    Response tokenResponse =  target("authentication")
        .request()
        .post(Entity.json(entity));

    return tokenResponse.readEntity(String.class);
  }

  @Override
  protected String getContextLocation() {
    return "classpath:it-applicationContext.xml";
  }

}