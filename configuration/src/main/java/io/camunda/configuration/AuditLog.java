/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.broker.system.configuration.AuditLogCfg;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class AuditLog {
  private boolean isEnabled = false;

  @NestedConfigurationProperty private AuditLogEntry user = new AuditLogEntry();
  @NestedConfigurationProperty private AuditLogEntry client = new AuditLogEntry();

  public boolean isEnabled() {
    return isEnabled;
  }

  public void setEnabled(final boolean enabled) {
    isEnabled = enabled;
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

  public AuditLogCfg toCfg() {
    final AuditLogCfg auditLogCfg = new AuditLogCfg();
    auditLogCfg.setEnabled(isEnabled());
    auditLogCfg.getClient().setCategories(getClient().getCategories());
    auditLogCfg.getClient().setExcludes(getClient().getExcludes());
    auditLogCfg.getUser().setCategories(getUser().getCategories());
    auditLogCfg.getUser().setExcludes(getUser().getExcludes());
    return auditLogCfg;
  }
}
