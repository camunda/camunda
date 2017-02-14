package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.*;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.junit.Before;
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
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it-applicationContext.xml"})
public class CorrelationReaderIT {
  public static final String PROCESS_DEFINITION_ID = "123";
  public static final String END_ACTIVITY = "endActivity";
  public static final String GATEWAY_ACTIVITY = "gw_1";
  public static final String PROCESS_INSTANCE_ID = "processInstanceId";
  public static final String DIAGRAM = "gateway_process.bpmn";
  public static final String TASK = "task_1";
  public static final String TASK_2 = "task_2";

  @Autowired
  @Rule
  public ElasticSearchIntegrationTestRule rule;

  @Autowired
  private CorrelationReader correlationReader;

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
    rule.addEntryToElasticsearch(configurationService.getEventType(), "1", event);

    event = new EventDto();
    event.setActivityId(GATEWAY_ACTIVITY);
    event.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "2", event);

    event = new EventDto();
    event.setActivityId(GATEWAY_ACTIVITY);
    event.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID + "2");
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
  public void activityCorrelation() throws Exception {
    //given

    //when
    CorrelationOutcomeDto result = correlationReader.activityCorrelation(PROCESS_DEFINITION_ID, GATEWAY_ACTIVITY, END_ACTIVITY);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getId(), is(GATEWAY_ACTIVITY));
    assertThat(result.getAll(), is(2L));
    assertThat(result.getReached(), is(1L));
  }

  @Test
  public void activityCorrelationWithDto() throws Exception {
    //given
    EventDto event = new EventDto();
    event.setActivityId(TASK);
    event.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    rule.addEntryToElasticsearch(configurationService.getEventType(), "4", event);

    event = new EventDto();
    event.setActivityId(END_ACTIVITY);
    event.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID + "2");
    rule.addEntryToElasticsearch(configurationService.getEventType(), "5", event);

    CorrelationQueryDto dto = new CorrelationQueryDto();
    dto.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    dto.setGateway(GATEWAY_ACTIVITY);
    dto.setEnd(END_ACTIVITY);

    //when
    GatewaySplitDto result = correlationReader.activityCorrelation(dto);
    //then

    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_ACTIVITY));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    CorrelationOutcomeDto task1 = result.getFollowingNodes().get(0);
    assertThat(task1.getId(), is(TASK));
    assertThat(task1.getReached(), is(1L));
    assertThat(task1.getAll(), is(1L));

    CorrelationOutcomeDto task2 = result.getFollowingNodes().get(1);
    assertThat(task2.getId(), is(TASK_2));
    assertThat(task2.getReached(), is(0L));
    assertThat(task2.getAll(), is(0L));
  }

}