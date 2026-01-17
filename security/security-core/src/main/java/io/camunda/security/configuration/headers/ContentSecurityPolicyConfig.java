/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.headers;

import java.util.List;

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
          + "script-src 'self' https: osano.com *.osano.com *.appcues.com *.chargebee.com *.mixpanel.com ajax.cloudflare.com static.cloudflareinsights.com; "
          + "script-src-elem 'self' cdn.jsdelivr.net *.mixpanel.com osano.com *.osano.com *.appcues.com appcues.com cloudflareinsights.com; "
          + "connect-src 'self' https: cdn.jsdelivr.net *.appcues.net wss://api.appcues.net *.osano.com *.mixpanel.com; "
          + "style-src 'self' 'unsafe-inline' https: cdn.jsdelivr.net *.appcues.com *.osano.com *.mixpanel.com *.googleapis.com *.chargebee.com; "
          + "img-src * data: 'self'; "
          + "form-action 'self'; "
          + "frame-ancestors 'self'; "
          + "frame-src 'self' https: *.osano.com *.mixpanel.com *.chargebee.com blob:; "
          + "object-src 'self' blob:; "
          + "font-src 'self' data: fonts.camunda.io cdn.jsdelivr.net fonts.gstatic.com; "
          + "worker-src 'self' *.osano.com *.mixpanel.com blob:; "
          + "child-src; "
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

  public static String getDefaultSmSecurityPolicy(final List<String> allowedConnectUrls) {
    final String defaultSmSecurityPolicy =
        "default-src 'self'; "
            + "base-uri 'self'; "
            + "script-src 'self' https: *.chargebee.com *.mixpanel.com ajax.cloudflare.com static.cloudflareinsights.com; "
            + "script-src-elem 'self' cdn.jsdelivr.net ; "
            + "connect-src 'self' https: *.mixpanel.com cloudflareinsights.com *.appcues.net wss://api.appcues.net cdn.jsdelivr.net "
            + String.join(" ", allowedConnectUrls)
            + "; "
            + "style-src 'self' https: 'unsafe-inline' cdn.jsdelivr.net *.googleapis.com *.chargebee.com; "
            + "img-src data: 'self'; "
            + "form-action 'self'; "
            + "frame-ancestors 'self'; "
            + "frame-src 'self' https: *.chargebee.com blob: ; "
            + "object-src 'self' blob:; "
            + "font-src 'self' data: fonts.camunda.io cdn.jsdelivr.net; "
            + "worker-src 'self' blob:; "
            + "child-src; "
            + "script-src-attr 'none'";

    return defaultSmSecurityPolicy;
  }

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
