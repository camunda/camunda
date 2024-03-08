package io.camunda.zeebe.spring.client.actuator;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.Topology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

public class ZeebeClientHealthIndicator extends AbstractHealthIndicator {

  private final ZeebeClient client;

  @Autowired
  public ZeebeClientHealthIndicator(ZeebeClient client) {
    this.client = client;
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    Topology topology = client.newTopologyRequest().send().join();
    if (topology.getBrokers().isEmpty()) {
      builder.down();
    } else {
      builder.up();
    }
  }
}
