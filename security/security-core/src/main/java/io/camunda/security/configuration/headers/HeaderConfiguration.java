/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.headers;

public class HeaderConfiguration {
  private ContentTypeOptionsConfig contentTypeOptions = new ContentTypeOptionsConfig();
  private CacheControlConfig cacheControl = new CacheControlConfig();
  private HstsConfig hsts = new HstsConfig();
  private FrameOptionsConfig frameOptions = new FrameOptionsConfig();
  private ContentSecurityPolicyConfig contentSecurityPolicy = new ContentSecurityPolicyConfig();
  private ReferrerPolicyConfig referrerPolicy = new ReferrerPolicyConfig();
  private PermissionsPolicyConfig permissionsPolicy = new PermissionsPolicyConfig();
  private CrossOriginOpenerPolicyConfig crossOriginOpenerPolicy =
      new CrossOriginOpenerPolicyConfig();
  private CrossOriginEmbedderPolicyConfig crossOriginEmbedderPolicy =
      new CrossOriginEmbedderPolicyConfig();
  private CrossOriginResourcePolicyConfig crossOriginResourcePolicy =
      new CrossOriginResourcePolicyConfig();

  public ContentTypeOptionsConfig getContentTypeOptions() {
    return contentTypeOptions;
  }

  public void setContentTypeOptions(final ContentTypeOptionsConfig contentTypeOptions) {
    this.contentTypeOptions = contentTypeOptions;
  }

  public CacheControlConfig getCacheControl() {
    return cacheControl;
  }

  public void setCacheControl(final CacheControlConfig cacheControl) {
    this.cacheControl = cacheControl;
  }

  public HstsConfig getHsts() {
    return hsts;
  }

  public void setHsts(final HstsConfig hsts) {
    this.hsts = hsts;
  }

  public FrameOptionsConfig getFrameOptions() {
    return frameOptions;
  }

  public void setFrameOptions(final FrameOptionsConfig frameOptions) {
    this.frameOptions = frameOptions;
  }

  public ContentSecurityPolicyConfig getContentSecurityPolicy() {
    return contentSecurityPolicy;
  }

  public void setContentSecurityPolicy(final ContentSecurityPolicyConfig contentSecurityPolicy) {
    this.contentSecurityPolicy = contentSecurityPolicy;
  }

  public ReferrerPolicyConfig getReferrerPolicy() {
    return referrerPolicy;
  }

  public void setReferrerPolicy(final ReferrerPolicyConfig referrerPolicy) {
    this.referrerPolicy = referrerPolicy;
  }

  public PermissionsPolicyConfig getPermissionsPolicy() {
    return permissionsPolicy;
  }

  public void setPermissionsPolicy(final PermissionsPolicyConfig permissionsPolicy) {
    this.permissionsPolicy = permissionsPolicy;
  }

  public CrossOriginOpenerPolicyConfig getCrossOriginOpenerPolicy() {
    return crossOriginOpenerPolicy;
  }

  public void setCrossOriginOpenerPolicy(
      final CrossOriginOpenerPolicyConfig crossOriginOpenerPolicy) {
    this.crossOriginOpenerPolicy = crossOriginOpenerPolicy;
  }

  public CrossOriginEmbedderPolicyConfig getCrossOriginEmbedderPolicy() {
    return crossOriginEmbedderPolicy;
  }

  public void setCrossOriginEmbedderPolicy(
      final CrossOriginEmbedderPolicyConfig crossOriginEmbedderPolicy) {
    this.crossOriginEmbedderPolicy = crossOriginEmbedderPolicy;
  }

  public CrossOriginResourcePolicyConfig getCrossOriginResourcePolicy() {
    return crossOriginResourcePolicy;
  }

  public void setCrossOriginResourcePolicy(
      final CrossOriginResourcePolicyConfig crossOriginResourcePolicy) {
    this.crossOriginResourcePolicy = crossOriginResourcePolicy;
  }
}
