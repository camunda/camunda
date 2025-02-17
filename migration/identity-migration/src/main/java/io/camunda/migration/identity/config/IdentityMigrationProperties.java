/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camunda.migration.identity")
public class IdentityMigrationProperties {
  private ManagementIdentityProperties managementIdentity;
  private String organizationId;

  public ManagementIdentityProperties getManagementIdentity() {
    return managementIdentity;
  }

  public void setManagementIdentity(final ManagementIdentityProperties managementIdentity) {
    this.managementIdentity = managementIdentity;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(final String organizationId) {
    this.organizationId = organizationId;
  }
}
