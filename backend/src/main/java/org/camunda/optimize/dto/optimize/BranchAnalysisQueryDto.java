package org.camunda.optimize.dto.optimize;

/**
 * @author Askar Akhmerov
 */
public class BranchAnalysisQueryDto extends HeatMapQueryDto {

  protected String end;
  protected String gateway;

  /**
   * The end event the branch analysis is referred to.
   */
  public String getEnd() {
    return end;
  }

  public void setEnd(String end) {
    this.end = end;
  }

  /**
   * The gateway the branch analysis is referred to.
   */
  public String getGateway() {
    return gateway;
  }

  public void setGateway(String gateway) {
    this.gateway = gateway;
  }
}
