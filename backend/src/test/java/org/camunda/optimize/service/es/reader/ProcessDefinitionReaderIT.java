package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.query.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.SimpleEventDto;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class ProcessDefinitionReaderIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule("classpath:rest/restEmbeddedOptimizeContext.xml");

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void getProcessDefinitionsWithMoreThenTen() throws Exception {
    for (int i = 0; i < 11; i++) {
      // given
      String index = String.valueOf(1 + i);
      ProcessDefinitionOptimizeDto procDef = new ProcessDefinitionOptimizeDto();
      procDef.setId(index);
      procDef.setKey("testDefinition");
      elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(), index, procDef);
    }

    String token = embeddedOptimizeRule.authenticateAdmin();
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
    ProcessDefinitionOptimizeDto procDef = new ProcessDefinitionOptimizeDto();
    procDef.setId("123");
    procDef.setKey("testDefinition");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(),"123", procDef);

    String token = embeddedOptimizeRule.authenticateAdmin();
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
    assertThat(definitions.get(0).getId(), is("123"));
    assertThat(definitions.get(0).getKey(), is("testDefinition"));
  }

  @Test
  public void getProcessDefinitionsWithXml() throws Exception {

    // given
    ProcessDefinitionOptimizeDto procDef = new ProcessDefinitionOptimizeDto();
    procDef.setId("123");
    procDef.setKey("testDefinition");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(),"123", procDef);

    ProcessDefinitionXmlOptimizeDto xmlDto = new ProcessDefinitionXmlOptimizeDto();
    xmlDto.setId("123");
    String leadXml = readDiagram();
    xmlDto.setBpmn20Xml(leadXml);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(),"123", xmlDto);

    String token = embeddedOptimizeRule.authenticateAdmin();
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
    assertThat(definitions.get(0).getId(), is("123"));
    assertThat(definitions.get(0).getKey(), is("testDefinition"));
    assertThat(definitions.get(0).getBpmn20Xml(), is(leadXml));
  }

  @Test
  public void getProcessDefinitionsWithSeveralEventsForSameDefinitionDeployed() {

    // given
    ProcessDefinitionOptimizeDto procDef = new ProcessDefinitionOptimizeDto();
    procDef.setId("123");
    procDef.setKey("testDefinition");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(),"123", procDef);

    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId("testActivity");
    event.setId("1");
    SimpleEventDto event2 = new SimpleEventDto();
    event2.setActivityId("testActivity");
    event2.setId("2");
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId("123");
    List<SimpleEventDto> events = new LinkedList<>();
    events.add(event);
    events.add(event2);
    procInst.setEvents(events);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(),"1", procInst);

    String token = embeddedOptimizeRule.authenticateAdmin();
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
    assertThat(definitions.get(0).getId(), is("123"));
    assertThat(definitions.get(0).getKey(), is("testDefinition"));
  }

  @Test
  public void getProcessDefinitionXml() throws Exception {

    // given
    ProcessDefinitionXmlOptimizeDto xmlDto = new ProcessDefinitionXmlOptimizeDto();
    xmlDto.setId("123");
    String leadXml = readDiagram();
    xmlDto.setBpmn20Xml(leadXml);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(),"123", xmlDto);

    // when
    String token = embeddedOptimizeRule.authenticateAdmin();
    Response response =
        embeddedOptimizeRule.target("process-definition/123/xml")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();
    String actualXml =
        response.readEntity(String.class);

    // then
    assertThat(actualXml, is(leadXml));
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
