package org.camunda.optimize.dto.optimize;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
public class GatewaySplitDto {
  protected String endEvent;
  protected Long total;

  protected Map<String, CorrelationOutcomeDto> followingNodes = new HashMap<>();

  public String getEndEvent() {
    return endEvent;
  }

  public void setEndEvent(String endEvent) {
    this.endEvent = endEvent;
  }

  public Long getTotal() {
    return total;
  }

  public void setTotal(Long total) {
    this.total = total;
  }

  public Map<String, CorrelationOutcomeDto> getFollowingNodes() {
    return followingNodes;
  }

  public void setFollowingNodes(Map<String, CorrelationOutcomeDto> followingNodes) {
    this.followingNodes = followingNodes;
  }
}
