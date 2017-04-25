package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.dto.optimize.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.rest.optimize.dto.ActivityListDto;
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
import java.util.Date;
import java.util.List;
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

  public static final String ID = "123";
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
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();

    ProcessDefinitionOptimizeDto expected = new ProcessDefinitionOptimizeDto();
    String expectedProcessDefinitionId = ID;
    expected.setId(expectedProcessDefinitionId);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(), ID,expected);

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>(){});
    assertThat(definitions,is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(expectedProcessDefinitionId));
  }

  @Test
  public void getProcessDefinitionsWithXml() throws IOException {
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();
    ProcessDefinitionOptimizeDto expected = new ProcessDefinitionOptimizeDto();
    String expectedProcessDefinitionId = ID;
    expected.setId(expectedProcessDefinitionId);

    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(), ID,expected);

    ProcessDefinitionXmlOptimizeDto expectedXml = new ProcessDefinitionXmlOptimizeDto();
    expectedXml.setBpmn20Xml("test");
    expectedXml.setId(ID);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), ID,expectedXml);

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition")
            .queryParam("includeXml", true)
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    List<ExtendedProcessDefinitionOptimizeDto> definitions =
        response.readEntity(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>(){});
    assertThat(definitions,is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(expectedProcessDefinitionId));
    assertThat(definitions.get(0).getBpmn20Xml(), is("test"));
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
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();

    ProcessDefinitionXmlOptimizeDto expectedXml = new ProcessDefinitionXmlOptimizeDto();
    expectedXml.setBpmn20Xml("ProcessModelXml");
    expectedXml.setId(ID);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), ID,expectedXml);

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/123/xml")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
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
    // given some mocks
    insert10ActivitiesWithDifferentPis();

    // when
    String token = embeddedOptimizeRule.authenticateAdmin();
    Response response =
        embeddedOptimizeRule.target("process-definition/123/heatmap/frequency")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .get();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    HeatMapResponseDto actual =
        response.readEntity(HeatMapResponseDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getPiCount(), is(10L));
  }

  private void insert10ActivitiesWithDifferentPis() {
    for (int i = 0; i< 10; i++) {
      String activityInstanceId = "AI_" + i;

      EventDto event = new EventDto();
      event.setActivityId("A_" + i);
      event.setActivityInstanceId(activityInstanceId);
      event.setStartDate(new Date());
      event.setEndDate(new Date());
      event.setProcessDefinitionId(ID);
      event.setProcessInstanceId("PI_" + i);
      event.setDurationInMs(Long.valueOf(i));
      elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getEventType(),activityInstanceId,event);
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
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();
    insert10ActivitiesWithDifferentPis();

    // when
    HeatMapQueryDto heatMapQueryDto = new HeatMapQueryDto();
    heatMapQueryDto.setProcessDefinitionId(ID);

    Entity<HeatMapQueryDto> entity = Entity.entity(heatMapQueryDto, MediaType.APPLICATION_JSON);
    Response response =
        embeddedOptimizeRule.target("process-definition/heatmap/frequency")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
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
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();
    insert10ActivitiesWithDifferentPis();

    // when
    Response response =
        embeddedOptimizeRule.target("process-definition/123/heatmap/duration")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
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
    // given some mocks
    String token = embeddedOptimizeRule.authenticateAdmin();
    insert10ActivitiesWithDifferentPis();

    // when
    HeatMapQueryDto heatMapQueryDto = new HeatMapQueryDto();
    heatMapQueryDto.setProcessDefinitionId(ID);

    Entity<HeatMapQueryDto> entity = Entity.entity(heatMapQueryDto, MediaType.APPLICATION_JSON);
    Response response =
        embeddedOptimizeRule.target("process-definition/heatmap/duration")
            .request()
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
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
    // given some mocks
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
            .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
            .post(entity);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    BranchAnalysisDto actual =
        response.readEntity(BranchAnalysisDto.class);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.getTotal(), is(2L));
  }


  private void setupFullInstanceFlow() throws IOException {

    ProcessDefinitionXmlOptimizeDto processDefinitionXmlDto = new ProcessDefinitionXmlOptimizeDto();
    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID);
    processDefinitionXmlDto.setBpmn20Xml(readDiagram(DIAGRAM));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), PROCESS_DEFINITION_ID, processDefinitionXmlDto);
    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID_2);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), PROCESS_DEFINITION_ID_2, processDefinitionXmlDto);


    ActivityListDto actList = new ActivityListDto();
    actList.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    actList.setActivityList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY, TASK});
    actList.setProcessInstanceStartDate(new Date());
    actList.setProcessInstanceEndDate(new Date());
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getBranchAnalysisDataType(), PROCESS_INSTANCE_ID, actList);
    actList.setActivityList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY});
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getBranchAnalysisDataType(), PROCESS_INSTANCE_ID_2, actList);
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
