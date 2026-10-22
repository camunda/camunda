/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.broker.system.configuration.FailureDetectorType;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/** Experimental, opt-in detection of regions gone from the cluster by the system partition. */
public class FailureDetector {

  /** Which failure detector the system partition runs: none, or swim. */
  private FailureDetectorType type = FailureDetectorType.NONE;

  /** Configuration of the SWIM failure detector. */
  @NestedConfigurationProperty private Swim swim = new Swim();

  public FailureDetectorType getType() {
    return type;
  }

  public void setType(final FailureDetectorType type) {
    this.type = type;
  }

  public Swim getSwim() {
    return swim;
  }

  public void setSwim(final Swim swim) {
    this.swim = swim;
  }

  @Override
  public String toString() {
    return "FailureDetector{" + "type=" + type + ", swim=" + swim + '}';
  }
}
