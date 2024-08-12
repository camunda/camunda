/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import io.camunda.zeebe.gateway.cmd.IllegalTenantRequestException;
import io.camunda.zeebe.gateway.cmd.InvalidTenantRequestException;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.micrometer.common.util.StringUtils;
import java.util.List;
import java.util.regex.Pattern;

public final class MultiTenancyValidator {
  private static final Pattern TENANT_ID_MASK = Pattern.compile("^[\\w\\.-]{1,31}$");

  public static String ensureTenantIdSet(
      final String commandName, final String tenantId, final boolean multiTenancyEnabled) {
    final var hasTenantId = !StringUtils.isBlank(tenantId);
    if (!multiTenancyEnabled) {
      if (hasTenantId && !TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)) {
        throw new InvalidTenantRequestException(commandName, tenantId, "multi-tenancy is disabled");
      }

      return TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    }

    if (!hasTenantId) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "no tenant identifier was provided.");
    }

    if (tenantId.length() > 31) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "tenant identifier is longer than 31 characters");
    }

    if (!TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)
        && !TENANT_ID_MASK.matcher(tenantId).matches()) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "tenant identifier contains illegal characters");
    }

    final List<String> authorizedTenants;
    try {
      authorizedTenants = RequestMapper.getAuthentication().authenticatedTenantIds();
    } catch (final Exception e) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "tenant could not be retrieved from the request context", e);
    }
    if (authorizedTenants == null) {
      throw new InvalidTenantRequestException(
          commandName, tenantId, "tenant could not be retrieved from the request context");
    }
    if (!authorizedTenants.contains(tenantId)) {
      throw new IllegalTenantRequestException(
          commandName, tenantId, "tenant is not authorized to perform this request");
    }

    return tenantId;
  }
}
