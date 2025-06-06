/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

import io.camunda.security.configuration.secureheaders.values.CrossOriginEmbedderPolicy;

/**
 * Set the 'Cross-Origin-Embedder-Policy' header. Can take the following values:
 *
 * <ul>
 *   <li>UNSAFE_NONE
 *   <li>REQUIRE_CORP
 * </ul>
 *
 * By default, the header value is 'REQUIRE_CORP'.
 */
public class CrossOriginEmbedderPolicyConfig {
  private CrossOriginEmbedderPolicy crossOriginEmbedderPolicy =
      CrossOriginEmbedderPolicy.REQUIRE_CORP;

  public CrossOriginEmbedderPolicy getCrossOriginEmbedderPolicy() {
    return crossOriginEmbedderPolicy;
  }

  public void setCrossOriginEmbedderPolicy(
      final CrossOriginEmbedderPolicy crossOriginEmbedderPolicy) {
    this.crossOriginEmbedderPolicy = crossOriginEmbedderPolicy;
  }
}
