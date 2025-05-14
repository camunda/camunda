/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

import io.camunda.security.configuration.secureheaders.values.CrossOriginResourcePolicy;

/**
 * Sets the 'Cross-Origin-Resource-Policy' header. Can take the following values:
 *
 * <ul>
 *   <li>SAME_SITE
 *   <li>SAME_ORIGIN
 *   <li>CROSS_ORIGIN
 * </ul>
 *
 * By default, the value is 'SAME_ORIGIN'.
 */
public class CrossOriginResourcePolicyConfig {
  private CrossOriginResourcePolicy crossOriginResourcePolicy =
      CrossOriginResourcePolicy.SAME_ORIGIN;

  public CrossOriginResourcePolicy getCrossOriginResourcePolicy() {
    return crossOriginResourcePolicy;
  }

  public void setCrossOriginResourcePolicy(
      final CrossOriginResourcePolicy crossOriginResourcePolicy) {
    this.crossOriginResourcePolicy = crossOriginResourcePolicy;
  }
}
