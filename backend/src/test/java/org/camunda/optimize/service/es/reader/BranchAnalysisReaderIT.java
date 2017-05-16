package org.camunda.optimize.service.es.reader;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.BranchAnalysisOutcomeDto;
import org.camunda.optimize.dto.optimize.query.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.SimpleEventDto;
import org.camunda.optimize.dto.optimize.variable.VariableFilterDto;
import org.camunda.optimize.dto.optimize.variable.value.StringVariableDto;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.util.DataUtilHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
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
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/rest/restTestApplicationContext.xml"})
public class BranchAnalysisReaderIT {
  private static final String DIAGRAM = "org/camunda/optimize/service/es/reader/gateway_process.bpmn";
  private static final String PROCESS_DEFINITION_ID = "procDef1";
  private static final String PROCESS_DEFINITION_ID_2 = "procDef2";
  private static final String PROCESS_INSTANCE_ID = "processInstanceId";
  private static final String PROCESS_INSTANCE_ID_2 = PROCESS_INSTANCE_ID + "2";
  private static final String GATEWAY_ACTIVITY = "gw_1";
  private static final String TASK = "task_1";
  private static final String TASK_2 = "task_2";
  private static final String END_ACTIVITY = "endActivity";

  private static final String DIAGRAM_WITH_BYPASS = "org/camunda/optimize/service/es/reader/gatewayWithBypass.bpmn";
  private static final String PROCESS_DEFINITION_ID_BY_PASS = "procDefBypass";
  private static final String GATEWAY_B = "gw_b";
  private static final String GATEWAY_C = "gw_c";
  private static final String GATEWAY_D = "gw_d";
  private static final String GATEWAY_F = "gw_f";
  private static final String PROCESS_INSTANCE_ID_BY_PASS = PROCESS_INSTANCE_ID + "Bypass";

  private static final String VARIABLE_NAME = "var";
  private static final String VARIABLE_TYPE_STRING = "String";
  private static final String VARIABLE_VALUE = "aValue";
  private static final String VARIABLE_VALUE_2 = "anotherValue";

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule("classpath:rest/restEmbeddedOptimizeContext.xml");

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  public static String read(InputStream input) throws IOException {
    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
      return buffer.lines().collect(Collectors.joining("\n"));
    }
  }

  @Before
  public void setUp() throws Exception {
    // given
    ProcessDefinitionXmlOptimizeDto processDefinitionXmlDto = new ProcessDefinitionXmlOptimizeDto();
    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID);
    processDefinitionXmlDto.setBpmn20Xml(readDiagram(DIAGRAM));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), PROCESS_DEFINITION_ID, processDefinitionXmlDto);
    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID_2);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), PROCESS_DEFINITION_ID_2, processDefinitionXmlDto);
  }

  private String readDiagram(String diagramPath) throws IOException {
    return read(Thread.currentThread().getContextClassLoader().getResourceAsStream(diagramPath));
  }

  @Test
  public void branchAnalysis() throws Exception {
    //given
    setupFullInstanceFlow();
    BranchAnalysisQueryDto dto = getBasicBranchAnalysisQueryDto();

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_ACTIVITY));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK);
    assertThat(task1.getActivityId(), is(TASK));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_2);
    assertThat(task2.getActivityId(), is(TASK_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void anotherProcessDefinitionDoesNotAffectAnalysis() throws Exception {
    //given
    setupFullInstanceFlow();
    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID_2);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setStartDate(new Date());
    procInst.setEndDate(new Date());
    procInst.setEvents(createEventList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY, TASK_2}));

    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "radnomProcInstId1", procInst);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "radnomProcInstId2", procInst);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), "radnomProcInstId3", procInst);
    BranchAnalysisQueryDto dto = getBasicBranchAnalysisQueryDto();
    dto.setProcessDefinitionId(PROCESS_DEFINITION_ID_2);

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_ACTIVITY));
    assertThat(result.getTotal(), is(3L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK);
    assertThat(task1.getActivityId(), is(TASK));
    assertThat(task1.getActivitiesReached(), is(0L));
    assertThat(task1.getActivityCount(), is(0L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_2);
    assertThat(task2.getActivityId(), is(TASK_2));
    assertThat(task2.getActivitiesReached(), is(3L));
    assertThat(task2.getActivityCount(), is(3L));
  }

  private BranchAnalysisQueryDto getBasicBranchAnalysisQueryDto() {
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    dto.setGateway(GATEWAY_ACTIVITY);
    dto.setEnd(END_ACTIVITY);
    return dto;
  }

  private void setupFullInstanceFlow() {

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setStartDate(new Date());
    procInst.setEndDate(new Date());
    procInst.setEvents(createEventList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY, TASK}));
    procInst.addVariableInstance(createStringVariable(VARIABLE_VALUE));

    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), PROCESS_INSTANCE_ID, procInst);
    procInst.setEvents(createEventList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY}));
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID_2);
    procInst.addVariableInstance(createStringVariable(VARIABLE_VALUE_2));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), PROCESS_INSTANCE_ID_2, procInst);
  }

  private StringVariableDto createStringVariable(String variableValue) {
    StringVariableDto variableDto = new StringVariableDto();
    variableDto.setName(VARIABLE_NAME);
    variableDto.setType(VARIABLE_TYPE_STRING);
    variableDto.setValue(variableValue);
    return variableDto;
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

  @Test
  public void branchAnalysisWithDtoFilteredByDateBefore() throws Exception {
    //given
    setupFullInstanceFlow();
    BranchAnalysisQueryDto dto = getBasicBranchAnalysisQueryDto();
    DataUtilHelper.addDateFilter("<=", "start_date", new Date(), dto);

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_ACTIVITY));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK);
    assertThat(task1.getActivityId(), is(TASK));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_2);
    assertThat(task2.getActivityId(), is(TASK_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void branchAnalysisWithDtoFilteredByDateAfter() throws Exception {
    //given
    setupFullInstanceFlow();
    BranchAnalysisQueryDto dto = getBasicBranchAnalysisQueryDto();
    DataUtilHelper.addDateFilter(">", "start_date", new Date(), dto);

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_ACTIVITY));
    assertThat(result.getTotal(), is(0L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK);
    assertThat(task1.getActivityId(), is(TASK));
    assertThat(task1.getActivitiesReached(), is(0L));
    assertThat(task1.getActivityCount(), is(0L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_2);
    assertThat(task2.getActivityId(), is(TASK_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void branchAnalysisWithGtEndDateCriteria() throws Exception {
    //given
    setupFullInstanceFlow();
    BranchAnalysisQueryDto dto = getBasicBranchAnalysisQueryDto();
    DataUtilHelper.addDateFilter("<", "end_date", nowPlusTimeInMs(1000), dto);

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_ACTIVITY));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK);
    assertThat(task1.getActivityId(), is(TASK));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_2);
    assertThat(task2.getActivityId(), is(TASK_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void branchAnalysisWithMixedDateCriteria() throws Exception {
    //given
    setupFullInstanceFlow();
    BranchAnalysisQueryDto dto = getBasicBranchAnalysisQueryDto();
    DataUtilHelper.addDateFilter("<", "end_date", nowPlusTimeInMs(1000), dto);
    DataUtilHelper.addDateFilter(">", "start_date", nowPlusTimeInMs(-2000), dto);

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_ACTIVITY));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK);
    assertThat(task1.getActivityId(), is(TASK));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_2);
    assertThat(task2.getActivityId(), is(TASK_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void bypassOfGatewayDoesNotDistortResult() throws Exception {
    //given
    setupFullGatewayWithBypassFlow();
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(PROCESS_DEFINITION_ID_BY_PASS);
    dto.setGateway(GATEWAY_C);
    dto.setEnd(END_ACTIVITY);

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_ACTIVITY));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto gatewayD = result.getFollowingNodes().get(GATEWAY_D);
    assertThat(gatewayD.getActivityId(), is(GATEWAY_D));
    assertThat(gatewayD.getActivitiesReached(), is(1L));
    assertThat(gatewayD.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task = result.getFollowingNodes().get(TASK);
    assertThat(task.getActivityId(), is(TASK));
    assertThat(task.getActivitiesReached(), is(1L));
    assertThat(task.getActivityCount(), is(1L));
  }

  private void setupFullGatewayWithBypassFlow() throws IOException {
    addXmlToElasticsearch(PROCESS_DEFINITION_ID_BY_PASS, readDiagram(DIAGRAM_WITH_BYPASS));

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID_BY_PASS);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setStartDate(new Date());
    procInst.setEndDate(new Date());
    procInst.setEvents(createEventList(new String[]{GATEWAY_B, GATEWAY_D, GATEWAY_F, END_ACTIVITY}));

    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), PROCESS_INSTANCE_ID, procInst);
    procInst.setEvents(
      createEventList(new String[]{GATEWAY_B, GATEWAY_C, GATEWAY_D, GATEWAY_F, END_ACTIVITY})
    );
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID_2);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), PROCESS_INSTANCE_ID_2, procInst);
    procInst.setEvents(
      createEventList(new String[]{GATEWAY_B, GATEWAY_C, TASK, GATEWAY_F, END_ACTIVITY})
    );
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID_BY_PASS);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), PROCESS_INSTANCE_ID_BY_PASS, procInst);
  }

  @Test
  public void variableFilterWorkInBranchAnalysis() {
    //given
    setupFullInstanceFlow();
    BranchAnalysisQueryDto dto = getBasicBranchAnalysisQueryDto();
    VariableFilterDto filter = new VariableFilterDto();
    filter.setName(VARIABLE_NAME);
    filter.setType(VARIABLE_TYPE_STRING);
    filter.setOperator("=");
    filter.setValues(Collections.singletonList(VARIABLE_VALUE_2));
    FilterMapDto filterMapDto = new FilterMapDto();
    filterMapDto.setVariables(Collections.singletonList(filter));
    dto.setFilter(filterMapDto);

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_ACTIVITY));
    assertThat(result.getTotal(), is(1L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK);
    assertThat(task1.getActivityId(), is(TASK));
    assertThat(task1.getActivitiesReached(), is(0L));
    assertThat(task1.getActivityCount(), is(0L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_2);
    assertThat(task2.getActivityId(), is(TASK_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void shortcutInExclusiveGatewayDoesNotDistortBranchAnalysis() {

    // given
    String startEventId = "startEvent";
    String exclusiveSplittingGateway = "splittingGateway";
    String userTask = "userTask";
    String mergeExclusiveGateway = "mergeExclusiveGateway";
    String endEvent = "endEvent";
    BpmnModelInstance modelInstance = Bpmn.createProcess()
    .startEvent(startEventId)
    .exclusiveGateway(exclusiveSplittingGateway)
      .userTask(userTask)
      .exclusiveGateway(mergeExclusiveGateway)
      .endEvent(endEvent)
    .moveToLastGateway()
    .moveToLastGateway()
      .connectTo(mergeExclusiveGateway)
    .done();
    addXmlToElasticsearch(PROCESS_DEFINITION_ID, Bpmn.convertToString(modelInstance));

    List<SimpleEventDto> events = new ArrayList<>();
    events.add(createEventWithFlownodeType(startEventId));
    events.add(createEventWithGatewayType(exclusiveSplittingGateway));
    events.add(createEventWithGatewayType(mergeExclusiveGateway));
    events.add(createEventWithFlownodeType(endEvent));

    ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID);
    procInst.setEvents(events);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), PROCESS_INSTANCE_ID, procInst);

    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID_2);
    events.add(createEventWithFlownodeType(userTask));
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessInstanceType(), PROCESS_INSTANCE_ID_2, procInst);

    //when
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    dto.setGateway(exclusiveSplittingGateway);
    dto.setEnd(endEvent);
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(endEvent));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(userTask);
    assertThat(task1.getActivityId(), is(userTask));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(mergeExclusiveGateway);
    assertThat(task2.getActivityId(), is(mergeExclusiveGateway));
    assertThat(task2.getActivitiesReached(), is(1L));
    assertThat(task2.getActivityCount(), is(1L));
  }

  private void addXmlToElasticsearch(String processDefinitionId, String bpmn20Xml) {
    ProcessDefinitionXmlOptimizeDto processDefinitionXmlDto = new ProcessDefinitionXmlOptimizeDto();
    processDefinitionXmlDto.setId(processDefinitionId);
    processDefinitionXmlDto.setBpmn20Xml(bpmn20Xml);
    elasticSearchRule.addEntryToElasticsearch(elasticSearchRule.getProcessDefinitionXmlType(), processDefinitionId, processDefinitionXmlDto);
  }

  private SimpleEventDto createEventWithFlownodeType(String activityId) {
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(activityId);
    event.setActivityType("flowNode");
    return event;
  }

  private SimpleEventDto createEventWithGatewayType(String activityId) {
    SimpleEventDto event = new SimpleEventDto();
    event.setActivityId(activityId);
    event.setActivityType("exclusiveGateway");
    return event;
  }


  @Test
  public void testValidationExceptionOnNullDto() {

    //when
    Response response = getResponse(null);
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void testValidationExceptionOnNullProcessDefinition() {

    //when
    Response response = getResponse(new BranchAnalysisQueryDto());
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void testValidationExceptionOnNullGateway() {
    //given
    BranchAnalysisQueryDto request = new BranchAnalysisQueryDto();
    request.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    //when
    Response response = getResponse(request);

    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void testValidationExceptionOnNullEndActivity() {

    BranchAnalysisQueryDto request = new BranchAnalysisQueryDto();
    request.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    request.setEnd(GATEWAY_ACTIVITY);
    //when
    Response response = getResponse(request);

    assertThat(response.getStatus(), is(500));
  }


  private BranchAnalysisDto getBranchAnalysisDto(BranchAnalysisQueryDto dto) {
    String token = embeddedOptimizeRule.authenticateAdmin();
    Response response = getResponse(token, dto);

    // then the status code is okay
    return response.readEntity(BranchAnalysisDto.class);
  }

  private Response getResponse(String token, BranchAnalysisQueryDto dto) {
    Entity<BranchAnalysisQueryDto> entity = Entity.entity(dto, MediaType.APPLICATION_JSON);
    return embeddedOptimizeRule.target("process-definition/correlation")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .post(entity);
  }

  private Response getResponse(BranchAnalysisQueryDto request) {
    String token = embeddedOptimizeRule.authenticateAdmin();
    return getResponse(token, request);
  }

  private Date nowPlusTimeInMs(int timeInMs) {
    return new Date(new Date().getTime() + timeInMs);
  }

}