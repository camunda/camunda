/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.headers;

import io.camunda.security.configuration.headers.values.CrossOriginEmbedderPolicy;

/**
 * Configures Cross-Origin-Embedder-Policy (COEP) header for cross-origin isolation.
 *
 * <p>COEP controls whether the document can load cross-origin resources that don't explicitly grant
 * permission. When set to 'require-corp' (default), it requires all cross-origin resources to
 * either: - Include a Cross-Origin-Resource-Policy (CORP) header allowing the load - Be requested
 * using CORS
 *
 * <p>This header works in conjunction with Cross-Origin-Opener-Policy (COOP).
 *
 * <p>Default: REQUIRE_CORP - Requiring explicit opt-in for all cross-origin resources. This may
 * require coordination with third-party services to add appropriate CORP headers.
 *
 * @see <a
 *     href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cross-Origin-Embedder-Policy">MDN:
 *     Cross-Origin-Embedder-Policy</a>
 */
public class CrossOriginEmbedderPolicyConfig {

  /**
   * The COEP policy value.
   *
   * <p>Default: REQUIRE_CORP - This is the most secure setting, requiring all cross-origin
   * resources to explicitly allow embedding. While this may require additional configuration for
   * third-party resources, it provides important security benefits and enables advanced browser
   * features.
   *
   * <p>UNSAFE_NONE would allow unrestricted cross-origin resource loading but is not recommended as
   * it disables important security protections.
   */
  private CrossOriginEmbedderPolicy value = CrossOriginEmbedderPolicy.REQUIRE_CORP;

  public CrossOriginEmbedderPolicy getValue() {
    return value;
  }

  public void setValue(final CrossOriginEmbedderPolicy value) {
    this.value = value;
  }
}
