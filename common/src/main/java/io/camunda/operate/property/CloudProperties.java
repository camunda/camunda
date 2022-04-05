/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.property;

public class CloudProperties {

  // Cloud related properties for mixpanel events
  private String organizationId;

  private String clusterId;

  private String mixpanelToken;

  private String mixpanelAPIHost;

  private String permissionUrl;

  private String permissionAudience;

  public String getPermissionUrl() {
    return permissionUrl;
  }

  public void setPermissionUrl(String permissionUrl) {
    this.permissionUrl = permissionUrl;
  }

  public String getPermissionAudience() {
    return permissionAudience;
  }

  public void setPermissionAudience(String permissionAudience) {
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
}
