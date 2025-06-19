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
 * <p>Default: UNSAFE_NONE - Allows the document to load cross-origin resources without giving
 * explicit permission through the CORS protocol or the Cross-Origin-Resource-Policy header.
 *
 * @see <a
 *     href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cross-Origin-Embedder-Policy">MDN:
 *     Cross-Origin-Embedder-Policy</a>
 */
public class CrossOriginEmbedderPolicyConfig {

  /**
   * The COEP policy value.
   *
   * <p>Default: UNSAFE_NONE - Allows the document to load cross-origin resources without giving
   * explicit permission through the CORS protocol or the Cross-Origin-Resource-Policy header.
   */
  private CrossOriginEmbedderPolicy value = CrossOriginEmbedderPolicy.UNSAFE_NONE;

  public CrossOriginEmbedderPolicy getValue() {
    return value;
  }

  public void setValue(final CrossOriginEmbedderPolicy value) {
    this.value = value;
  }
}
