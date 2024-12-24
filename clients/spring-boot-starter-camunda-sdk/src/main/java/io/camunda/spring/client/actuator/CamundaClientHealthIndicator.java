/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.actuator;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Topology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

public class CamundaClientHealthIndicator extends AbstractHealthIndicator {

  private final CamundaClient client;

  @Autowired
  public CamundaClientHealthIndicator(final CamundaClient client) {
    this.client = client;
  }

  @Override
  protected void doHealthCheck(final Health.Builder builder) {
    final Topology topology = client.newTopologyRequest().send().join();
    if (topology.getBrokers().isEmpty()) {
      builder.down();
    } else {
      builder.up();
    }
  }
}
