/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.state.authorization.PersistedAuthorization;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@NullMarked
public class PermissionsBehavior {

  public static final String PERMISSIONS_FOR_RESOURCE_IDENTIFIER_ALREADY_EXISTS_MESSAGE =
      "Expected to create authorization for owner '%s' for resource identifier '%s', but an authorization for this resource identifier already exists.";
  public static final String PERMISSIONS_FOR_RESOURCE_PROPERTY_NAME_ALREADY_EXISTS_MESSAGE =
      "Expected to create authorization for owner '%s' for resource property name '%s', but an authorization for this resource property name already exists.";
  public static final String AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_UPDATE =
      "Expected to update authorization with key %s, but an authorization with this key does not exist";
  public static final String AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_DELETION =
      "Expected to delete authorization with key %s, but an authorization with this key does not exist";

  private static final Logger LOG = Loggers.ENGINE_IDENTITY_LOGGER;

  private final AuthorizationState authorizationState;
  private final CslAuthorizationCheck cslCheck;
  private final @Nullable AuthorizationCheckPort authCheckPort;
  private final @Nullable LazyTokenClaimsConverter claimsConverter;
  private final @Nullable EngineSecurityConfig securityConfig;

  public PermissionsBehavior(
      final ProcessingState processingState, final CslAuthorizationCheck cslCheck) {
    this(processingState, cslCheck, null, null, null);
  }

  public PermissionsBehavior(
      final ProcessingState processingState,
      final AuthorizationCheckPort authCheckPort,
      final LazyTokenClaimsConverter claimsConverter,
      final EngineSecurityConfig securityConfig) {
    this(
        processingState,
        new CslAuthorizationCheck(authCheckPort, claimsConverter, securityConfig),
        authCheckPort,
        claimsConverter,
        securityConfig);
  }

  private PermissionsBehavior(
      final ProcessingState processingState,
      final CslAuthorizationCheck cslCheck,
      final @Nullable AuthorizationCheckPort authCheckPort,
      final @Nullable LazyTokenClaimsConverter claimsConverter,
      final @Nullable EngineSecurityConfig securityConfig) {
    authorizationState = processingState.getAuthorizationState();
    this.cslCheck = cslCheck;
    this.authCheckPort = authCheckPort;
    this.claimsConverter = claimsConverter;
    this.securityConfig = securityConfig;
  }

  public Either<Rejection, AuthorizationRecord> isAuthorized(
      final TypedRecord<AuthorizationRecord> command) {
    return isAuthorized(command, PermissionType.UPDATE);
  }

  public Either<Rejection, AuthorizationRecord> isAuthorized(
      final TypedRecord<AuthorizationRecord> command, final PermissionType permissionType) {
    return isAuthorized(command, AuthorizationResourceType.AUTHORIZATION, permissionType);
  }

  public <R extends UnifiedRecordValue> Either<Rejection, R> isAuthorized(
      final TypedRecord<R> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return isAuthorized(command, resourceType, permissionType, AuthorizationScope.WILDCARD_CHAR);
  }

  public <R extends UnifiedRecordValue> Either<Rejection, R> isAuthorized(
      final TypedRecord<R> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String resourceId) {
    LOG.trace(
        "Checking {} permission on {} resource for command {}",
        permissionType,
        resourceType,
        command.getIntent());
    final var cslPermType = AuthzModelMapper.fromProtocol(permissionType);
    final var cslResourceType = AuthzModelMapper.fromProtocol(resourceType);
    return cslCheck.check(
        command,
        RequiredAuthorization.of(
            b ->
                b.resourceType(cslResourceType).permissionType(cslPermType).resourceId(resourceId)),
        command.getValue(),
        AuthorizationRejectionMapper.forbidden(permissionType, resourceType),
        AuthorizationRejectionMapper::toBareRejection);
  }

  /**
   * Like {@link #isAuthorized(TypedRecord, AuthorizationResourceType, PermissionType, String)} but
   * includes the {@code required resource identifiers are one of '[*, ...]'} suffix on the denial
   * message, matching the pre-migration engine-internal path used by process/resource domain
   * processors (as opposed to the bare identity-processor message).
   */
  public <R extends UnifiedRecordValue> Either<Rejection, R> isAuthorizedWithResourceIdentifiers(
      final TypedRecord<R> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String resourceId) {
    LOG.trace(
        "Checking {} permission on {} resource for command {}",
        permissionType,
        resourceType,
        command.getIntent());
    final var cslPermType = AuthzModelMapper.fromProtocol(permissionType);
    final var cslResourceType = AuthzModelMapper.fromProtocol(resourceType);
    return cslCheck.check(
        command,
        RequiredAuthorization.of(
            b ->
                b.resourceType(cslResourceType).permissionType(cslPermType).resourceId(resourceId)),
        command.getValue(),
        AuthorizationRejectionMapper.forbidden(permissionType, resourceType));
  }

  @SuppressWarnings("NullAway")
  public Either<Rejection, Void> isAuthorized(
      final Map<String, Object> claims,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String resourceId) {
    if (Boolean.TRUE.equals(claims.get(Authorization.AUTHORIZED_ANONYMOUS_USER))) {
      return Either.right(null);
    }
    if (!securityConfig.isAuthorizationsEnabled()
        && !securityConfig.isMultiTenancyChecksEnabled()) {
      return Either.right(null);
    }
    if (claims.get(Authorization.AUTHORIZED_USERNAME) == null
        && claims.get(Authorization.AUTHORIZED_CLIENT_ID) == null) {
      if (!securityConfig.isAuthorizationsEnabled()) {
        return Either.right(null);
      }
      return Either.left(AuthorizationRejectionMapper.forbidden(permissionType, resourceType));
    }
    final var auth = claimsConverter.convert(claims);
    final var cslPermType = AuthzModelMapper.fromProtocol(permissionType);
    final var cslResourceType = AuthzModelMapper.fromProtocol(resourceType);
    final var result =
        authCheckPort.check(
            auth,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(cslResourceType)
                        .permissionType(cslPermType)
                        .resourceId(resourceId)));
    if (result.isLeft()) {
      return Either.left(AuthorizationRejectionMapper.toRejection(result.leftValue()));
    }
    return Either.right(null);
  }

  public Either<Rejection, PersistedAuthorization> authorizationExists(
      final AuthorizationRecord authorizationRecord, final String rejectionMessage) {
    final var key = authorizationRecord.getAuthorizationKey();
    return authorizationState
        .get(key)
        .map(Either::<Rejection, PersistedAuthorization>right)
        .orElseGet(
            () ->
                Either.left(
                    new Rejection(RejectionType.NOT_FOUND, rejectionMessage.formatted(key))));
  }

  public Either<Rejection, AuthorizationRecord> permissionsAlreadyExist(
      final AuthorizationRecord record) {
    for (final PermissionType permission : record.getPermissionTypes()) {
      final var addedAuthorizationScope = createAuthorizationScope(record);
      final var currentAuthorizationScopes =
          authorizationState.getAuthorizationScopes(
              record.getOwnerType(), record.getOwnerId(), record.getResourceType(), permission);

      if (currentAuthorizationScopes.contains(addedAuthorizationScope)) {
        final var rejectionReason = createDuplicatePermissionRejectionReason(record);
        return Either.left(new Rejection(RejectionType.ALREADY_EXISTS, rejectionReason));
      }
    }
    return Either.right(record);
  }

  private AuthorizationScope createAuthorizationScope(final AuthorizationRecord record) {
    return new AuthorizationScope(
        record.getResourceMatcher(), record.getResourceId(), record.getResourcePropertyName());
  }

  private String createDuplicatePermissionRejectionReason(final AuthorizationRecord record) {
    final var ownerId = record.getOwnerId();
    return record.getResourceMatcher() == AuthorizationResourceMatcher.PROPERTY
        ? PERMISSIONS_FOR_RESOURCE_PROPERTY_NAME_ALREADY_EXISTS_MESSAGE.formatted(
            ownerId, record.getResourcePropertyName())
        : PERMISSIONS_FOR_RESOURCE_IDENTIFIER_ALREADY_EXISTS_MESSAGE.formatted(
            ownerId, record.getResourceId());
  }

  public Either<Rejection, AuthorizationRecord> hasValidPermissionTypes(
      final AuthorizationRecord record,
      final Set<PermissionType> permissionTypes,
      final AuthorizationResourceType resourceType,
      final String rejectionMessage) {
    if (resourceType.getSupportedPermissionTypes().containsAll(record.getPermissionTypes())) {
      return Either.right(record);
    }

    permissionTypes.removeAll(resourceType.getSupportedPermissionTypes());

    return Either.left(
        new Rejection(
            RejectionType.INVALID_ARGUMENT,
            rejectionMessage.formatted(
                permissionTypes, resourceType, resourceType.getSupportedPermissionTypes())));
  }
}
