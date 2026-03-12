/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/** Configuration for audit logging, including separate settings for user and client operations. */
public class AuditLog {
  private boolean enabled = true;

  @NestedConfigurationProperty private AuditLogEntry user = AuditLogEntry.logAll();
  @NestedConfigurationProperty private AuditLogEntry client = AuditLogEntry.logNone();
  @NestedConfigurationProperty private AuditLogEntry unknown = AuditLogEntry.logAll();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public AuditLogEntry getClient() {
    return client;
  }

  public void setClient(final AuditLogEntry client) {
    this.client = client;
  }

  public AuditLogEntry getUser() {
    return user;
  }

  public void setUser(final AuditLogEntry user) {
    this.user = user;
  }

  public AuditLogEntry getUnknown() {
    return unknown;
  }

  public void setUnknown(final AuditLogEntry unknown) {
    this.unknown = unknown;
  }

  /** Converts this configuration to an {@link AuditLogConfiguration} for the exporter. */
  public AuditLogConfiguration toConfiguration() {
    final AuditLogConfiguration auditLogConfiguration = new AuditLogConfiguration();
    auditLogConfiguration.setEnabled(isEnabled());
    auditLogConfiguration
        .getClient()
        .setCategories(getClient().getCategories())
        .setExcludes(getClient().getExcludes());
    auditLogConfiguration
        .getUser()
        .setCategories(getUser().getCategories())
        .setExcludes(getUser().getExcludes());
    auditLogConfiguration
        .getUnknown()
        .setCategories(getUnknown().getCategories())
        .setExcludes(getUnknown().getExcludes());
    return auditLogConfiguration;
  }
}
