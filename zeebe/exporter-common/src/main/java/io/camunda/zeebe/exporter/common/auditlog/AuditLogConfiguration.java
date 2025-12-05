/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog;

import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import java.util.Set;

public final class AuditLogConfiguration {

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
    return "AuditLogConfiguration{" + "user=" + user + ", client=" + client + '}';
  }

  public boolean isEnabled() {
    return !(getUser().getCategories().isEmpty() && getClient().getCategories().isEmpty());
  }

  public boolean isEnabled(final AuditLogInfo auditLog) {
    final var config =
        switch (auditLog.actor().actorType()) {
          case USER -> getUser();
          case CLIENT -> getClient();
        };

    return config.getCategories().contains(auditLog.category())
        && !config.getExcludes().contains(auditLog.entityType());
  }

  public static final class ActorAuditLogConfiguration {
    private Set<AuditLogOperationCategory> categories =
        Set.of(
            AuditLogOperationCategory.OPERATOR,
            AuditLogOperationCategory.USER_TASK,
            AuditLogOperationCategory.ADMIN);
    private Set<AuditLogEntityType> excludes = Set.of(AuditLogEntityType.VARIABLE);

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
