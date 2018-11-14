package org.camunda.operate.property;

public class ZeebeProperties {

  private String brokerContactPoint = "localhost:26500";

  private String worker = "operate";

  public String getBrokerContactPoint() {
    return brokerContactPoint;
  }

  public void setBrokerContactPoint(String brokerContactPoint) {
    this.brokerContactPoint = brokerContactPoint;
  }

  public String getWorker() {
    return worker;
  }

  public void setWorker(String worker) {
    this.worker = worker;
  }
}