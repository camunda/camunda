package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.rest.optimize.dto.ActivityListDto;
import org.camunda.optimize.dto.optimize.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.BranchAnalysisOutcomeDto;
import org.camunda.optimize.dto.optimize.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.util.DataUtilHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/es/it/es-it-applicationContext.xml"})
public class BranchAnalysisReaderES_IT {
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

  @Rule
  public ElasticSearchIntegrationTestRule rule = new ElasticSearchIntegrationTestRule();

  @Rule
  public final ExpectedException exception = ExpectedException.none();

  @Autowired
  private BranchAnalysisReader branchAnalysisReader;

  @Autowired
  private ConfigurationService configurationService;

  @Before
  public void setUp() throws Exception {
    // given
    ProcessDefinitionXmlOptimizeDto processDefinitionXmlDto = new ProcessDefinitionXmlOptimizeDto();
    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID);
    processDefinitionXmlDto.setBpmn20Xml(readDiagram(DIAGRAM));
    rule.addEntryToElasticsearch(configurationService.getProcessDefinitionXmlType(), PROCESS_DEFINITION_ID, processDefinitionXmlDto);
    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID_2);
    rule.addEntryToElasticsearch(configurationService.getProcessDefinitionXmlType(), PROCESS_DEFINITION_ID_2, processDefinitionXmlDto);
  }

  private String readDiagram(String diagramPath) throws IOException {
    return read(Thread.currentThread().getContextClassLoader().getResourceAsStream(diagramPath));
  }

  public static String read(InputStream input) throws IOException {
    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
      return buffer.lines().collect(Collectors.joining("\n"));
    }
  }

  @Test
  public void branchAnalysis() throws Exception {
    //given
    setupFullInstanceFlow();
    BranchAnalysisQueryDto dto = getBasicBranchAnalysisQueryDto();

    //when
    BranchAnalysisDto result = branchAnalysisReader.branchAnalysis(dto);

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
    ActivityListDto actList = new ActivityListDto();
    actList.setProcessDefinitionId(PROCESS_DEFINITION_ID_2);
    actList.setActivityList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY, TASK_2});
    actList.setProcessInstanceStartDate(new Date());
    actList.setProcessInstanceEndDate(new Date());
    rule.addEntryToElasticsearch(configurationService.getBranchAnalysisDataType(), "radnomProcInstId1", actList);
    rule.addEntryToElasticsearch(configurationService.getBranchAnalysisDataType(), "radnomProcInstId2", actList);
    rule.addEntryToElasticsearch(configurationService.getBranchAnalysisDataType(), "radnomProcInstId3", actList);
    BranchAnalysisQueryDto dto = getBasicBranchAnalysisQueryDto();
    dto.setProcessDefinitionId(PROCESS_DEFINITION_ID_2);

    //when
    BranchAnalysisDto result = branchAnalysisReader.branchAnalysis(dto);

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
    ActivityListDto actList = new ActivityListDto();
    actList.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    actList.setActivityList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY, TASK});
    actList.setProcessInstanceStartDate(new Date());
    actList.setProcessInstanceEndDate(new Date());
    rule.addEntryToElasticsearch(configurationService.getBranchAnalysisDataType(), PROCESS_INSTANCE_ID, actList);
    actList.setActivityList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY});
    rule.addEntryToElasticsearch(configurationService.getBranchAnalysisDataType(), PROCESS_INSTANCE_ID_2, actList);
  }

  @Test
  public void branchAnalysisWithDtoFilteredByDateBefore() throws Exception {
    //given
    setupFullInstanceFlow();
    BranchAnalysisQueryDto dto = getBasicBranchAnalysisQueryDto();
    DataUtilHelper.addDateFilter("<=", "start_date", new Date(), dto);

    //when
    BranchAnalysisDto result = branchAnalysisReader.branchAnalysis(dto);

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
    BranchAnalysisDto result = branchAnalysisReader.branchAnalysis(dto);

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
    BranchAnalysisDto result = branchAnalysisReader.branchAnalysis(dto);

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
    BranchAnalysisDto result = branchAnalysisReader.branchAnalysis(dto);

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

  private Date nowPlusTimeInMs(int timeInMs) {
    return new Date(new Date().getTime() + timeInMs);
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
    BranchAnalysisDto result = branchAnalysisReader.branchAnalysis(dto);

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
    ProcessDefinitionXmlOptimizeDto processDefinitionXmlDto = new ProcessDefinitionXmlOptimizeDto();
    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID_BY_PASS);
    processDefinitionXmlDto.setBpmn20Xml(readDiagram(DIAGRAM_WITH_BYPASS));
    rule.addEntryToElasticsearch(configurationService.getProcessDefinitionXmlType(), PROCESS_DEFINITION_ID_BY_PASS, processDefinitionXmlDto);

    ActivityListDto actList = new ActivityListDto();
    actList.setProcessDefinitionId(PROCESS_DEFINITION_ID_BY_PASS);
    actList.setActivityList(new String[]{GATEWAY_B, GATEWAY_D, GATEWAY_F, END_ACTIVITY});
    actList.setProcessInstanceStartDate(new Date());
    actList.setProcessInstanceEndDate(new Date());
    rule.addEntryToElasticsearch(configurationService.getBranchAnalysisDataType(), PROCESS_INSTANCE_ID, actList);
    actList.setActivityList(new String[]{GATEWAY_B, GATEWAY_C, GATEWAY_D, GATEWAY_F, END_ACTIVITY});
    rule.addEntryToElasticsearch(configurationService.getBranchAnalysisDataType(), PROCESS_INSTANCE_ID_2, actList);
    actList.setActivityList(new String[]{GATEWAY_B, GATEWAY_C, TASK, GATEWAY_F, END_ACTIVITY});
    rule.addEntryToElasticsearch(configurationService.getBranchAnalysisDataType(), PROCESS_INSTANCE_ID_BY_PASS, actList);
  }

  @Test
  public void testValidationExceptionOnNullDto () {
    //expect
    exception.expect(OptimizeValidationException.class);

    //when
    branchAnalysisReader.branchAnalysis(null);
  }

  @Test
  public void testValidationExceptionOnNullProcessDefinition () {
    //expect
    exception.expect(OptimizeValidationException.class);

    //when
    branchAnalysisReader.branchAnalysis(new BranchAnalysisQueryDto());
  }

  @Test
  public void testValidationExceptionOnNullGateway () {
    //expect
    exception.expect(OptimizeValidationException.class);
    //given
    BranchAnalysisQueryDto request = new BranchAnalysisQueryDto();
    request.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    //when
    branchAnalysisReader.branchAnalysis(request);
  }

  @Test
  public void testValidationExceptionOnNullEndActivity () {
    //expect
    exception.expect(OptimizeValidationException.class);

    BranchAnalysisQueryDto request = new BranchAnalysisQueryDto();
    request.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    request.setEnd(GATEWAY_ACTIVITY);
    //when
    branchAnalysisReader.branchAnalysis(new BranchAnalysisQueryDto());
  }


}