/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

/**
 * Configures HTTP Strict Transport Security (HSTS) to enforce HTTPS connections.
 *
 * <p>HSTS instructs browsers to only connect to the server using HTTPS, preventing protocol
 * downgrade attacks and cookie hijacking. Once a browser receives this header, it will
 * automatically convert all HTTP requests to HTTPS for the specified duration, even if the user
 * types http:// or follows an HTTP link.
 *
 * <p>When enabled (default state), the header value includes: - max-age=31536000 (1 year): How long
 * browsers remember to force HTTPS - includeSubDomains: Applies HSTS to all subdomains - preload:
 * Indicates consent for inclusion in browser preload lists
 *
 * <p>This prevents several attack vectors: - Man-in-the-middle attacks on initial HTTP connection -
 * SSL stripping attacks - Accidental exposure of session cookies over HTTP - Mixed content issues
 *
 * @see <a
 *     href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Strict-Transport-Security">MDN:
 *     Strict-Transport-Security</a>
 * @see <a
 *     href="https://cheatsheetseries.owasp.org/cheatsheets/HTTP_Strict_Transport_Security_Cheat_Sheet.html">OWASP:
 *     HSTS Cheat Sheet</a>
 */
public class HstsConfig {

  /**
   * Default max-age of 1 year (in seconds).
   *
   * <p>Two years is recommended by hstspreload.org for inclusion in browser preload lists. This
   * provides long-term protection while allowing for eventual policy changes if needed.
   *
   * <p>This value was in Camunda pre 8.8 release.
   */
  private static final long DEFAULT_MAX_AGE_IN_SECONDS = 60 * 60 * 24 * 365;

  /**
   * Controls whether the Strict-Transport-Security header is sent.
   *
   * <p>Default: true (enabled) - HSTS should be enabled for all HTTPS sites. Note: Per RFC 6797,
   * the header is only sent over HTTPS connections, never over HTTP, to prevent header injection
   * attacks.
   */
  private boolean enabled = true;

  /**
   * The max-age directive in seconds.
   *
   * <p>Default: 31536000 (1 year)
   */
  private long maxAgeInSeconds = DEFAULT_MAX_AGE_IN_SECONDS;

  /**
   * Whether to include the includeSubDomains directive.
   *
   * <p>Default: false - This applies HSTS to all subdomains, preventing attacks on vulnerable
   * subdomains from compromising security. Only disable if you have subdomains that cannot support
   * HTTPS.
   *
   * <p>That was default for Camunda 7.
   *
   * <p>WARNING: This affects ALL subdomains, including internal ones. Ensure all subdomains support
   * HTTPS before enabling.
   */
  private boolean includeSubDomains = false;

  /**
   * Whether to include the preload directive.
   *
   * <p>Default: false - Indicates eligibility for inclusion in browser HSTS preload lists.
   *
   * <p>That was default for Camunda 7.
   *
   * <p>WARNING: Preload list inclusion is practically permanent. Removal can take months to
   * propagate to all browsers. Only enable if you're certain about long-term HTTPS support.
   */
  private boolean preload = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isDisabled() {
    return !enabled;
  }

  public long getMaxAgeInSeconds() {
    return maxAgeInSeconds;
  }

  public void setMaxAgeInSeconds(final long maxAgeInSeconds) {
    this.maxAgeInSeconds = maxAgeInSeconds;
  }

  public boolean isIncludeSubDomains() {
    return includeSubDomains;
  }

  public void setIncludeSubDomains(final boolean includeSubDomains) {
    this.includeSubDomains = includeSubDomains;
  }

  public boolean isPreload() {
    return preload;
  }

  public void setPreload(final boolean preload) {
    this.preload = preload;
  }
}
