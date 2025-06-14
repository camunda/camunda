/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

/**
 * Configures cache control headers to prevent sensitive content from being cached.
 *
 * <p>The Cache-Control header directives control caching in browsers and shared caches (e.g.,
 * proxies, CDNs). When enabled (default state), this configuration sets headers that completely
 * disable caching: - Cache-Control: no-cache, no-store, max-age=0, must-revalidate - Pragma:
 * no-cache (for HTTP/1.0 backward compatibility) - Expires: 0 (indicates expired content)
 *
 * <p>This is a critical security measure for applications handling sensitive data to prevent: -
 * Cached credentials or session data being accessible after logout - Sensitive information
 * persisting in browser/proxy caches - Shared computer scenarios where subsequent users could
 * access cached data
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control">MDN:
 *     Cache-Control</a>
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Pragma">MDN: Pragma</a>
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Expires">MDN: Expires</a>
 */
public class CacheControlConfig {

  /**
   * Controls whether cache prevention headers are sent. Default: true (enabled) - This ensures data
   * is not cached by default, following the security principle of "secure by default".
   */
  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isDisabled() {
    return !enabled;
  }
}
