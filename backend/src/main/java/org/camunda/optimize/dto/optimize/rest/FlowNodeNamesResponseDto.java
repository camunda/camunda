package org.camunda.optimize.dto.optimize.rest;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
public class FlowNodeNamesResponseDto {

  private Map<String, String> flowNodeNames = new HashMap<>();

  public Map<String, String> getFlowNodeNames() {
    return flowNodeNames;
  }

  public void setFlowNodeNames(Map<String, String> flowNodeNames) {
    this.flowNodeNames = flowNodeNames;
  }
}
