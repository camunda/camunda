package org.camunda.optimize.service.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.engine.ProcessDefinitionDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.util.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.service.util.ConfigurationService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it-applicationContext.xml"})
public class ProcessDefinitionReaderIT {

  @Rule
  public ElasticSearchIntegrationTestRule rule = ElasticSearchIntegrationTestRule.getInstance();

  @Autowired
  private ProcessDefinitionReader procDefReader;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  public void getProcessDefinitions() throws Exception {

    // given
    ProcessDefinitionDto procDef = new ProcessDefinitionDto();
    procDef.setId("123");
    procDef.setKey("testDefinition");
    rule.addEntryToElasticsearch(configurationService.getProcessDefinitionType(),"123", procDef);

    // when
    List<ProcessDefinitionDto> testDefinition = procDefReader.getProcessDefinitions();

    // then
    assertThat(testDefinition.size(), is(1));
    assertThat(testDefinition.get(0).getId(), is("123"));
    assertThat(testDefinition.get(0).getKey(), is("testDefinition"));
  }

  @Test
  public void getProcessDefinitionXml() throws Exception {

    // given
    ProcessDefinitionXmlDto xmlDto = new ProcessDefinitionXmlDto();
    xmlDto.setId("123");
    xmlDto.setBpmn20Xml("testBpmnXml");
    rule.addEntryToElasticsearch(configurationService.getProcessDefinitionXmlType(),"123", xmlDto);

    // when
    String testXml = procDefReader.getProcessDefinitionXmls("123");

    // then
    assertThat(testXml, is("testBpmnXml"));
  }

}