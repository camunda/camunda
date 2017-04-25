package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.dto.optimize.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
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

    EventDto event1 = new EventDto();
    event1.setActivityId("testActivity");
    event1.setActivityInstanceId("1");
    event1.setProcessDefinitionId("123");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getEventType(),"1", event1);

    EventDto event2 = new EventDto();
    event2.setActivityId("testActivity");
    event2.setActivityInstanceId("2");
    event2.setProcessDefinitionId("123");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getEventType(),"2", event2);

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
