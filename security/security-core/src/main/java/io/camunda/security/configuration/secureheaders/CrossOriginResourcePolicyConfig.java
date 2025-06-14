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
 * Configures Cross-Origin-Resource-Policy (CORP) header for resource isolation.
 *
 * <p>CORP lets websites declare that certain resources should not be loaded by other origins. This
 * protects against: - Spectre-like side-channel attacks - Cross-site script inclusion attacks -
 * Unauthorized resource embedding
 *
 * <p>The header works by instructing browsers to block no-cors cross-origin requests to the
 * resource. It complements CORB (Cross-Origin Read Blocking) which browsers implement by default.
 *
 * <p>Default: SAME_ORIGIN - Only allows the resource to be loaded by the same origin, providing
 * strong protection against cross-origin attacks while maintaining functionality for same-origin
 * use cases.
 *
 * @see <a
 *     href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Cross-Origin_Resource_Policy">MDN:
 *     Cross-Origin Resource Policy</a>
 * @see <a
 *     href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cross-Origin-Resource-Policy">MDN:
 *     Cross-Origin-Resource-Policy header</a>
 */
public class CrossOriginResourcePolicyConfig {

  /**
   * The CORP policy value.
   *
   * <p>Default: SAME_ORIGIN - This default prevents cross-origin loading while allowing same-origin
   * use. This is the recommended setting for most resources unless they are specifically intended
   * to be embedded cross-origin.
   *
   * <p>Alternative values: - SAME_SITE: Allows loading from the same site (more permissive) -
   * CROSS_ORIGIN: Allows loading from any origin (least secure)
   */
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
