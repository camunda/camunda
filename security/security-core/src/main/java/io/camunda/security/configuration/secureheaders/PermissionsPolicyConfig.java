/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

/**
 * Configures Permissions-Policy header to control browser feature access.
 *
 * <p>Permissions Policy (formerly Feature Policy) allows fine-grained control over which browser
 * features and APIs can be used in a document or iframe. This helps: - Improve privacy by disabling
 * unnecessary sensors/APIs - Enhance security by limiting attack surface - Enforce best practices
 * by preventing use of legacy APIs - Improve performance by disabling unused features
 *
 * <p>The policy is not set by default, allowing applications to define policies based on their
 * specific needs. Common uses include: - Disabling geolocation, camera, microphone for privacy -
 * Preventing payment APIs on non-payment pages - Disabling legacy features like document-write -
 * Restricting autoplay to improve user experience
 *
 * <p>Example policies: - "geolocation=(), camera=(), microphone=()" - Disable sensors -
 * "payment=(self)" - Only allow payments on same origin - "accelerometer=() gyroscope=()
 * magnetometer=()" - Disable motion sensors
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Permissions-Policy">MDN:
 *     Permissions-Policy</a>
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Permissions_Policy">MDN:
 *     Permissions Policy Guide</a>
 * @see <a href="https://www.permissionspolicy.com/">Permissions Policy Generator</a>
 */
public class PermissionsPolicyConfig {

  /**
   * The Permissions Policy directives as a string.
   *
   * <p>Default: null (no policy set) - This allows all features by default, letting applications
   * selectively restrict features based on their needs.
   *
   * <p>Format: "directive1=allowlist directive2=allowlist ..."
   *
   * <p>Allowlist values: - * : Allow for all origins - () : Deny for all origins (empty allowlist)
   * - (self) : Allow for same origin only - (self "https://example.com") : Allow for self and
   * specific origins
   *
   * <p>Common directives: - camera, microphone, geolocation: Privacy-sensitive sensors - payment,
   * usb, serial: Sensitive APIs - autoplay, fullscreen: User experience features - accelerometer,
   * gyroscope: Motion sensors
   *
   * <p>Example: "camera=() microphone=() geolocation=(self) payment=(self)"
   */
  private String policy;

  public String getPolicy() {
    return policy;
  }

  public void setPolicy(final String policy) {
    this.policy = policy;
  }
}
