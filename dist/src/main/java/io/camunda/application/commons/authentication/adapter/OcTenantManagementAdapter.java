/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication.adapter;

import io.camunda.auth.domain.model.AuthTenant;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.port.inbound.TenantManagementPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.TenantEntity;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.TenantServices.TenantRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletionException;

/**
 * Bridges the auth library's {@link TenantManagementPort} to the monorepo's {@link TenantServices}.
 */
public class OcTenantManagementAdapter implements TenantManagementPort {

  private final TenantServices tenantServices;
  private final CamundaAuthenticationProvider authProvider;

  public OcTenantManagementAdapter(
      final TenantServices tenantServices, final CamundaAuthenticationProvider authProvider) {
    this.tenantServices = tenantServices;
    this.authProvider = authProvider;
  }

  @Override
  public AuthTenant getById(final String tenantId) {
    final var entity = tenantServices.withAuthentication(auth()).getById(tenantId);
    return toAuthTenant(entity);
  }

  @Override
  public AuthTenant create(final String tenantId, final String name, final String description) {
    try {
      tenantServices
          .withAuthentication(auth())
          .createTenant(new TenantRequest(null, tenantId, name, description))
          .join();
      return new AuthTenant(0L, tenantId, name, description);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public AuthTenant update(final String tenantId, final String name, final String description) {
    try {
      tenantServices
          .withAuthentication(auth())
          .updateTenant(new TenantRequest(null, tenantId, name, description))
          .join();
      return new AuthTenant(0L, tenantId, name, description);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void delete(final String tenantId) {
    try {
      tenantServices.withAuthentication(auth()).deleteTenant(tenantId).join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void addMember(final String tenantId, final String memberId, final MemberType memberType) {
    try {
      tenantServices
          .withAuthentication(auth())
          .addMember(new TenantMemberRequest(tenantId, memberId, toEntityType(memberType)))
          .join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void removeMember(
      final String tenantId, final String memberId, final MemberType memberType) {
    try {
      tenantServices
          .withAuthentication(auth())
          .removeMember(new TenantMemberRequest(tenantId, memberId, toEntityType(memberType)))
          .join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  private CamundaAuthentication auth() {
    return authProvider.getCamundaAuthentication();
  }

  private static AuthTenant toAuthTenant(final TenantEntity entity) {
    return new AuthTenant(
        entity.key() != null ? entity.key() : 0L,
        entity.tenantId(),
        entity.name(),
        entity.description());
  }

  private static EntityType toEntityType(final MemberType type) {
    return switch (type) {
      case USER -> EntityType.USER;
      case CLIENT -> EntityType.CLIENT;
      case GROUP -> EntityType.GROUP;
      case ROLE -> EntityType.ROLE;
      case MAPPING_RULE -> EntityType.MAPPING_RULE;
    };
  }

  private static RuntimeException mapException(final Throwable cause) {
    if (cause instanceof RuntimeException re) {
      return re;
    }
    return new RuntimeException(cause);
  }
}
