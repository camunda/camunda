package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/es-it-applicationContext.xml"})
public class ProcessDefinitionReaderES_IT {

  @Rule
  public ElasticSearchIntegrationTestRule rule = new ElasticSearchIntegrationTestRule();

  @Autowired
  private ProcessDefinitionReader procDefReader;

  @Autowired
  private ConfigurationService configurationService;

  @Test
  public void getProcessDefinitionsWithMoreThenTen() throws Exception {
    for (int i = 0; i < 11; i++) {
      // given
      String index = String.valueOf(1 + i);
      ProcessDefinitionOptimizeDto procDef = new ProcessDefinitionOptimizeDto();
      procDef.setId(index);
      procDef.setKey("testDefinition");
      rule.addEntryToElasticsearch(configurationService.getProcessDefinitionType(), index, procDef);
    }

    // when
    List<ProcessDefinitionOptimizeDto> testDefinition = procDefReader.getProcessDefinitions();

    assertThat(testDefinition.size(), is(11));
  }

  @Test
  public void getProcessDefinitions() throws Exception {

    // given
    ProcessDefinitionOptimizeDto procDef = new ProcessDefinitionOptimizeDto();
    procDef.setId("123");
    procDef.setKey("testDefinition");
    rule.addEntryToElasticsearch(configurationService.getProcessDefinitionType(),"123", procDef);

    // when
    List<ProcessDefinitionOptimizeDto> testDefinition = procDefReader.getProcessDefinitions();

    // then
    assertThat(testDefinition.size(), is(1));
    assertThat(testDefinition.get(0).getId(), is("123"));
    assertThat(testDefinition.get(0).getKey(), is("testDefinition"));
  }

  @Test
  public void getProcessDefinitionsWithSeveralEventsForSameDefinitionDeployed() {

    // given
    ProcessDefinitionOptimizeDto procDef = new ProcessDefinitionOptimizeDto();
    procDef.setId("123");
    procDef.setKey("testDefinition");
    rule.addEntryToElasticsearch(configurationService.getProcessDefinitionType(),"123", procDef);

    EventDto event1 = new EventDto();
    event1.setActivityId("testActivity");
    event1.setActivityInstanceId("1");
    event1.setProcessDefinitionId("123");
    rule.addEntryToElasticsearch(configurationService.getEventType(),"1", event1);

    EventDto event2 = new EventDto();
    event2.setActivityId("testActivity");
    event2.setActivityInstanceId("2");
    event2.setProcessDefinitionId("123");
    rule.addEntryToElasticsearch(configurationService.getEventType(),"2", event2);

    // when
    List<ProcessDefinitionOptimizeDto> testDefinition = procDefReader.getProcessDefinitions();

    // then
    assertThat(testDefinition.size(), is(1));
    assertThat(testDefinition.get(0).getId(), is("123"));
    assertThat(testDefinition.get(0).getKey(), is("testDefinition"));
  }

  @Test
  public void getProcessDefinitionXml() throws Exception {

    // given
    ProcessDefinitionXmlOptimizeDto xmlDto = new ProcessDefinitionXmlOptimizeDto();
    xmlDto.setId("123");
    String leadXml = readDiagram();
    xmlDto.setBpmn20Xml(leadXml);
    rule.addEntryToElasticsearch(configurationService.getProcessDefinitionXmlType(),"123", xmlDto);

    // when
    String testXml = procDefReader.getProcessDefinitionXml("123");

    // then
    assertThat(testXml, is(leadXml));
  }

  private String readDiagram() throws IOException {
    return read(Thread.currentThread().getContextClassLoader().getResourceAsStream("org/camunda/optimize/service/es/reader/leadQualification.bpmn"));
  }

  public static String read(InputStream input) throws IOException {
    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
      return buffer.lines().collect(Collectors.joining("\n"));
    }
  }

}
