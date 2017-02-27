package org.camunda.optimize.dto.optimize;

import java.util.Map;

/**
 * @author Askar Akhmerov
 */
public class HeatMapResponseDto {
  private Map<String, Long> flowNodes;
  private Long piCount;

  public Map<String, Long> getFlowNodes() {
    return flowNodes;
  }

  public void setFlowNodes(Map<String, Long> flowNodes) {
    this.flowNodes = flowNodes;
  }

  public Long getPiCount() {
    return piCount;
  }

  public void setPiCount(Long piCount) {
    this.piCount = piCount;
  }
}
