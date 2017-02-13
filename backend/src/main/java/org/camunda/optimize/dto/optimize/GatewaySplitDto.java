package org.camunda.optimize.dto.optimize;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class GatewaySplitDto {
  private String gateway;
  private String total;

  private List<CorrelationOutcomeDto> followingNodes = new ArrayList<>();

  public String getGateway() {
    return gateway;
  }

  public void setGateway(String gateway) {
    this.gateway = gateway;
  }

  public String getTotal() {
    return total;
  }

  public void setTotal(String total) {
    this.total = total;
  }

  public List<CorrelationOutcomeDto> getFollowingNodes() {
    return followingNodes;
  }

  public void setFollowingNodes(List<CorrelationOutcomeDto> followingNodes) {
    this.followingNodes = followingNodes;
  }
}
