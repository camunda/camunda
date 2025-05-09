/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

public final class SaasConfiguration {

  private String organizationId;
  private String clusterId;

  public boolean isConfigured() {
    if (organizationId != null && clusterId != null) {
      return true;
    }
    if (organizationId == null && clusterId == null) {
      return false;
    }
    throw new IllegalStateException("Must configure both organizationId and clusterId");
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(final String organizationId) {
    this.organizationId = organizationId;
  }

  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }
}
