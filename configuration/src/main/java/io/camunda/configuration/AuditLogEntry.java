/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import java.util.Set;

/**
 * Configuration for audit log filtering by operation categories and entity type exclusions. Used
 * for both user and client audit logging.
 */
public class AuditLogEntry {
  private Set<AuditLogOperationCategory> categories =
      Set.of(
          AuditLogOperationCategory.DEPLOYED_RESOURCES,
          AuditLogOperationCategory.USER_TASKS,
          AuditLogOperationCategory.ADMIN);
  private Set<AuditLogEntityType> excludes = Set.of(); // e.g. AuditLogEntityType.VARIABLE

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
