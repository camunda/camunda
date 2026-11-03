/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data;

import io.camunda.configuration.data.auditlog.AuditLogEntry;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/** Configuration for audit logging, including separate settings for user and client operations. */
public class AuditLog {
  private boolean enabled = true;

  @NestedConfigurationProperty private AuditLogEntry user = AuditLogEntry.logAll();
  @NestedConfigurationProperty private AuditLogEntry client = AuditLogEntry.logNone();
  @NestedConfigurationProperty private AuditLogEntry unknown = AuditLogEntry.logNone();

  /**
   * PoC for the variable history write load: share of variable changes, 0 to 100, that are written
   * with their value to the variable history. 0 switches the variable history off.
   */
  private int variableHistoryPercent = 0;
  private boolean variableHistoryUser = true;
  private boolean variableHistoryClient = false;
  private boolean variableHistoryEngine = false;

  /**
   * PoC for the variable history write load: an update of a multi-instance output collection
   * records only the item it adds instead of the whole collection.
   */
  private boolean variableHistoryCollectionItems = false;

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

  public int getVariableHistoryPercent() {
    return variableHistoryPercent;
  }

  public void setVariableHistoryPercent(final int variableHistoryPercent) {
    this.variableHistoryPercent = variableHistoryPercent;
  }

  public boolean isVariableHistoryUser() {
    return variableHistoryUser;
  }

  public void setVariableHistoryUser(final boolean variableHistoryUser) {
    this.variableHistoryUser = variableHistoryUser;
  }

  public boolean isVariableHistoryClient() {
    return variableHistoryClient;
  }

  public void setVariableHistoryClient(final boolean variableHistoryClient) {
    this.variableHistoryClient = variableHistoryClient;
  }

  public boolean isVariableHistoryEngine() {
    return variableHistoryEngine;
  }

  public void setVariableHistoryEngine(final boolean variableHistoryEngine) {
    this.variableHistoryEngine = variableHistoryEngine;
  }

  public boolean isVariableHistoryCollectionItems() {
    return variableHistoryCollectionItems;
  }

  public void setVariableHistoryCollectionItems(final boolean variableHistoryCollectionItems) {
    this.variableHistoryCollectionItems = variableHistoryCollectionItems;
  }

  /** Converts this configuration to an {@link AuditLogConfiguration} for the exporter. */
  public AuditLogConfiguration toConfiguration() {
    final AuditLogConfiguration auditLogConfiguration = new AuditLogConfiguration();
    auditLogConfiguration.setEnabled(isEnabled());
    auditLogConfiguration.setVariableHistoryPercent(getVariableHistoryPercent());
    auditLogConfiguration.setVariableHistoryCollectionItems(isVariableHistoryCollectionItems());
    final var actorTypes = new java.util.HashSet<String>();
    if (variableHistoryUser) {
      actorTypes.add("USER");
    }
    if (variableHistoryClient) {
      actorTypes.add("CLIENT");
    }
    if (variableHistoryEngine) {
      actorTypes.add("ENGINE");
    }
    auditLogConfiguration.setVariableHistoryActorTypes(java.util.Set.copyOf(actorTypes));
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
