/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.headers;

import io.camunda.security.configuration.headers.values.CrossOriginOpenerPolicy;

/**
 * Configures Cross-Origin-Opener-Policy (COOP) header for window isolation.
 *
 * <p>COOP allows a website to control whether a new top-level document opened via window.open() or
 * navigation is opened in the same browsing context group (BCG). This provides process isolation
 * between windows, preventing: - Cross-origin access to window.opener - Side-channel attacks like
 * Spectre - Unintended information leakage between windows
 *
 * <p>Default: SAME_ORIGIN_ALLOW_POPUPS - Balances security with compatibility by: - Isolating the
 * window from cross-origin openers - Still allowing popups to be opened (common use case) -
 * Preventing direct DOM access between cross-origin windows
 *
 * <p>Works with COEP to enable "cross-origin isolation" for advanced features.
 *
 * @see <a
 *     href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cross-Origin-Opener-Policy">MDN:
 *     Cross-Origin-Opener-Policy</a>
 */
public class CrossOriginOpenerPolicyConfig {

  /**
   * The COOP policy value.
   *
   * <p>Default: SAME_ORIGIN_ALLOW_POPUPS - This default provides security while maintaining
   * compatibility with common patterns like OAuth popups and social media sharing. It prevents
   * cross-origin windows from accessing each other while still allowing popups to function.
   *
   * <p>Alternative values: - UNSAFE_NONE: No isolation (not recommended) - SAME_ORIGIN: Strictest
   * isolation, may break popup functionality
   */
  private CrossOriginOpenerPolicy value = CrossOriginOpenerPolicy.SAME_ORIGIN_ALLOW_POPUPS;

  public CrossOriginOpenerPolicy getValue() {
    return value;
  }

  public void setValue(final CrossOriginOpenerPolicy value) {
    this.value = value;
  }
}
