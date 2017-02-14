package org.camunda.optimize.dto.optimize;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class GatewaySplitDto {
  private String endEvent;
  private Long total;

  private List<CorrelationOutcomeDto> followingNodes = new ArrayList<>();

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

  public List<CorrelationOutcomeDto> getFollowingNodes() {
    return followingNodes;
  }

  public void setFollowingNodes(List<CorrelationOutcomeDto> followingNodes) {
    this.followingNodes = followingNodes;
  }
}
