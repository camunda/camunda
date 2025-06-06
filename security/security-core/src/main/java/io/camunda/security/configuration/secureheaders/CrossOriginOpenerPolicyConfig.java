/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

import io.camunda.security.configuration.secureheaders.values.CrossOriginOpenerPolicy;

/**
 * Sets the 'Cross-Origin-Opener-Policy' header. Can take the following values:
 *
 * <ul>
 *   <li>UNSAFE_NONE
 *   <li>SAME_ORIGIN_ALLOW_POPUPS
 *   <li>SAME_ORIGIN
 * </ul>
 *
 * By default, the header value is 'SAME_ORIGIN_ALLOW_POPUPS'.
 */
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
