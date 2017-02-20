package org.camunda.optimize.rest;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.service.importing.ImportScheduler;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EngineIntegrationRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.GenericType;
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

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ImportScheduler importScheduler;

  @Test
  public void importDataFromEngine() throws Exception {
    //given
    importScheduler.start();
    engineRule.deployServiceTaskProcess();

    //when
    Response response = target("import")
        .request()
        .get();

    //then
    assertThat(response.getStatus(),is(200));
    Thread.currentThread().sleep(configurationService.getImportHandlerWait() * 2);

    //given
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    //when
    response = target("process-definition")
        .request().get();

    //then
    assertThat(response.getStatus(),is(200));
    List<ProcessDefinitionEngineDto> definitions =
        response.readEntity(new GenericType<List<ProcessDefinitionEngineDto>>(){});

    assertThat(definitions,is(notNullValue()));
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(),is(notNullValue()));
  }

  @Override
  protected String getContextLocation() {
    return "classpath:it-applicationContext.xml";
  }

}