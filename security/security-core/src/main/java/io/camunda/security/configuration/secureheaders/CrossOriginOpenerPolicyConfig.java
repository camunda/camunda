/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

import io.camunda.security.configuration.secureheaders.values.CrossOriginOpenerPolicy;

public class CrossOriginOpenerPolicyConfig {
  private CrossOriginOpenerPolicy crossOriginOpenerPolicy =
      CrossOriginOpenerPolicy.SAME_ORIGIN_ALLOW_POPUPS;

  public CrossOriginOpenerPolicy getCrossOriginOpenerPolicy() {
    return crossOriginOpenerPolicy;
  }

  public void setCrossOriginOpenerPolicy(final CrossOriginOpenerPolicy crossOriginOpenerPolicy) {
    this.crossOriginOpenerPolicy = crossOriginOpenerPolicy;
  }
}
