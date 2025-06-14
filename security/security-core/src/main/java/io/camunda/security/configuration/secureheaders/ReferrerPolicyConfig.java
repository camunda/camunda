/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

import io.camunda.security.configuration.secureheaders.values.ReferrerPolicy;

/**
 * Configures Referrer-Policy header to control referrer information leakage.
 *
 * <p>The Referrer-Policy header controls how much information about the origin page is included in
 * the Referer header when navigating to other pages or loading resources. This helps prevent
 * sensitive information leakage through URLs.
 *
 * <p>Default: STRICT_ORIGIN_WHEN_CROSS_ORIGIN - This balanced default: - Sends full URL (minus
 * auth/fragment) for same-origin requests - Sends only origin (protocol + domain) for cross-origin
 * HTTPS→HTTPS - Sends nothing for HTTPS→HTTP (preventing secure data leakage)
 *
 * <p>This prevents several privacy/security issues: - Leaking sensitive URL parameters (tokens,
 * session IDs, personal data) - Exposing internal URL structures to third parties - Revealing user
 * navigation patterns to external sites - Search query exposure in referrer URLs
 *
 * <p>Common scenarios requiring stricter policies: - Password reset pages with tokens in URLs -
 * Admin panels with sensitive paths - Search results with private queries - Authenticated areas
 * with user-specific URLs
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Referrer-Policy">MDN:
 *     Referrer-Policy</a>
 * @see <a
 *     href="https://developer.mozilla.org/en-US/docs/Web/Security/Referer_header:_privacy_and_security_concerns">MDN:
 *     Referer header privacy concerns</a>
 */
public class ReferrerPolicyConfig {

  /**
   * The referrer policy value.
   *
   * <p>Default: STRICT_ORIGIN_WHEN_CROSS_ORIGIN - Recommended by Mozilla as the best balance
   * between functionality and privacy. It provides: - Full referrer for same-origin navigation
   * (preserves analytics) - Origin-only for secure cross-origin requests (privacy) - No referrer
   * for downgrade scenarios (security)
   */
  private ReferrerPolicy referrerPolicy = ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN;

  public ReferrerPolicy getReferrerPolicy() {
    return referrerPolicy;
  }

  public void setReferrerPolicy(final ReferrerPolicy referrerPolicy) {
    this.referrerPolicy = referrerPolicy;
  }
}
