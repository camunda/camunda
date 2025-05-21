/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

public class SecurityHeaderConfigurations {
  private ContentTypeOptionsConfig contentTypeOptionsConfig = new ContentTypeOptionsConfig();
  private XssConfig xssConfig = new XssConfig();
  private CacheControlConfig cacheConfig = new CacheControlConfig();
  private HstsConfig hstsConfig = new HstsConfig();
  private FrameOptionsConfig frameOptionsConfig = new FrameOptionsConfig();
  private ContentSecurityPolicyConfig contentSecurityPolicyConfig =
      new ContentSecurityPolicyConfig();
  private ReferrerPolicyConfig referrerPolicyConfig = new ReferrerPolicyConfig();
  private PermissionsPolicyConfig permissionsPolicyConfig = new PermissionsPolicyConfig();
  private CrossOriginOpenerPolicyConfig crossOriginOpenerPolicyConfig =
      new CrossOriginOpenerPolicyConfig();
  private CrossOriginEmbedderPolicyConfig crossOriginEmbedderPolicyConfig =
      new CrossOriginEmbedderPolicyConfig();
  private CrossOriginResourcePolicyConfig crossOriginResourcePolicyConfig =
      new CrossOriginResourcePolicyConfig();

  public ContentTypeOptionsConfig getContentTypeOptionsConfig() {
    return contentTypeOptionsConfig;
  }

  public void setContentTypeOptionsConfig(final ContentTypeOptionsConfig contentTypeOptionsConfig) {
    this.contentTypeOptionsConfig = contentTypeOptionsConfig;
  }

  public XssConfig getXssConfig() {
    return xssConfig;
  }

  public void setXssConfig(final XssConfig xssConfig) {
    this.xssConfig = xssConfig;
  }

  public CacheControlConfig getCacheConfig() {
    return cacheConfig;
  }

  public void setCacheConfig(final CacheControlConfig cacheConfig) {
    this.cacheConfig = cacheConfig;
  }

  public HstsConfig getHstsConfig() {
    return hstsConfig;
  }

  public void setHstsConfig(final HstsConfig hstsConfig) {
    this.hstsConfig = hstsConfig;
  }

  public FrameOptionsConfig getFrameOptionsConfig() {
    return frameOptionsConfig;
  }

  public void setFrameOptionsConfig(final FrameOptionsConfig frameOptionsConfig) {
    this.frameOptionsConfig = frameOptionsConfig;
  }

  public ContentSecurityPolicyConfig getContentSecurityPolicyConfig() {
    return contentSecurityPolicyConfig;
  }

  public void setContentSecurityPolicyConfig(
      final ContentSecurityPolicyConfig contentSecurityPolicyConfig) {
    this.contentSecurityPolicyConfig = contentSecurityPolicyConfig;
  }

  public ReferrerPolicyConfig getReferrerPolicyConfig() {
    return referrerPolicyConfig;
  }

  public void setReferrerPolicyConfig(final ReferrerPolicyConfig referrerPolicyConfig) {
    this.referrerPolicyConfig = referrerPolicyConfig;
  }

  public PermissionsPolicyConfig getPermissionsPolicyConfig() {
    return permissionsPolicyConfig;
  }

  public void setPermissionsPolicyConfig(final PermissionsPolicyConfig permissionsPolicyConfig) {
    this.permissionsPolicyConfig = permissionsPolicyConfig;
  }

  public CrossOriginOpenerPolicyConfig getCrossOriginOpenerPolicyConfig() {
    return crossOriginOpenerPolicyConfig;
  }

  public void setCrossOriginOpenerPolicyConfig(
      final CrossOriginOpenerPolicyConfig crossOriginOpenerPolicyConfig) {
    this.crossOriginOpenerPolicyConfig = crossOriginOpenerPolicyConfig;
  }

  public CrossOriginEmbedderPolicyConfig getCrossOriginEmbedderPolicyConfig() {
    return crossOriginEmbedderPolicyConfig;
  }

  public void setCrossOriginEmbedderPolicyConfig(
      final CrossOriginEmbedderPolicyConfig crossOriginEmbedderPolicyConfig) {
    this.crossOriginEmbedderPolicyConfig = crossOriginEmbedderPolicyConfig;
  }

  public CrossOriginResourcePolicyConfig getCrossOriginResourcePolicyConfig() {
    return crossOriginResourcePolicyConfig;
  }

  public void setCrossOriginResourcePolicyConfig(
      final CrossOriginResourcePolicyConfig crossOriginResourcePolicyConfig) {
    this.crossOriginResourcePolicyConfig = crossOriginResourcePolicyConfig;
  }
}
