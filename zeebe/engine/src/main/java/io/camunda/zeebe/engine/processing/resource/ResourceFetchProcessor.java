/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.PersistedResource;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class ResourceFetchProcessor implements TypedRecordProcessor<ResourceRecord> {

  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final StateWriter stateWriter;
  private final ResourceState resourceState;
  private final AuthorizationCheckBehavior authorizationCheckBehavior;

  public ResourceFetchProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authorizationCheckBehavior) {
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    stateWriter = writers.state();
    resourceState = processingState.getResourceState();
    this.authorizationCheckBehavior = authorizationCheckBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ResourceRecord> command) {
    final var resourceKey = command.getValue().getResourceKey();
    for (final var tenantId :
        authorizationCheckBehavior.getAuthorizedTenantIds(command).getAuthorizedTenantIds()) {
      final var optionalResource = resourceState.findResourceByKey(resourceKey, tenantId);
      if (optionalResource.isPresent()) {
        final var record = asResourceRecord(optionalResource.get());
        stateWriter.appendFollowUpEvent(resourceKey, ResourceIntent.FETCHED, record);
        responseWriter.writeEventOnCommand(resourceKey, ResourceIntent.FETCHED, record, command);
        return;
      }
    }
    throw new NoSuchResourceException(resourceKey);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ResourceRecord> command, final Throwable error) {
    if (error instanceof final NoSuchResourceException exception) {
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.NOT_FOUND, exception.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }
    return ProcessingError.UNEXPECTED_ERROR;
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

  private static final class NoSuchResourceException extends IllegalStateException {
    private static final String ERROR_MESSAGE_RESOURCE_NOT_FOUND =
        "Expected to fetch resource but no resource found with key `%d`";

    private NoSuchResourceException(final long resourceKey) {
      super(String.format(ERROR_MESSAGE_RESOURCE_NOT_FOUND, resourceKey));
    }
  }
}
