/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.ForbiddenException;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class ResourceFetchProcessor implements TypedRecordProcessor<ResourceRecord> {

  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final StateWriter stateWriter;
  private final ResourceState resourceState;
  private final TenantState tenantState;
  private final AuthorizationCheckBehavior authorizationCheckBehavior;

  public ResourceFetchProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authorizationCheckBehavior) {
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    stateWriter = writers.state();
    resourceState = processingState.getResourceState();
    tenantState = processingState.getTenantState();
    this.authorizationCheckBehavior = authorizationCheckBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ResourceRecord> command) {
    final var resourceKey = command.getValue().getResourceKey();
    findResource(command, resourceKey)
        .ifPresentOrElse(
            resource -> {
              checkAuthorization(command, BufferUtil.bufferAsString(resource.getResourceId()));
              final var record = asResourceRecord(resource);
              stateWriter.appendFollowUpEvent(resourceKey, ResourceIntent.FETCHED, record);
              responseWriter.writeEventOnCommand(
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
        .setResource(BufferUtil.wrapString(resource.getResource()))
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
    final var authorizedTenants = authorizationCheckBehavior.getAuthorizedTenantIds(command);
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

  private void checkAuthorization(
      final TypedRecord<ResourceRecord> command, final String resourceId) {
    final var authRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.RESOURCE, PermissionType.READ)
            .addResourceId(resourceId);
    if (authorizationCheckBehavior.isAuthorized(authRequest).isLeft()) {
      throw new ForbiddenException(authRequest);
    }
  }

  private ProcessingError rejectCommand(
      final TypedRecord<ResourceRecord> command,
      final RejectionType rejectionType,
      final String reason) {
    rejectionWriter.appendRejection(command, rejectionType, reason);
    responseWriter.writeRejectionOnCommand(command, rejectionType, reason);
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
