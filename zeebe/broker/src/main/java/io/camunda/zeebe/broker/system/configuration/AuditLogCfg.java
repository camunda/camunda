/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import java.util.Set;

public class AuditLogCfg implements ConfigurationEntry {
  private boolean isEnabled = true;

  private final Entry user = new Entry();
  private final Entry client = new Entry();

  public AuditLogCfg() {}

  public boolean isEnabled() {
    return isEnabled;
  }

  public void setEnabled(final boolean enabled) {
    isEnabled = enabled;
  }

  public Entry getUser() {
    return user;
  }

  public Entry getClient() {
    return client;
  }

  public class Entry {
    private Set<AuditLogOperationCategory> categories = Set.of();
    private Set<AuditLogEntityType> excludes = Set.of();

    public Set<AuditLogOperationCategory> getCategories() {
      return categories;
    }

    public void setCategories(final Set<AuditLogOperationCategory> categories) {
      this.categories = categories;
    }

    public Set<AuditLogEntityType> getExcludes() {
      return excludes;
    }

    public void setExcludes(final Set<AuditLogEntityType> excludes) {
      this.excludes = excludes;
    }
  }
}
