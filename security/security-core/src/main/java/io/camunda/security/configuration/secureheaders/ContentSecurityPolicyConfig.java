/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

/**
 * Configures Content Security Policy (CSP) to prevent XSS and other content injection attacks.
 *
 * <p>CSP allows fine-grained control over which resources can be loaded, providing defense-in-depth
 * against Cross-Site Scripting (XSS), clickjacking, and other code injection attacks. When enabled
 * (default state), CSP instructs browsers to only load resources from approved sources.
 *
 * <p>Default policies taken from Operate/Tasklist prior to 8.7 release, aggregated together.
 *
 * @see <a
 *     href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy">MDN:
 *     Content-Security-Policy</a>
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP">MDN: Content Security Policy
 *     Guide</a>
 */
public class ContentSecurityPolicyConfig {

  public static final String DEFAULT_SAAS_SECURITY_POLICY =
      "default-src 'self'; "
          + "base-uri 'self'; "
          + "script-src 'self'; "
          + "script-src-elem 'self' cdn.jsdelivr.net .mixpanel.com .osano.com .appcues.com; "
          + "connect-src 'self' cdn.jsdelivr.net .appcues.net wss://api.appcues.net .osano.com .mixpanel.com; "
          + "style-src 'self' 'unsafe-inline' cdn.jsdelivr.net .appcues.com .osano.com .mixpanel.com; "
          + "img-src  data:; "
          + "block-all-mixed-content; "
          + "form-action 'self'; "
          + "frame-ancestors 'none'; "
          + "frame-src 'self' https: .osano.com .mixpanel.com; "
          + "object-src 'none'; "
          + "font-src 'self' fonts.camunda.io cdn.jsdelivr.net; "
          + "worker-src 'self' .osano.com .mixpanel.com blob:; "
          + "sandbox allow-forms allow-scripts allow-same-origin allow-popups; "
          + "connect-src 'self' https: .mixpanel.com cloudflareinsights.com .appcues.net wss://api.appcues.net; "
          + "script-src 'self' https: .chargebee.com .mixpanel.com ajax.cloudflare.com static.cloudflareinsights.com; "
          + "style-src 'self' https: 'unsafe-inline' .googleapis.com .chargebee.com; "
          + "font-src 'self' data: https://fonts.gstatic.com https://fonts.camunda.io; "
          + "frame-ancestors 'self'; "
          + "frame-src 'self' https: .chargebee.com blob:; "
          + "child-src; "
          + "worker-src 'self' blob:; "
          + "object-src 'self' blob:; "
          + "script-src-attr 'none'";

  public static final String DEFAULT_SM_SECURITY_POLICY =
      "default-src 'self'; "
          + "base-uri 'self'; "
          + "script-src 'self'; "
          + "script-src-elem 'self' cdn.jsdelivr.net; "
          + "connect-src 'self' cdn.jsdelivr.net; "
          + "style-src 'self' 'unsafe-inline' cdn.jsdelivr.net; "
          + "img-src * data:; "
          + "block-all-mixed-content; form-action 'self'; "
          + "frame-ancestors 'none'; "
          + "frame-src 'self' https:; "
          + "object-src 'none'; "
          + "font-src 'self' fonts.camunda.io cdn.jsdelivr.net; "
          + "worker-src 'self' blob:; "
          + "sandbox allow-forms allow-scripts allow-same-origin allow-popups; "
          + "connect-src 'self' https: *.mixpanel.com cloudflareinsights.com *.appcues.net wss://api.appcues.net; "
          + "script-src 'self' https: *.chargebee.com *.mixpanel.com ajax.cloudflare.com static.cloudflareinsights.com; "
          + "style-src 'self' https: 'unsafe-inline' *.googleapis.com *.chargebee.com; "
          + "font-src 'self' data: https://fonts.gstatic.com https://fonts.camunda.io; "
          + "frame-ancestors 'self'; "
          + "frame-src 'self' https: *.chargebee.com blob:; "
          + "child-src; object-src 'self' blob:; "
          + "script-src-attr 'none'";

  /**
   * Controls whether CSP headers are sent.
   *
   * <p>Default: true (enabled) - CSP is a critical security control and should remain enabled
   * unless there are specific compatibility issues that need to be resolved.
   */
  private boolean enabled = true;

  /**
   * Custom policy directives.
   *
   * <p>When not defined, appropriate defaults (SAAS or SM) are used based on deployment type.
   * Custom policies should be carefully tested as overly restrictive policies can break
   * functionality, while permissive policies reduce security.
   *
   * <p>Example: "default-src 'self'; script-src 'self' 'unsafe-inline';"
   */
  private String policyDirectives;

  /**
   * Controls whether to use report-only mode.
   *
   * <p>Default: false - When true, CSP violations are reported but not enforced. This is useful for
   * testing new policies before enforcement to identify potential compatibility issues without
   * breaking functionality.
   *
   * <p>In report-only mode, the Content-Security-Policy-Report-Only header is used instead of
   * Content-Security-Policy.
   */
  private boolean reportOnly = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isDisabled() {
    return !enabled;
  }

  public String getPolicyDirectives() {
    return policyDirectives;
  }

  public void setPolicyDirectives(final String policyDirectives) {
    this.policyDirectives = policyDirectives;
  }

  public boolean isReportOnly() {
    return reportOnly;
  }

  public void setReportOnly(final boolean reportOnly) {
    this.reportOnly = reportOnly;
  }
}
