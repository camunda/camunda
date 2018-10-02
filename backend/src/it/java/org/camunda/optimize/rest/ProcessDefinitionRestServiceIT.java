package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.SimpleEventDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.definition.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Before;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;



public class ProcessDefinitionRestServiceIT {
  private static final String DIAGRAM = "org/camunda/optimize/service/es/reader/gateway_process.bpmn";
  private static final String PROCESS_DEFINITION_ID_2 = "procDef2";
  private static final String PROCESS_DEFINITION_ID = "procDef1";
  private static final String PROCESS_DEFINITION_KEY = "procDef";
  private static final String PROCESS_DEFINITION_VERSION_1 = "1";
  private static final String PROCESS_DEFINITION_VERSION_2 = "2";
  private static final String END_ACTIVITY = "endActivity";
  private static final String GATEWAY_ACTIVITY = "gw_1";
  private static final String PROCESS_INSTANCE_ID = "processInstanceId";
  private static final String PROCESS_INSTANCE_ID_2 = PROCESS_INSTANCE_ID + "2";
  private static final String TASK = "task_1";

  private static final String ID = "123";
  private static final String KEY = "testKey";
  private static final String BPMN_20_XML = "test";
  private static final String TEST_ENGINE = "1";
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private ConfigurationService configurationService;

  @Before
  public void init() {
    configurationService = embeddedOptimizeRule.getConfigurationService();
  }

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Test
  public void getProcessDefinitionsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildGetProcessDefinitionsRequest()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitions() {
    //given
    createProcessDefinition(ID, KEY);

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetProcessDefinitionsRequest()
            .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then the status code is okay
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(ID));
  }

  @Test
  public void getProcessDefinitionsWithXml() {
    //given
    String expectedProcessDefinitionId = ID;
    createProcessDefinition(expectedProcessDefinitionId, KEY);

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeRule
              .getRequestExecutor()
              .buildGetProcessDefinitionsRequest()
              .addSingleQueryParam("includeXml", true)
              .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.get(0).getId(), is(expectedProcessDefinitionId));
    assertThat(definitions.get(0).getBpmn20Xml(), is("test"));
  }

  private void createProcessDefinition(String expectedProcessDefinitionId, String key) {
    createProcessDefinition(expectedProcessDefinitionId, key, "0");
  }

  private void createProcessDefinition(String expectedProcessDefinitionId, String key, String version) {
    ProcessDefinitionOptimizeDto expected = new ProcessDefinitionOptimizeDto();
    expected.setId(expectedProcessDefinitionId);
    expected.setKey(key);
    expected.setVersion(version);
    expected.setBpmn20Xml(BPMN_20_XML);
    expected.setFlowNodeNames(new HashMap<>());
    expected.setEngine(TEST_ENGINE);
    elasticSearchRule.addEntryToElasticsearch(configurationService.getProcessDefinitionType(), expectedProcessDefinitionId, expected);
  }


  @Test
  public void getProcessDefinitionXmlWithoutAuthentication() {
    // when
    Response response =
        embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildGetProcessDefinitionXmlRequest("foo", "bar")
            .execute();


    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getProcessDefinitionXml() {
    //given
    ProcessDefinitionOptimizeDto expectedXml = new ProcessDefinitionOptimizeDto();
    expectedXml.setBpmn20Xml("ProcessModelXml");
    expectedXml.setKey("aProcDefKey");
    expectedXml.setVersion("aProcDefVersion");
    expectedXml.setId("aProcDefId");
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionType(), ID, expectedXml);

    // when
    String actualXml =
            embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetProcessDefinitionXmlRequest("aProcDefKey", "aProcDefVersion")
            .execute(String.class, 200);

    // then
    assertThat(actualXml, is(expectedXml.getBpmn20Xml()));
  }

  @Test
  public void getProcessDefinitionXmlWithNonsenseVersionReturns404Code() {
    //given
    ProcessDefinitionOptimizeDto expectedXml = new ProcessDefinitionOptimizeDto();
    expectedXml.setBpmn20Xml("ProcessModelXml");
    expectedXml.setKey("aProcDefKey");
    expectedXml.setVersion("aProcDefVersion");
    expectedXml.setId("aProcDefId");
    elasticSearchRule.addEntryToElasticsearch(configurationService.getProcessDefinitionType(), ID, expectedXml);

    // when
    String message =
            embeddedOptimizeRule
                    .getRequestExecutor()
                    .buildGetProcessDefinitionXmlRequest("aProcDefKey", "nonsenseVersion")
                    .execute(String.class, 404);

    // then
    assertThat(message.contains("Could not find xml for process definition with key"), is(true));
  }

  @Test
  public void getProcessDefinitionXmlWithNonsenseKeyReturns404Code() {
    //given
    ProcessDefinitionOptimizeDto expectedXml = new ProcessDefinitionOptimizeDto();
    expectedXml.setBpmn20Xml("ProcessModelXml");
    expectedXml.setKey("aProcDefKey");
    expectedXml.setVersion("aProcDefVersion");
    expectedXml.setId("aProcDefId");
    elasticSearchRule.addEntryToElasticsearch(configurationService.getProcessDefinitionType(), ID, expectedXml);

    // when
    String message =
            embeddedOptimizeRule
                    .getRequestExecutor()
                    .buildGetProcessDefinitionXmlRequest("nonsenseKey", "aProcDefVersion")
                    .execute(String.class, 404);

    assertThat(message.contains("Could not find xml for process definition with key"), is(true));
  }

  @Test
  public void getCorrelationWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildProcessDefinitionCorrelation(new BranchAnalysisQueryDto())
            .withoutAuthentication()
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getCorrelation() throws IOException {
    //given
    setupFullInstanceFlow();

    // when
    BranchAnalysisQueryDto branchAnalysisQueryDto = new BranchAnalysisQueryDto();
    branchAnalysisQueryDto.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    branchAnalysisQueryDto.setProcessDefinitionVersion(PROCESS_DEFINITION_VERSION_1);
    branchAnalysisQueryDto.setGateway(GATEWAY_ACTIVITY);
    branchAnalysisQueryDto.setEnd(END_ACTIVITY);

    Response response =
            embeddedOptimizeRule
                    .getRequestExecutor()
                    .buildProcessDefinitionCorrelation(branchAnalysisQueryDto)
                    .execute();

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
    createProcessDefinitionsForKey("procDefKey1", 11);
    createProcessDefinitionsForKey("procDefKey2", 2);

    // when
    List <ProcessDefinitionGroupOptimizeDto> actual = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetProcessDefinitionsGroupedByKeyRequest()
            .executeAndReturnList(ProcessDefinitionGroupOptimizeDto.class, 200);

    // then
    assertThat(actual, is(notNullValue()));
    assertThat(actual.size(), is(2));
    // assert that procDefKey1 comes first in list
    actual.sort(Comparator.comparing(ProcessDefinitionGroupOptimizeDto::getVersions, Comparator.comparing(v -> v.get(0).getKey())));
    ProcessDefinitionGroupOptimizeDto procDefs1 = actual.get(0);
    assertThat(procDefs1.getKey(), is("procDefKey1"));
    assertThat(procDefs1.getVersions().size(), is(11));
    assertThat(procDefs1.getVersions().get(0).getVersion(), is("10"));
    assertThat(procDefs1.getVersions().get(1).getVersion(), is("9"));
    assertThat(procDefs1.getVersions().get(2).getVersion(), is("8"));
    ProcessDefinitionGroupOptimizeDto procDefs2 = actual.get(1);
    assertThat(procDefs2.getKey(), is("procDefKey2"));
    assertThat(procDefs2.getVersions().size(), is(2));
    assertThat(procDefs2.getVersions().get(0).getVersion(), is("1"));
    assertThat(procDefs2.getVersions().get(1).getVersion(), is("0"));
  }

  private void createProcessDefinitionsForKey(String key, int count) {
    IntStream.range(0, count).forEach(
      i -> {
        String constructedId = "id-" + key + "-version-" + i;
        createProcessDefinition(constructedId, key, String.valueOf(i) );
      }
    );
  }

  private void setupFullInstanceFlow() throws IOException {

    ProcessDefinitionOptimizeDto processDefinitionXmlDto = new ProcessDefinitionOptimizeDto();
    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID);
    processDefinitionXmlDto.setKey(PROCESS_DEFINITION_KEY);
    processDefinitionXmlDto.setVersion(PROCESS_DEFINITION_VERSION_1);
    processDefinitionXmlDto.setBpmn20Xml(readDiagram(DIAGRAM));
    elasticSearchRule.addEntryToElasticsearch(configurationService.getProcessDefinitionType(), PROCESS_DEFINITION_ID, processDefinitionXmlDto);
    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID_2);
    processDefinitionXmlDto.setVersion(PROCESS_DEFINITION_VERSION_2);
    elasticSearchRule.addEntryToElasticsearch(configurationService.getProcessDefinitionType(), PROCESS_DEFINITION_ID_2, processDefinitionXmlDto);

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    procInst.setProcessDefinitionVersion(PROCESS_DEFINITION_VERSION_1);
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

  private static String read(InputStream input) throws IOException {
    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
      return buffer.lines().collect(Collectors.joining("\n"));
    }
  }
}
