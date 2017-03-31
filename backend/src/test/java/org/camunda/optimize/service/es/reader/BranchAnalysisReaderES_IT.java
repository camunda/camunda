package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.BranchAnalysisOutcomeDto;
import org.camunda.optimize.dto.optimize.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.dto.optimize.BranchAnalysisDto;
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
  public static final String PROCESS_DEFINITION_ID = "123";
  public static final String END_ACTIVITY = "endActivity";
  public static final String GATEWAY_ACTIVITY = "gw_1";
  public static final String PROCESS_INSTANCE_ID = "processInstanceId";
  public static final String PROCESS_INSTANCE_ID_2 = PROCESS_INSTANCE_ID + "2";
  public static final String DIAGRAM = "gateway_process.bpmn";
  public static final String TASK = "task_1";
  public static final String TASK_2 = "task_2";

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
    processDefinitionXmlDto.setBpmn20Xml(readDiagram());
    rule.addEntryToElasticsearch(configurationService.getProcessDefinitionXmlType(), PROCESS_DEFINITION_ID, processDefinitionXmlDto);

    EventDto event = new EventDto();
    event.setActivityId(END_ACTIVITY);
    event.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    event.setStartDate(new Date());
    event.setEndDate(new Date());
    event.setProcessInstanceStartDate(new Date());
    event.setProcessInstanceEndDate(new Date());
    rule.addEntryToElasticsearch(configurationService.getEventType(), "1", event);

    event = new EventDto();
    event.setActivityId(GATEWAY_ACTIVITY);
    event.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    event.setStartDate(new Date());
    event.setEndDate(new Date());
    event.setProcessInstanceStartDate(new Date());
    event.setProcessInstanceEndDate(new Date());
    rule.addEntryToElasticsearch(configurationService.getEventType(), "2", event);

    event = new EventDto();
    event.setActivityId(GATEWAY_ACTIVITY);
    event.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID_2);
    event.setStartDate(new Date());
    event.setEndDate(new Date());
    event.setProcessInstanceStartDate(new Date());
    event.setProcessInstanceEndDate(new Date());
    rule.addEntryToElasticsearch(configurationService.getEventType(), "3", event);
  }

  private String readDiagram() throws IOException {
    return read(Thread.currentThread().getContextClassLoader().getResourceAsStream(DIAGRAM));
  }

  public static String read(InputStream input) throws IOException {
    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
      return buffer.lines().collect(Collectors.joining("\n"));
    }
  }

  @Test
  public void branchAnalysis() throws Exception {
    //given

    //when
    BranchAnalysisOutcomeDto result = branchAnalysisReader.branchAnalysis(
        PROCESS_DEFINITION_ID, GATEWAY_ACTIVITY, END_ACTIVITY);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getActivityId(), is(GATEWAY_ACTIVITY));
    assertThat(result.getActivityCount(), is(2L));
    assertThat(result.getActivitiesReached(), is(1L));
  }

  @Test
  public void branchAnalysisWithDto() throws Exception {
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

  private BranchAnalysisQueryDto getBasicBranchAnalysisQueryDto() {
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    dto.setGateway(GATEWAY_ACTIVITY);
    dto.setEnd(END_ACTIVITY);
    return dto;
  }

  private void setupFullInstanceFlow() {
    EventDto event = new EventDto();
    event.setActivityId(TASK);
    event.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    event.setStartDate(new Date());
    event.setEndDate(new Date());
    event.setProcessInstanceStartDate(new Date());
    event.setProcessInstanceEndDate(new Date());
    rule.addEntryToElasticsearch(configurationService.getEventType(), "4", event);

    event = new EventDto();
    event.setActivityId(END_ACTIVITY);
    event.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID_2);
    event.setStartDate(new Date());
    event.setEndDate(new Date());
    event.setProcessInstanceStartDate(new Date());
    event.setProcessInstanceEndDate(new Date());
    rule.addEntryToElasticsearch(configurationService.getEventType(), "5", event);
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