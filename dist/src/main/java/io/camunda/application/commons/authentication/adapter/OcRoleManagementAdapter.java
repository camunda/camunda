/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication.adapter;

import io.camunda.auth.domain.model.AuthRole;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.port.inbound.RoleManagementPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.RoleEntity;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.RoleServices.UpdateRoleRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletionException;

/** Bridges the auth library's {@link RoleManagementPort} to the monorepo's {@link RoleServices}. */
public class OcRoleManagementAdapter implements RoleManagementPort {

  private final RoleServices roleServices;
  private final CamundaAuthenticationProvider authProvider;

  public OcRoleManagementAdapter(
      final RoleServices roleServices, final CamundaAuthenticationProvider authProvider) {
    this.roleServices = roleServices;
    this.authProvider = authProvider;
  }

  @Override
  public AuthRole getById(final String roleId) {
    final var entity = roleServices.withAuthentication(auth()).getRole(roleId);
    return toAuthRole(entity);
  }

  @Override
  public AuthRole create(final String roleId, final String name, final String description) {
    try {
      roleServices
          .withAuthentication(auth())
          .createRole(new CreateRoleRequest(roleId, name, description))
          .join();
      return new AuthRole(0L, roleId, name, description);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public AuthRole update(final String roleId, final String name, final String description) {
    try {
      roleServices
          .withAuthentication(auth())
          .updateRole(new UpdateRoleRequest(roleId, name, description))
          .join();
      return new AuthRole(0L, roleId, name, description);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void delete(final String roleId) {
    try {
      roleServices.withAuthentication(auth()).deleteRole(roleId).join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void addMember(final String roleId, final String memberId, final MemberType memberType) {
    try {
      roleServices
          .withAuthentication(auth())
          .addMember(new RoleMemberRequest(roleId, memberId, toEntityType(memberType)))
          .join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void removeMember(
      final String roleId, final String memberId, final MemberType memberType) {
    try {
      roleServices
          .withAuthentication(auth())
          .removeMember(new RoleMemberRequest(roleId, memberId, toEntityType(memberType)))
          .join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  private CamundaAuthentication auth() {
    return authProvider.getCamundaAuthentication();
  }

  private static AuthRole toAuthRole(final RoleEntity entity) {
    return new AuthRole(
        entity.roleKey() != null ? entity.roleKey() : 0L,
        entity.roleId(),
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
