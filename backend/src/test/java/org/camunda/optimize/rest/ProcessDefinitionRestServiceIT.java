package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.SimpleEventDto;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class ProcessDefinitionRestServiceIT {
  private static final String DIAGRAM = "org/camunda/optimize/service/es/reader/gateway_process.bpmn";
  private static final String PROCESS_DEFINITION_ID_2 = "procDef2";
  private static final String PROCESS_DEFINITION_ID = "procDef1";
  private static final String END_ACTIVITY = "endActivity";
  private static final String GATEWAY_ACTIVITY = "gw_1";
  private static final String PROCESS_INSTANCE_ID = "processInstanceId";
  private static final String PROCESS_INSTANCE_ID_2 = PROCESS_INSTANCE_ID + "2";
  private static final String TASK = "task_1";

  private static final String ID = "123";
  public static final String BEARER = "Bearer ";
  private static final String KEY = "testKey";
  public static final String BPMN_20_XML = "test";
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule("classpath:rest/restEmbeddedOptimizeContext.xml");

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void getProcessDefinitionsWithoutAuthentication() throws IOException {
    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .request()
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitions() throws IOException {
    //given
    String token = embeddedOptimizeRule.authenticateAdmin();
    createProcessDefinition(ID, KEY);

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>() {
        });
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(ID));
  }

  @Test
  public void getProcessDefinitionsWithXml() throws IOException {
    //given
    String token = embeddedOptimizeRule.authenticateAdmin();
    String expectedProcessDefinitionId = ID;

    createProcessDefinition(expectedProcessDefinitionId, KEY);
    createProcessDefinitionXml(expectedProcessDefinitionId);

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .queryParam("includeXml", true)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>() {
        });
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(expectedProcessDefinitionId));
    assertThat(definitions.get(0).getBpmn20Xml(), is("test"));
  }

  private void createProcessDefinitionXml(String expectedProcessDefinitionXmlId) {
    ProcessDefinitionXmlOptimizeDto expectedXml = new ProcessDefinitionXmlOptimizeDto();
    expectedXml.setBpmn20Xml(BPMN_20_XML);
    expectedXml.setId(expectedProcessDefinitionXmlId);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), expectedProcessDefinitionXmlId, expectedXml);
  }

  private void createProcessDefinition(String expectedProcessDefinitionId, String key) {
    ProcessDefinitionOptimizeDto expected = new ProcessDefinitionOptimizeDto();
    expected.setId(expectedProcessDefinitionId);
    expected.setKey(key);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(), expectedProcessDefinitionId, expected);
  }


  @Test
  public void getProcessDefinitionXmlWithoutAuthentication() throws IOException {
    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/123/xml")
            .request()
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitionXml() throws IOException {
    //given
    String token = embeddedOptimizeRule.authenticateAdmin();

    ProcessDefinitionXmlOptimizeDto expectedXml = new ProcessDefinitionXmlOptimizeDto();
    expectedXml.setBpmn20Xml("ProcessModelXml");
    expectedXml.setId(ID);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), ID, expectedXml);

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/123/xml")
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    String actualXml =
        response.readEntity(String.class);
    assertThat(actualXml, is(expectedXml.getBpmn20Xml()));
  }

  @Test
  public void getFrequencyHeatMapWithoutAuthentication() throws IOException {
    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/123/heatmap/frequency")
            .request()
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getFrequencyHeatMap() throws IOException {
    //given
    insert10ActivitiesWithDifferentPis();

    // when
    String token = embeddedOptimizeRule.authenticateAdmin();
    Response response =
        embeddedOptimizeRule.target("process-definition/123/heatmap/frequency")
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    HeatMapResponseDto actual =
        response.readEntity(HeatMapResponseDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getPiCount(), is(10L));
  }

  private void insert10ActivitiesWithDifferentPis() {
    for (int i = 0; i < 10; i++) {
      String processInstanceId = "PI_" + i;

      SimpleEventDto event = new SimpleEventDto();
      event.setActivityId("A_" + i);
      event.setDurationInMs(Long.valueOf(i));
      ProcessInstanceDto procInst = new ProcessInstanceDto();
      procInst.setProcessDefinitionId(ID);
      procInst.setProcessInstanceId(processInstanceId);
      procInst.setEvents(Collections.singletonList(event));

      elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), processInstanceId, procInst);
    }
  }

  @Test
  public void getFrequencyHeatMapPostWithoutAuthentication() throws IOException {
    // when
    Entity<HeatMapQueryDto> entity = Entity.entity(new HeatMapQueryDto(), MediaType.APPLICATION_JSON);
    Response response =
        embeddedOptimizeRule.target("process-definition/heatmap/frequency")
            .request()
            .post(entity);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getFrequencyHeatPostMap() throws IOException {
    //given
    String token = embeddedOptimizeRule.authenticateAdmin();
    insert10ActivitiesWithDifferentPis();

    // when
    HeatMapQueryDto heatMapQueryDto = new HeatMapQueryDto();
    heatMapQueryDto.setProcessDefinitionId(ID);

    Entity<HeatMapQueryDto> entity = Entity.entity(heatMapQueryDto, MediaType.APPLICATION_JSON);
    Response response =
        embeddedOptimizeRule.target("process-definition/heatmap/frequency")
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(entity);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    HeatMapResponseDto actual =
        response.readEntity(HeatMapResponseDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getPiCount(), is(10L));
  }

  @Test
  public void getDurationHeatMapWithoutAuthentication() throws IOException {
    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/123/heatmap/duration")
            .request()
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDurationHeatMap() throws IOException {
    //given
    String token = embeddedOptimizeRule.authenticateAdmin();
    insert10ActivitiesWithDifferentPis();

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/123/heatmap/duration")
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    HeatMapResponseDto actual =
        response.readEntity(HeatMapResponseDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getPiCount(), is(10L));
  }

  @Test
  public void getDurationHeatMapPostWithoutAuthentication() throws IOException {
    // when
    Entity<HeatMapQueryDto> entity = Entity.entity(new HeatMapQueryDto(), MediaType.APPLICATION_JSON);
    Response response =
        embeddedOptimizeRule.target("process-definition/heatmap/duration")
            .request()
            .post(entity);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDurationHeatMapAsPost() throws IOException {
    //given
    String token = embeddedOptimizeRule.authenticateAdmin();
    insert10ActivitiesWithDifferentPis();

    // when
    HeatMapQueryDto heatMapQueryDto = new HeatMapQueryDto();
    heatMapQueryDto.setProcessDefinitionId(ID);

    Entity<HeatMapQueryDto> entity = Entity.entity(heatMapQueryDto, MediaType.APPLICATION_JSON);
    Response response =
        embeddedOptimizeRule.target("process-definition/heatmap/duration")
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(entity);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    HeatMapResponseDto actual =
        response.readEntity(HeatMapResponseDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getPiCount(), is(10L));
  }

  @Test
  public void getCorrelationWithoutAuthentication() throws IOException {
    // when
    Entity<BranchAnalysisQueryDto> entity = Entity.entity(new BranchAnalysisQueryDto(), MediaType.APPLICATION_JSON);
    Response response =
        embeddedOptimizeRule.target("process-definition/correlation")
            .request()
            .post(entity);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getCorrelation() throws IOException {
    //given
    String token = embeddedOptimizeRule.authenticateAdmin();
    setupFullInstanceFlow();

    // when
    BranchAnalysisQueryDto branchAnalysisQueryDto = new BranchAnalysisQueryDto();
    branchAnalysisQueryDto.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    branchAnalysisQueryDto.setGateway(GATEWAY_ACTIVITY);
    branchAnalysisQueryDto.setEnd(END_ACTIVITY);

    Entity<BranchAnalysisQueryDto> entity = Entity.entity(branchAnalysisQueryDto, MediaType.APPLICATION_JSON);
    Response response =
        embeddedOptimizeRule.target("process-definition/correlation")
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(entity);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    BranchAnalysisDto actual =
        response.readEntity(BranchAnalysisDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getTotal(), is(2L));
  }

  @Test
  public void testGetProcessDefinitionsGroupedByKey() {
    //given
    String token = embeddedOptimizeRule.authenticateAdmin();
    String expectedProcessDefinitionId = ID;

    createProcessDefinition(expectedProcessDefinitionId, KEY);
    createProcessDefinitionXml(expectedProcessDefinitionId + "_xml");

    String expectedProcessDefinitionId_2 = ID + "2";
    createProcessDefinition(expectedProcessDefinitionId_2, KEY);
    createProcessDefinitionXml(expectedProcessDefinitionId_2 + "_xml");

    Response response =
        embeddedOptimizeRule.target("process-definition/groupedByKey")
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .get();

    assertThat(response.getStatus(), is(200));
    List <ProcessDefinitionGroupOptimizeDto> actual =
        response.readEntity(new GenericType<List<ProcessDefinitionGroupOptimizeDto>>() {});
    assertThat(actual, is(notNullValue()));
    assertThat(actual.size(), is(1));
    assertThat(actual.get(0).getKey(), is(KEY));
    assertThat(actual.get(0).getVersions().size(), is(2));
  }

  @Test
  public void getProcessDefinitionsXml() throws Exception {
    String token = embeddedOptimizeRule.authenticateAdmin();
    String expectedProcessDefinitionId = ID;

    createProcessDefinition(expectedProcessDefinitionId, KEY);
    createProcessDefinitionXml(expectedProcessDefinitionId);

    String expectedProcessDefinitionId_2 = ID + "2";
    createProcessDefinition(expectedProcessDefinitionId_2, KEY);
    createProcessDefinitionXml(expectedProcessDefinitionId_2);

    List<String> ids = new ArrayList<>();
    ids.add(expectedProcessDefinitionId);
    ids.add(expectedProcessDefinitionId_2);
    Entity entity = Entity.entity(ids , MediaType.APPLICATION_JSON);
    Response response =
        embeddedOptimizeRule.target("process-definition/xml")
            .request(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(entity);
    assertThat(response.getStatus(), is(200));
    Map<String,String> actual =
        response.readEntity(new GenericType<Map<String,String>>() {});
    assertThat(actual, is(notNullValue()));
    assertThat(actual.get(expectedProcessDefinitionId), is(BPMN_20_XML));
    assertThat(actual.get(expectedProcessDefinitionId_2), is(BPMN_20_XML));
  }



  private void setupFullInstanceFlow() throws IOException {

    ProcessDefinitionXmlOptimizeDto processDefinitionXmlDto = new ProcessDefinitionXmlOptimizeDto();
    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID);
    processDefinitionXmlDto.setBpmn20Xml(readDiagram(DIAGRAM));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), PROCESS_DEFINITION_ID, processDefinitionXmlDto);
    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID_2);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), PROCESS_DEFINITION_ID_2, processDefinitionXmlDto);

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setStartDate(new Date());
    procInst.setEndDate(new Date());
    procInst.setEvents(createEventList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY, TASK}));

    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), PROCESS_INSTANCE_ID, procInst);
    procInst.setEvents(
        createEventList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY})
    );
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID_2);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), PROCESS_INSTANCE_ID_2, procInst);
  }

  private List<SimpleEventDto> createEventList(String[] activityIds) {
    List<SimpleEventDto> events = new ArrayList<>(activityIds.length);
    for (String activityId : activityIds) {
      SimpleEventDto event = new SimpleEventDto();
      event.setActivityId(activityId);
      events.add(event);
    }
    return events;
  }

  private String readDiagram(String diagramPath) throws IOException {
    return read(Thread.currentThread().getContextClassLoader().getResourceAsStream(diagramPath));
  }

  public static String read(InputStream input) throws IOException {
    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
      return buffer.lines().collect(Collectors.joining("\n"));
    }
  }
}
