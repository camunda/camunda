package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.CorrelationOutcomeDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.bpm.engine.impl.json.JsonTaskQueryConverter.PROCESS_INSTANCE_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.*;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/it-applicationContext.xml" })
public class CorrelationReaderIT {
  public static final String PROCESS_DEFINITION_ID = "testDefinitionId";
  public static final String END_ACTIVITY = "endActivity";
  public static final String GATEWAY_ACTIVITY = "testGatewayActivity";
  @Autowired
  @Rule
  public ElasticSearchIntegrationTestRule rule;

  @Autowired
  private CorrelationReader correlationReader;

  @Autowired
  private ConfigurationService configurationService;

  @Before
  public void setUp() {
    // given
    EventDto event = new EventDto();
    event.setActivityId(END_ACTIVITY);
    event.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"1", event);

    event = new EventDto();
    event.setActivityId(GATEWAY_ACTIVITY);
    event.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"2", event);

    event = new EventDto();
    event.setActivityId(GATEWAY_ACTIVITY);
    event.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    event.setProcessInstanceId(PROCESS_INSTANCE_ID + "2");
    rule.addEntryToElasticsearch(configurationService.getEventType(),"3", event);
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

  }

}