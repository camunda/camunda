/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.mapper;

import io.camunda.security.api.model.authz.AuthorizationOwnerType;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.authz.PermissionType;
import java.util.Set;
import java.util.stream.Collectors;

public final class AuthzModelMapper {

  private AuthzModelMapper() {}

  public static io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType toProtocol(
      final AuthorizationOwnerType value) {
    return io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType.valueOf(value.name());
  }

  public static io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher toProtocol(
      final AuthorizationResourceMatcher value) {
    return io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher.valueOf(
        value.name());
  }

  public static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType toProtocol(
      final AuthorizationResourceType value) {
    return io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.valueOf(value.name());
  }

  public static io.camunda.zeebe.protocol.record.value.EntityType toProtocol(
      final EntityType value) {
    return io.camunda.zeebe.protocol.record.value.EntityType.valueOf(value.name());
  }

  public static io.camunda.zeebe.protocol.record.value.PermissionType toProtocol(
      final PermissionType value) {
    return io.camunda.zeebe.protocol.record.value.PermissionType.valueOf(value.name());
  }

  public static Set<io.camunda.zeebe.protocol.record.value.PermissionType>
      toProtocolPermissionTypes(final Set<PermissionType> values) {
    return values.stream().map(AuthzModelMapper::toProtocol).collect(Collectors.toSet());
  }

  public static io.camunda.zeebe.protocol.record.value.AuthorizationScope toProtocol(
      final AuthorizationScope value) {
    return new io.camunda.zeebe.protocol.record.value.AuthorizationScope(
        toProtocol(value.getMatcher()), value.getResourceId(), value.getResourcePropertyName());
  }

  public static AuthorizationOwnerType fromProtocol(
      final io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType value) {
    return AuthorizationOwnerType.valueOf(value.name());
  }

  public static AuthorizationResourceMatcher fromProtocol(
      final io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher value) {
    return AuthorizationResourceMatcher.valueOf(value.name());
  }

  public static AuthorizationResourceType fromProtocol(
      final io.camunda.zeebe.protocol.record.value.AuthorizationResourceType value) {
    return AuthorizationResourceType.valueOf(value.name());
  }

  public static EntityType fromProtocol(
      final io.camunda.zeebe.protocol.record.value.EntityType value) {
    return EntityType.valueOf(value.name());
  }

  public static PermissionType fromProtocol(
      final io.camunda.zeebe.protocol.record.value.PermissionType value) {
    return PermissionType.valueOf(value.name());
  }

  public static Set<PermissionType> fromProtocolPermissionTypes(
      final Set<io.camunda.zeebe.protocol.record.value.PermissionType> values) {
    return values.stream().map(AuthzModelMapper::fromProtocol).collect(Collectors.toSet());
  }

  public static AuthorizationScope fromProtocol(
      final io.camunda.zeebe.protocol.record.value.AuthorizationScope value) {
    return new AuthorizationScope(
        fromProtocol(value.getMatcher()), value.getResourceId(), value.getResourcePropertyName());
  }
}
