/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication.adapter;

import io.camunda.auth.domain.model.AuthGroup;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.port.inbound.GroupManagementPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.GroupEntity;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletionException;

/**
 * Bridges the auth library's {@link GroupManagementPort} to the monorepo's {@link GroupServices}.
 */
public class OcGroupManagementAdapter implements GroupManagementPort {

  private final GroupServices groupServices;
  private final CamundaAuthenticationProvider authProvider;

  public OcGroupManagementAdapter(
      final GroupServices groupServices, final CamundaAuthenticationProvider authProvider) {
    this.groupServices = groupServices;
    this.authProvider = authProvider;
  }

  @Override
  public AuthGroup getById(final String groupId) {
    final var entity = groupServices.withAuthentication(auth()).getGroup(groupId);
    return toAuthGroup(entity);
  }

  @Override
  public AuthGroup create(final String groupId, final String name, final String description) {
    try {
      groupServices
          .withAuthentication(auth())
          .createGroup(new GroupDTO(groupId, name, description))
          .join();
      return new AuthGroup(0L, groupId, name, description);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public AuthGroup update(final String groupId, final String name, final String description) {
    try {
      groupServices.withAuthentication(auth()).updateGroup(groupId, name, description).join();
      return new AuthGroup(0L, groupId, name, description);
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void delete(final String groupId) {
    try {
      groupServices.withAuthentication(auth()).deleteGroup(groupId).join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void addMember(final String groupId, final String memberId, final MemberType memberType) {
    try {
      groupServices
          .withAuthentication(auth())
          .assignMember(new GroupMemberDTO(groupId, memberId, toEntityType(memberType)))
          .join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void removeMember(
      final String groupId, final String memberId, final MemberType memberType) {
    try {
      groupServices
          .withAuthentication(auth())
          .removeMember(new GroupMemberDTO(groupId, memberId, toEntityType(memberType)))
          .join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  private CamundaAuthentication auth() {
    return authProvider.getCamundaAuthentication();
  }

  private static AuthGroup toAuthGroup(final GroupEntity entity) {
    return new AuthGroup(
        entity.groupKey() != null ? entity.groupKey() : 0L,
        entity.groupId(),
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
