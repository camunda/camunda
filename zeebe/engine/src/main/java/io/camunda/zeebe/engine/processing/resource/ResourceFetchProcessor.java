/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.processing.identity.AuthenticatedAuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenantsAdapter;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.exception.ForbiddenException;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.PersistedResource;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class ResourceFetchProcessor implements TypedRecordProcessor<ResourceRecord> {

  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final StateWriter stateWriter;
  private final ResourceState resourceState;
  private final TenantState tenantState;
  private final PermissionsBehavior permissionsBehavior;
  private final LazyTokenClaimsConverter claimsConverter;
  private final EngineSecurityConfig securityConfig;

  public ResourceFetchProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final PermissionsBehavior permissionsBehavior,
      final LazyTokenClaimsConverter claimsConverter,
      final EngineSecurityConfig securityConfig) {
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    stateWriter = writers.state();
    resourceState = processingState.getResourceState();
    tenantState = processingState.getTenantState();
    this.permissionsBehavior = permissionsBehavior;
    this.claimsConverter = claimsConverter;
    this.securityConfig = securityConfig;
  }

  @Override
  public void processRecord(final TypedRecord<ResourceRecord> command) {
    final var resourceKey = command.getValue().getResourceKey();
    findResource(command, resourceKey)
        .ifPresentOrElse(
            resource -> {
              checkAuthorization(command, resource);
              final var record = asResourceRecord(resource);
              stateWriter.appendFollowUpEvent(resourceKey, ResourceIntent.FETCHED, record);
              responseWriter.writeAcceptedResponseOnCommand(
                  resourceKey, ResourceIntent.FETCHED, record, command);
            },
            () -> {
              throw new NoSuchResourceException(resourceKey);
            });
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ResourceRecord> command, final Throwable error) {
    return switch (error) {
      case final NoSuchResourceException exception ->
          rejectCommand(command, RejectionType.NOT_FOUND, exception.getMessage());
      case final ForbiddenException exception ->
          rejectCommand(command, exception.getRejectionType(), exception.getMessage());
      default -> ProcessingError.UNEXPECTED_ERROR;
    };
  }

  private static ResourceRecord asResourceRecord(final PersistedResource resource) {
    return new ResourceRecord()
        .setResource(resource.getResourceBuffer())
        .setResourceKey(resource.getResourceKey())
        .setResourceId(resource.getResourceId())
        .setResourceName(resource.getResourceName())
        .setVersion(resource.getVersion())
        .setVersionTag(resource.getVersionTag())
        .setTenantId(resource.getTenantId())
        .setDeploymentKey(resource.getDeploymentKey())
        .setChecksum(resource.getChecksum());
  }

  private Optional<PersistedResource> findResource(
      final TypedRecord<ResourceRecord> command, final long resourceKey) {
    final var authorizedTenants = determineAuthorizedTenants(command);
    return AuthorizedTenants.ANONYMOUS.equals(authorizedTenants)
        ? findResourceForAnonymouslyAuthorizedTenants(resourceKey)
        : findResourceForAuthenticatedAuthorizedTenants(resourceKey, authorizedTenants);
  }

  private Optional<PersistedResource> findResourceForAnonymouslyAuthorizedTenants(
      final long resourceKey) {
    return resourceState
        .findResourceByKey(resourceKey, TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .or(() -> tryForEachTenantUntilResourceFound(resourceKey));
  }

  private Optional<PersistedResource> findResourceForAuthenticatedAuthorizedTenants(
      final long resourceKey, final AuthorizedTenants authorizedTenants) {
    for (final var tenantId : authorizedTenants.getAuthorizedTenantIds()) {
      final var optionalResource = resourceState.findResourceByKey(resourceKey, tenantId);
      if (optionalResource.isPresent()) {
        return optionalResource;
      }
    }
    return Optional.empty();
  }

  private Optional<PersistedResource> tryForEachTenantUntilResourceFound(final long resourceKey) {
    final var resource = new AtomicReference<PersistedResource>();
    tenantState.forEachTenant(
        tenantId -> {
          final var optionalResource = resourceState.findResourceByKey(resourceKey, tenantId);
          if (optionalResource.isPresent()) {
            resource.set(optionalResource.get());
            return false;
          }
          return true;
        });
    return Optional.ofNullable(resource.get());
  }

  private AuthorizedTenants determineAuthorizedTenants(final TypedRecord<?> command) {
    final var authorizations = command.getAuthorizations();
    if (Boolean.TRUE.equals(authorizations.get(Authorization.AUTHORIZED_ANONYMOUS_USER))) {
      return AuthorizedTenants.ANONYMOUS;
    }
    if (!securityConfig.isMultiTenancyChecksEnabled()) {
      return AuthorizedTenants.DEFAULT_TENANTS;
    }
    if (authorizations.get(Authorization.AUTHORIZED_USERNAME) == null
        && authorizations.get(Authorization.AUTHORIZED_CLIENT_ID) == null) {
      return new AuthenticatedAuthorizedTenants(List.of());
    }
    return new AuthorizedTenantsAdapter(claimsConverter.convert(authorizations));
  }

  private void checkAuthorization(
      final TypedRecord<ResourceRecord> command, final PersistedResource resource) {
    final var isAuthorized =
        permissionsBehavior.isAuthorizedWithResourceIdentifiers(
            command,
            AuthorizationResourceType.RESOURCE,
            PermissionType.READ,
            BufferUtil.bufferAsString(resource.getResourceId()));
    if (isAuthorized.isLeft()) {
      throw new ForbiddenException(isAuthorized.getLeft());
    }
  }

  private ProcessingError rejectCommand(
      final TypedRecord<ResourceRecord> command,
      final RejectionType rejectionType,
      final String reason) {
    rejectionWriter.appendRejection(command, rejectionType, reason);
    responseWriter.writeRejectedResponseOnCommand(command, rejectionType, reason);
    return ProcessingError.EXPECTED_ERROR;
  }

  private static final class NoSuchResourceException extends IllegalStateException {
    private static final String ERROR_MESSAGE_RESOURCE_NOT_FOUND =
        "Expected to fetch resource but no resource found with key `%d`";

    private NoSuchResourceException(final long resourceKey) {
      super(String.format(ERROR_MESSAGE_RESOURCE_NOT_FOUND, resourceKey));
    }
  }
}
