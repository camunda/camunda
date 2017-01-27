package org.camunda.optimize.service.es;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.HeatMapService;
import org.camunda.optimize.service.es.util.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.service.util.ConfigurationService;
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
  private HeatMapService heatMapService;

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
    Map<String, Long> testDefinition = heatMapService.getHeatMap("testDefinitionId");

    // then
    assertThat(testDefinition.size(),is(1));
    assertThat(testDefinition.get("testActivity"),is(1L));
  };

}