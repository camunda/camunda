package org.camunda.optimize.service.es;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/it-applicationContext.xml" })
public class HeatMapReaderIT {

  @Autowired
  private HeatMapReader heatMapReader;

  @Autowired
  private ConfigurationService configurationService;

  @Rule
  public ElasticSearchIntegrationTestRule rule = ElasticSearchIntegrationTestRule.getInstance();


  @Test
  public void getHeatMap() throws Exception {

    // given
    EventDto event = new EventDto();
    event.setActivityId("testActivity");
    event.setProcessDefinitionId("testDefinitionId");
    rule.addEntryToElasticsearch(configurationService.getEventType(),"5", event);

    // when
    Map<String, Long> testDefinition = heatMapReader.getHeatMap("testDefinitionId");

    // then
    assertThat(testDefinition.size(),is(1));
    assertThat(testDefinition.get("testActivity"),is(1L));
  }

  @Test
  public void getHeatMapMultipleEvents() throws Exception {

    // given
    EventDto event = new EventDto();
    event.setActivityId("testActivity");
    event.setProcessDefinitionId("testDefinitionId");
    rule.addEntryToElasticsearch(configurationService.getEventType(),"5", event);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"6", event);

    // when
    Map<String, Long> testDefinition = heatMapReader.getHeatMap("testDefinitionId");

    // then
    assertThat(testDefinition.size(),is(1));
    assertThat(testDefinition.get("testActivity"),is(2L));
  }

  @Test
  public void getHeatMapMultipleEventsWithMultipleProcesses() throws Exception {

    // given
    EventDto event = new EventDto();
    event.setActivityId("testActivity");
    event.setProcessDefinitionId("testDefinitionId1");
    rule.addEntryToElasticsearch(configurationService.getEventType(),"5", event);
    rule.addEntryToElasticsearch(configurationService.getEventType(),"6", event);

    event = new EventDto();
    event.setActivityId("testActivity");
    event.setProcessDefinitionId("testDefinitionId2");
    rule.addEntryToElasticsearch(configurationService.getEventType(),"7", event);

    // when
    Map<String, Long> testDefinition1 = heatMapReader.getHeatMap("testDefinitionId1");
    Map<String, Long> testDefinition2 = heatMapReader.getHeatMap("testDefinitionId2");

    // then
    assertThat(testDefinition1.size(),is(1));
    assertThat(testDefinition1.get("testActivity"),is(2L));
    assertThat(testDefinition2.size(),is(1));
    assertThat(testDefinition2.get("testActivity"),is(1L));
  }

}