/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.health;

import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

@Component
public class NodeIdProviderHealthIndicator implements HealthIndicator {

  private final NodeIdProvider nodeIdProvider;

  @Autowired
  public NodeIdProviderHealthIndicator(final NodeIdProvider nodeIdProvider) {
    this.nodeIdProvider = nodeIdProvider;
  }

  @Override
  public Health health() {
    final var status = nodeIdProvider.isValid().join() ? Status.UP : Status.DOWN;
    return Health.status(status).build();
  }
}
