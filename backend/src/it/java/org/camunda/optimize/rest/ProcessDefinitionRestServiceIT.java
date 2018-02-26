package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.SimpleEventDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.definition.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.dto.optimize.query.heatmap.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.heatmap.HeatMapResponseDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */

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
  private static final String TEST_ENGINE = "1";
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void getProcessDefinitionsWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .request()
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitions() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
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
  public void getProcessDefinitionsWithXml() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
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
    expectedXml.setProcessDefinitionId(expectedProcessDefinitionXmlId);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), expectedProcessDefinitionXmlId, expectedXml);
  }

  private void createProcessDefinition(String expectedProcessDefinitionId, String key) {
    createProcessDefinition(expectedProcessDefinitionId, key, 0);
  }

  private void createProcessDefinition(String expectedProcessDefinitionId, String key, int version) {
    ProcessDefinitionOptimizeDto expected = new ProcessDefinitionOptimizeDto();
    expected.setId(expectedProcessDefinitionId);
    expected.setKey(key);
    expected.setVersion(version);
    expected.setEngine(TEST_ENGINE);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(), expectedProcessDefinitionId, expected);
  }


  @Test
  public void getProcessDefinitionXmlWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/xml")
            .queryParam("processDefinitionKey", "aProcDefKey")
            .queryParam("processDefinitionVersion", "aProcDefVersion")
            .request()
            .get();


    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitionXml() {
    //given
    ProcessDefinitionXmlOptimizeDto expectedXml = new ProcessDefinitionXmlOptimizeDto();
    expectedXml.setBpmn20Xml("ProcessModelXml");
    expectedXml.setProcessDefinitionKey("aProcDefKey");
    expectedXml.setProcessDefinitionVersion("aProcDefVersion");
    expectedXml.setProcessDefinitionId("aProcDefId");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), ID, expectedXml);

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/xml")
            .queryParam("processDefinitionKey", "aProcDefKey")
            .queryParam("processDefinitionVersion", "aProcDefVersion")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    String actualXml =
        response.readEntity(String.class);
    assertThat(actualXml, is(expectedXml.getBpmn20Xml()));
  }

  @Test
  public void getProcessDefinitionXmlWithNonsenseVersionReturns404Code() {
    //given
    ProcessDefinitionXmlOptimizeDto expectedXml = new ProcessDefinitionXmlOptimizeDto();
    expectedXml.setBpmn20Xml("ProcessModelXml");
    expectedXml.setProcessDefinitionKey("aProcDefKey");
    expectedXml.setProcessDefinitionVersion("aProcDefVersion");
    expectedXml.setProcessDefinitionId("aProcDefId");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), ID, expectedXml);

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/xml")
            .queryParam("processDefinitionKey", "aProcDefKey")
            .queryParam("processDefinitionVersion", "nonsenseVersion")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(404));
    String message =
        response.readEntity(String.class);
    assertThat(message.contains("Could not find xml for process definition with key"), is(true));
  }

  @Test
  public void getProcessDefinitionXmlWithNonsenseKeyReturns404Code() {
    //given
    ProcessDefinitionXmlOptimizeDto expectedXml = new ProcessDefinitionXmlOptimizeDto();
    expectedXml.setBpmn20Xml("ProcessModelXml");
    expectedXml.setProcessDefinitionKey("aProcDefKey");
    expectedXml.setProcessDefinitionVersion("aProcDefVersion");
    expectedXml.setProcessDefinitionId("aProcDefId");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), ID, expectedXml);

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/xml")
            .queryParam("processDefinitionKey", "nonsenseKey")
            .queryParam("processDefinitionVersion", "aProcDefVersion")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(404));
    String message =
        response.readEntity(String.class);
    assertThat(message.contains("Could not find xml for process definition with key"), is(true));
  }

  @Test
  public void getFrequencyHeatMapWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/123/heatmap/frequency")
            .request()
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getFrequencyHeatMap() {
    //given
    insert10ActivitiesWithDifferentPis();

    // when
    String token = embeddedOptimizeRule.getAuthenticationToken();
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
  public void getFrequencyHeatMapPostWithoutAuthentication() {
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
  public void getFrequencyHeatPostMap() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
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
  public void getCorrelationWithoutAuthentication() {
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
    String token = embeddedOptimizeRule.getAuthenticationToken();
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
    String token = embeddedOptimizeRule.getAuthenticationToken();
    createProcessDefinitionsForKey("procDefKey1", 3);
    createProcessDefinitionsForKey("procDefKey2", 2);

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/groupedByKey")
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .get();

    // then
    assertThat(response.getStatus(), is(200));
    List <ProcessDefinitionGroupOptimizeDto> actual =
        response.readEntity(new GenericType<List<ProcessDefinitionGroupOptimizeDto>>() {});
    assertThat(actual, is(notNullValue()));
    assertThat(actual.size(), is(2));
    // assert that proceDefKey1 comes first in list
    actual.sort(Comparator.comparing(ProcessDefinitionGroupOptimizeDto::getVersions, Comparator.comparing(v -> v.get(0).getKey())));
    ProcessDefinitionGroupOptimizeDto procDefs1 = actual.get(0);
    assertThat(procDefs1.getKey(), is("procDefKey1"));
    assertThat(procDefs1.getVersions().size(), is(3));
    assertThat(procDefs1.getVersions().get(0).getVersion(), is(2L));
    assertThat(procDefs1.getVersions().get(1).getVersion(), is(1L));
    assertThat(procDefs1.getVersions().get(2).getVersion(), is(0L));
    ProcessDefinitionGroupOptimizeDto procDefs2 = actual.get(1);
    assertThat(procDefs2.getKey(), is("procDefKey2"));
    assertThat(procDefs2.getVersions().size(), is(2));
    assertThat(procDefs2.getVersions().get(0).getVersion(), is(1L));
    assertThat(procDefs2.getVersions().get(1).getVersion(), is(0L));
  }

  private void createProcessDefinitionsForKey(String key, int count) {
    IntStream.range(0, count).forEach(
      i -> {
        String constructedId = "id-" + key + "-version-" + i;
        createProcessDefinition(constructedId, key, i);
      }
    );
  }

  private void setupFullInstanceFlow() throws IOException {

    ProcessDefinitionXmlOptimizeDto processDefinitionXmlDto = new ProcessDefinitionXmlOptimizeDto();
    processDefinitionXmlDto.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    processDefinitionXmlDto.setBpmn20Xml(readDiagram(DIAGRAM));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), PROCESS_DEFINITION_ID, processDefinitionXmlDto);
    processDefinitionXmlDto.setProcessDefinitionId(PROCESS_DEFINITION_ID_2);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), PROCESS_DEFINITION_ID_2, processDefinitionXmlDto);

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setStartDate(OffsetDateTime.now());
    procInst.setEndDate(OffsetDateTime.now());
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
