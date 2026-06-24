/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.property;

import java.util.Objects;

public class CloudProperties {

  private String organizationId;

  private String permissionUrl;
  private String permissionAudience;

  private String clusterId;

  private String consoleUrl;

  private String stage;

  private String mixpanelToken;

  private String mixpanelAPIHost;

  public String getOrganizationId() {
    return organizationId;
  }

  public CloudProperties setOrganizationId(final String organizationId) {
    this.organizationId = organizationId;
    return this;
  }

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

  public String getClusterId() {
    return clusterId;
  }

  public CloudProperties setClusterId(final String clusterId) {
    this.clusterId = clusterId;
    return this;
  }

  public String getConsoleUrl() {
    return consoleUrl;
  }

  public CloudProperties setConsoleUrl(final String consoleUrl) {
    this.consoleUrl = consoleUrl;
    return this;
  }

  public String getStage() {
    return stage;
  }

  public CloudProperties setStage(final String stage) {
    this.stage = stage;
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

  @Override
  public int hashCode() {
    return Objects.hash(
        organizationId,
        permissionUrl,
        permissionAudience,
        clusterId,
        consoleUrl,
        stage,
        mixpanelToken,
        mixpanelAPIHost);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CloudProperties that = (CloudProperties) o;
    return Objects.equals(organizationId, that.organizationId)
        && Objects.equals(permissionUrl, that.permissionUrl)
        && Objects.equals(permissionAudience, that.permissionAudience)
        && Objects.equals(clusterId, that.clusterId)
        && Objects.equals(consoleUrl, that.consoleUrl)
        && Objects.equals(stage, that.stage)
        && Objects.equals(mixpanelToken, that.mixpanelToken)
        && Objects.equals(mixpanelAPIHost, that.mixpanelAPIHost);
  }
}
