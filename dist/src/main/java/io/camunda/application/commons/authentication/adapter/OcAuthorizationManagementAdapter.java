/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication.adapter;

import io.camunda.auth.domain.model.AuthorizationRecord;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.port.inbound.AuthorizationManagementPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.AuthorizationServices.UpdateAuthorizationRequest;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * Bridges the auth library's {@link AuthorizationManagementPort} to the monorepo's {@link
 * AuthorizationServices}. Permission types are mapped between the auth library's string-based
 * representation and the monorepo's {@link PermissionType} enum.
 */
public class OcAuthorizationManagementAdapter implements AuthorizationManagementPort {

  private final AuthorizationServices authorizationServices;
  private final CamundaAuthenticationProvider authProvider;

  public OcAuthorizationManagementAdapter(
      final AuthorizationServices authorizationServices,
      final CamundaAuthenticationProvider authProvider) {
    this.authorizationServices = authorizationServices;
    this.authProvider = authProvider;
  }

  @Override
  public List<AuthorizationRecord> findByOwner(final String ownerId, final String ownerType) {
    final var ocQuery =
        AuthorizationQuery.of(
            q -> {
              q.filter(f -> f.ownerIds(ownerId).ownerType(ownerType));
              q.page(p -> p.from(0).size(1000));
              return q;
            });

    final var result = authorizationServices.withAuthentication(auth()).search(ocQuery);
    return result.items().stream()
        .map(OcAuthorizationManagementAdapter::toAuthorizationRecord)
        .toList();
  }

  @Override
  public AuthorizationRecord getByKey(final long authorizationKey) {
    final var entity =
        authorizationServices.withAuthentication(auth()).getAuthorization(authorizationKey);
    return toAuthorizationRecord(entity);
  }

  @Override
  public AuthorizationRecord create(final AuthorizationRecord record) {
    try {
      authorizationServices
          .withAuthentication(auth())
          .createAuthorization(
              new CreateAuthorizationRequest(
                  record.ownerId(),
                  AuthorizationOwnerType.valueOf(record.ownerType()),
                  AuthorizationResourceMatcher.ID,
                  record.resourceId(),
                  null,
                  AuthorizationResourceType.valueOf(record.resourceType()),
                  toPermissionTypes(record.permissionTypes())))
          .join();
      return new AuthorizationRecord(
          0L,
          record.ownerId(),
          record.ownerType(),
          record.resourceType(),
          record.resourceId(),
          record.permissionTypes());
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public AuthorizationRecord update(final long authorizationKey, final AuthorizationRecord record) {
    try {
      authorizationServices
          .withAuthentication(auth())
          .updateAuthorization(
              new UpdateAuthorizationRequest(
                  authorizationKey,
                  record.ownerId(),
                  AuthorizationOwnerType.valueOf(record.ownerType()),
                  AuthorizationResourceMatcher.ID,
                  record.resourceId(),
                  null,
                  AuthorizationResourceType.valueOf(record.resourceType()),
                  toPermissionTypes(record.permissionTypes())))
          .join();
      return new AuthorizationRecord(
          authorizationKey,
          record.ownerId(),
          record.ownerType(),
          record.resourceType(),
          record.resourceId(),
          record.permissionTypes());
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  @Override
  public void delete(final long authorizationKey) {
    try {
      authorizationServices.withAuthentication(auth()).deleteAuthorization(authorizationKey).join();
    } catch (final CompletionException e) {
      throw mapException(e.getCause());
    }
  }

  private CamundaAuthentication auth() {
    return authProvider.getCamundaAuthentication();
  }

  private static AuthorizationRecord toAuthorizationRecord(final AuthorizationEntity entity) {
    final Set<String> permissions =
        entity.permissionTypes() != null
            ? entity.permissionTypes().stream()
                .map(PermissionType::name)
                .collect(Collectors.toSet())
            : Set.of();

    return new AuthorizationRecord(
        entity.authorizationKey() != null ? entity.authorizationKey() : 0L,
        entity.ownerId(),
        entity.ownerType(),
        entity.resourceType(),
        entity.resourceId(),
        permissions);
  }

  private static Set<PermissionType> toPermissionTypes(final Set<String> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return Set.of();
    }
    return permissions.stream().map(PermissionType::valueOf).collect(Collectors.toSet());
  }

  private static RuntimeException mapException(final Throwable cause) {
    if (cause instanceof RuntimeException re) {
      return re;
    }
    return new RuntimeException(cause);
  }
}
