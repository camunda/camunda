/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth.matcher;

import io.camunda.search.entities.AuditLogEntity;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.zeebe.protocol.record.value.AuthorizedAuditLogCategoryType;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches audit log properties against authentication context.
 *
 * <p>Supports matching:
 *
 * <ul>
 *   <li>category - audit log is type of authenticated categories
 * </ul>
 */
public class AuditLogPropertyMatcher implements ResourcePropertyMatcher<AuditLogEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(AuditLogPropertyMatcher.class);

  @Override
  public boolean matches(
      final AuditLogEntity resource,
      final Set<String> authorizedPropertyNames,
      final CamundaAuthentication authentication) {

    for (final var propertyName : authorizedPropertyNames) {
      if (matchesProperty(resource, propertyName)) {
        return true;
      }
    }

    return false;
  }

  private boolean matchesProperty(final AuditLogEntity resource, final String propertyName) {
    if (Authorization.PROP_CATEGORY.equals(propertyName)) {
      return matchesCategory(resource);
    } else {
      LOG.warn(
          "Unknown property name '{}' for {} matching; ignoring.",
          propertyName,
          AuditLogEntity.class.getSimpleName());
      return false;
    }
  }

  private boolean matchesCategory(final AuditLogEntity resource) {
    final var category = resource.category();
    if (category == null) {
      return false;
    }

    return AuthorizedAuditLogCategoryType.getAuthorizedCategories().contains(category.name());
  }

  @Override
  public Class<AuditLogEntity> getResourceClass() {
    return AuditLogEntity.class;
  }
}
