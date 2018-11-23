package org.camunda.operate.property;

public class ZeebeProperties {

  private String brokerContactPoint = "localhost:26500";

  public String getBrokerContactPoint() {
    return brokerContactPoint;
  }

  public void setBrokerContactPoint(String brokerContactPoint) {
    this.brokerContactPoint = brokerContactPoint;
  }

}