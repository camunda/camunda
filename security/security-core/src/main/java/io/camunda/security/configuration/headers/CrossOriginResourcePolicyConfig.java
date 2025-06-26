/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.headers;

import io.camunda.security.configuration.headers.values.CrossOriginResourcePolicy;

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
 * <p>Default: SAME_SITE - Only requests from the same Site can read the resource.
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
   * <p>Default: SAME_SITE - Only requests from the same Site can read the resource.
   *
   * <p>Alternative values: - SAME_ORIGIN: Only requests from the same origin (i.e., scheme + host +
   * port) can read the resource.
   */
  private CrossOriginResourcePolicy value = CrossOriginResourcePolicy.SAME_SITE;

  public CrossOriginResourcePolicy getValue() {
    return value;
  }

  public void setValue(final CrossOriginResourcePolicy value) {
    this.value = value;
  }
}
