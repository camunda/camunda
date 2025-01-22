/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.newwork;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/** This class contains all project configuration parameters. */
@Component
@Configuration
@ConfigurationProperties(OptimizeProperties.PREFIX)
@PropertySource("classpath:optimize-version.properties")
public class OptimizeProperties {

  public static final String PREFIX = "camunda.optimize";

  private static final String UNKNOWN_VERSION = "unknown-version";

  private boolean webappEnabled = true;

  private final boolean persistentSessionsEnabled = false;

  /** Indicates, whether CSRF prevention is enabled. */
  private boolean csrfPreventionEnabled = true;

  /** Standard user data */
  private String userId = "demo";

  private String displayName = "demo";

  private String password = "demo";

  private List<String> roles = List.of("OWNER");

  @Value("${camunda.operate.internal.version.current}")
  private String version = UNKNOWN_VERSION;

  @NestedConfigurationProperty
  private WebSecurityProperties webSecurity = new WebSecurityProperties();

  public boolean isWebappEnabled() {
    return webappEnabled;
  }

  public void setWebappEnabled(final boolean webappEnabled) {
    this.webappEnabled = webappEnabled;
  }

  public boolean isCsrfPreventionEnabled() {
    return csrfPreventionEnabled;
  }

  public void setCsrfPreventionEnabled(final boolean csrfPreventionEnabled) {
    this.csrfPreventionEnabled = csrfPreventionEnabled;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(final String displayName) {
    this.displayName = displayName;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public WebSecurityProperties getWebSecurity() {
    return webSecurity;
  }

  public OptimizeProperties setWebSecurity(final WebSecurityProperties webSecurity) {
    this.webSecurity = webSecurity;
    return this;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(final List<String> roles) {
    this.roles = roles;
  }
}
