/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.health;

import io.camunda.zeebe.broker.clustering.mapper.NodeIdMapper;
import org.springframework.boot.actuate.availability.LivenessStateHealthIndicator;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.LivenessState;
import org.springframework.stereotype.Component;

@Component
public class BrokerLivenessHealthIndicator extends LivenessStateHealthIndicator {

  private final NodeIdMapper nodeIdMapper;

  public BrokerLivenessHealthIndicator(
      final ApplicationAvailability availability, final NodeIdMapper nodeIdMapper) {
    super(availability);
    this.nodeIdMapper = nodeIdMapper;
  }

  @Override
  protected AvailabilityState getState(final ApplicationAvailability applicationAvailability) {
    if (nodeIdMapper.isHealthy()) {
      return LivenessState.CORRECT;
    } else {
      return LivenessState.BROKEN;
    }
  }
}
