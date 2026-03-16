/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.config;

/**
 * Security header configuration properties matching {@code camunda.security.http-headers.*}.
 * Provides fine-grained control over HTTP security response headers including content-type options,
 * HSTS, frame options, CSP, referrer policy, and cross-origin policies.
 */
public class HttpHeadersProperties {

  public static final String DEFAULT_CSP_POLICY =
      "default-src 'self'; "
          + "base-uri 'self'; "
          + "script-src 'self' https:; "
          + "script-src-elem 'self' cdn.jsdelivr.net; "
          + "connect-src 'self' https:; "
          + "style-src 'self' https: 'unsafe-inline' cdn.jsdelivr.net; "
          + "img-src data: 'self'; "
          + "form-action 'self'; "
          + "frame-ancestors 'self'; "
          + "frame-src 'self' https: blob:; "
          + "object-src 'self' blob:; "
          + "font-src 'self' data: fonts.camunda.io cdn.jsdelivr.net; "
          + "worker-src 'self' blob:; "
          + "child-src; "
          + "script-src-attr 'none'";

  private ContentTypeOptions contentTypeOptions = new ContentTypeOptions();
  private CacheControl cacheControl = new CacheControl();
  private Hsts hsts = new Hsts();
  private FrameOptions frameOptions = new FrameOptions();
  private ContentSecurityPolicy contentSecurityPolicy = new ContentSecurityPolicy();
  private String referrerPolicy = "STRICT_ORIGIN_WHEN_CROSS_ORIGIN";
  private String permissionsPolicy;
  private String crossOriginOpenerPolicy = "SAME_ORIGIN_ALLOW_POPUPS";
  private String crossOriginEmbedderPolicy = "UNSAFE_NONE";
  private String crossOriginResourcePolicy = "SAME_SITE";

  public ContentTypeOptions getContentTypeOptions() {
    return contentTypeOptions;
  }

  public void setContentTypeOptions(final ContentTypeOptions contentTypeOptions) {
    this.contentTypeOptions = contentTypeOptions;
  }

  public CacheControl getCacheControl() {
    return cacheControl;
  }

  public void setCacheControl(final CacheControl cacheControl) {
    this.cacheControl = cacheControl;
  }

  public Hsts getHsts() {
    return hsts;
  }

  public void setHsts(final Hsts hsts) {
    this.hsts = hsts;
  }

  public FrameOptions getFrameOptions() {
    return frameOptions;
  }

  public void setFrameOptions(final FrameOptions frameOptions) {
    this.frameOptions = frameOptions;
  }

  public ContentSecurityPolicy getContentSecurityPolicy() {
    return contentSecurityPolicy;
  }

  public void setContentSecurityPolicy(final ContentSecurityPolicy contentSecurityPolicy) {
    this.contentSecurityPolicy = contentSecurityPolicy;
  }

  public String getReferrerPolicy() {
    return referrerPolicy;
  }

  public void setReferrerPolicy(final String referrerPolicy) {
    this.referrerPolicy = referrerPolicy;
  }

  public String getPermissionsPolicy() {
    return permissionsPolicy;
  }

  public void setPermissionsPolicy(final String permissionsPolicy) {
    this.permissionsPolicy = permissionsPolicy;
  }

  public String getCrossOriginOpenerPolicy() {
    return crossOriginOpenerPolicy;
  }

  public void setCrossOriginOpenerPolicy(final String crossOriginOpenerPolicy) {
    this.crossOriginOpenerPolicy = crossOriginOpenerPolicy;
  }

  public String getCrossOriginEmbedderPolicy() {
    return crossOriginEmbedderPolicy;
  }

  public void setCrossOriginEmbedderPolicy(final String crossOriginEmbedderPolicy) {
    this.crossOriginEmbedderPolicy = crossOriginEmbedderPolicy;
  }

  public String getCrossOriginResourcePolicy() {
    return crossOriginResourcePolicy;
  }

  public void setCrossOriginResourcePolicy(final String crossOriginResourcePolicy) {
    this.crossOriginResourcePolicy = crossOriginResourcePolicy;
  }

  /** Controls X-Content-Type-Options: nosniff header. */
  public static class ContentTypeOptions {
    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }
  }

  /** Controls Cache-Control no-store/no-cache headers. */
  public static class CacheControl {
    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }
  }

  /** HTTP Strict Transport Security (HSTS) header configuration. */
  public static class Hsts {
    private boolean enabled = true;
    private long maxAgeInSeconds = 31536000;
    private boolean includeSubDomains = false;
    private boolean preload = false;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
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

  /** X-Frame-Options header configuration. Values: DENY, SAMEORIGIN. */
  public static class FrameOptions {
    private boolean enabled = true;
    private String mode = "SAMEORIGIN";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    public String getMode() {
      return mode;
    }

    public void setMode(final String mode) {
      this.mode = mode;
    }
  }

  /** Content-Security-Policy header configuration. */
  public static class ContentSecurityPolicy {
    private boolean enabled = true;
    private String policyDirectives;
    private boolean reportOnly = false;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
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
}
