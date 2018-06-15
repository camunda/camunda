package org.camunda.operate.property;

import java.util.Arrays;
import java.util.List;

/**
 * @author Svetlana Dorokhova.
 */
public class ZeebeProperties {

  private String brokerContactPoint = "localhost:51015";

  private List<String> topics = Arrays.asList("default-topic");

  private String worker;

  public String getBrokerContactPoint() {
    return brokerContactPoint;
  }

  public void setBrokerContactPoint(String brokerContactPoint) {
    this.brokerContactPoint = brokerContactPoint;
  }

  public List<String> getTopics() {
    return topics;
  }

  public void setTopics(List<String> topics) {
    this.topics = topics;
  }

  public String getWorker() {
    return worker;
  }

  public void setWorker(String worker) {
    this.worker = worker;
  }
}