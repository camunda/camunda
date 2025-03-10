/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.config.operate;

public class CloudProperties {

  // Cloud related properties for mixpanel events
  private String organizationId;

  private String clusterId;

  private String mixpanelToken;

  private String mixpanelAPIHost;

  private String permissionUrl;

  private String permissionAudience;
  private String consoleUrl;

  public String getPermissionUrl() {
    return permissionUrl;
  }

  public void setPermissionUrl(final String permissionUrl) {
    this.permissionUrl = permissionUrl;
  }

  public String getPermissionAudience() {
    return permissionAudience;
  }

  public void setPermissionAudience(final String permissionAudience) {
    this.permissionAudience = permissionAudience;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public CloudProperties setOrganizationId(final String organizationId) {
    this.organizationId = organizationId;
    return this;
  }

  public String getClusterId() {
    return clusterId;
  }

  public CloudProperties setClusterId(final String clusterId) {
    this.clusterId = clusterId;
    return this;
  }

  public String getMixpanelToken() {
    return mixpanelToken;
  }

  public CloudProperties setMixpanelToken(final String mixpanelToken) {
    this.mixpanelToken = mixpanelToken;
    return this;
  }

  public String getMixpanelAPIHost() {
    return mixpanelAPIHost;
  }

  public CloudProperties setMixpanelAPIHost(final String mixpanelAPIHost) {
    this.mixpanelAPIHost = mixpanelAPIHost;
    return this;
  }

  public String getConsoleUrl() {
    return consoleUrl;
  }

  public CloudProperties setConsoleUrl(final String consoleUrl) {
    this.consoleUrl = consoleUrl;
    return this;
  }
}
