/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.property;

public class CloudProperties {

  // Cloud related properties for mixpanel events
  private String organizationId;

  private String clusterId;

  private String mixpanelToken;

  private String mixpanelAPIHost;

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
