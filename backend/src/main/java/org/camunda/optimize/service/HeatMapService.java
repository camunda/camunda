package org.camunda.optimize.service;

import org.camunda.optimize.service.es.HeatMapReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
@Component
public class HeatMapService {
  @Autowired
  private HeatMapReader heatMapReader;

  public Map<String, Long> getHeatMap(String processDefinitionId) {
    return heatMapReader.getHeatMap(processDefinitionId);
  }

  public Long activityCorrelation (String process, List<String> activities) {
    return heatMapReader.activityCorrelation(process,activities);
  }
}
