/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix = IdentityMigrationProperties.CONFIG_PREFIX_CAMUNDA_MIGRATION_IDENTITY)
public final class IdentityMigrationProperties {
  public static final String CONFIG_PREFIX_CAMUNDA_MIGRATION_IDENTITY =
      "camunda.migration.identity";
  public static final String PROP_CAMUNDA_MIGRATION_IDENTITY_MODE =
      CONFIG_PREFIX_CAMUNDA_MIGRATION_IDENTITY + ".mode";
  private ManagementIdentityProperties managementIdentity;
  private String organizationId;
  private Mode mode = Mode.CLOUD;

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

  public Mode getMode() {
    return mode;
  }

  public void setMode(final Mode mode) {
    this.mode = mode;
  }

  public enum Mode {
    CLOUD,
    KEYCLOAK,
    OIDC
  }
}
