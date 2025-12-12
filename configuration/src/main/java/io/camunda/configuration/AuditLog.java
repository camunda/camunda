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

public class AuditLog {
  private boolean enabled = true;

  @NestedConfigurationProperty private AuditLogEntry user = new AuditLogEntry();
  @NestedConfigurationProperty private AuditLogEntry client = new AuditLogEntry();

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
    return auditLogConfiguration;
  }
}
