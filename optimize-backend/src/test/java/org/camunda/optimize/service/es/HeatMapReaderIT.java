package org.camunda.optimize.service.es;

import org.camunda.optimize.service.HeatMapService;
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

  @Test
  public void getHeatMap() throws Exception {
    Map<String, Long> testDefinition = heatMapService.getHeatMap("testDefinition");
    assertThat(testDefinition.size(),is(1));
    assertThat(testDefinition.get("testactivity"),is(1L));
  }

}