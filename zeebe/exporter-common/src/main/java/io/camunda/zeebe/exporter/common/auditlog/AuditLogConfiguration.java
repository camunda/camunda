/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import java.util.Set;

public final class AuditLogConfiguration implements AuditLogCheck {

  private boolean enabled = true;

  private ActorAuditLogConfiguration user = new ActorAuditLogConfiguration();
  private ActorAuditLogConfiguration client = new ActorAuditLogConfiguration();

  public ActorAuditLogConfiguration getUser() {
    return user;
  }

  public AuditLogConfiguration setUser(final ActorAuditLogConfiguration user) {
    this.user = user;
    return this;
  }

  public ActorAuditLogConfiguration getClient() {
    return client;
  }

  public AuditLogConfiguration setClient(final ActorAuditLogConfiguration client) {
    this.client = client;
    return this;
  }

  @Override
  public String toString() {
    return "AuditLogConfiguration{"
        + "enabled="
        + enabled
        + ", user="
        + user
        + ", client="
        + client
        + '}';
  }

  public boolean isEnabled() {
    return enabled
        && !(getUser().getCategories().isEmpty() && getClient().getCategories().isEmpty());
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public boolean isEnabled(final AuditLogInfo auditLog) {
    final AuditLogCheck check =
        switch (auditLog.actor().actorType()) {
          case USER -> getUser();
          case CLIENT -> getClient();
          case ANONYMOUS -> AuditLogCheck.DISABLED;
          case UNKNOWN -> {
            // TODO: enable logging after ensuring all expected events are correctly captured
            //            LOG.warn("{} has unknown actor.", auditLog);

            yield AuditLogCheck.ENABLED;
          }
        };

    return isEnabled() && check.isEnabled(auditLog);
  }

  public static final class ActorAuditLogConfiguration implements AuditLogCheck {

    private Set<AuditLogOperationCategory> categories =
        Set.of(
            AuditLogOperationCategory.ADMIN,
            AuditLogOperationCategory.DEPLOYED_RESOURCES,
            AuditLogOperationCategory.USER_TASKS);
    private Set<AuditLogEntityType> excludes = Set.of();

    @Override
    public boolean isEnabled(final AuditLogInfo auditLog) {
      return getCategories().contains(auditLog.category())
          && !getExcludes().contains(auditLog.entityType());
    }

    public Set<AuditLogEntityType> getExcludes() {
      return excludes;
    }

    public ActorAuditLogConfiguration setExcludes(final Set<AuditLogEntityType> excludes) {
      this.excludes = excludes;
      return this;
    }

    public Set<AuditLogOperationCategory> getCategories() {
      return categories;
    }

    public ActorAuditLogConfiguration setCategories(
        final Set<AuditLogOperationCategory> categories) {
      this.categories = categories;
      return this;
    }

    @Override
    public String toString() {
      return "ActorAuditLogConfiguration{"
          + "categories="
          + categories
          + ", excludes="
          + excludes
          + '}';
    }
  }
}
