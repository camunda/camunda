package org.camunda.optimize.dto.optimize;

/**
 * @author Askar Akhmerov
 */
public class CorrelationQueryDto extends HeatMapQueryDto {

  private String end;
  private String gateway;

  public String getEnd() {
    return end;
  }

  public void setEnd(String end) {
    this.end = end;
  }

  public String getGateway() {
    return gateway;
  }

  public void setGateway(String gateway) {
    this.gateway = gateway;
  }
}
