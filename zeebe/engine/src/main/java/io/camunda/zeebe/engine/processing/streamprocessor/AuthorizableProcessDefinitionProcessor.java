/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.processing.identity.behavior.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Map;

public final class AuthorizableProcessDefinitionProcessor<T extends UnifiedRecordValue, Resource>
    implements TypedRecordProcessor<T> {

  private final AuthorizationCheckBehavior authorizationCheckBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final AuthorizedProcessorInterface<T, Resource> delegate;

  public AuthorizableProcessDefinitionProcessor(
      final AuthorizationCheckBehavior authorizationCheckBehavior,
      final Writers writers,
      final AuthorizedProcessorInterface<T, Resource> delegate) {
    this.authorizationCheckBehavior = authorizationCheckBehavior;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.delegate = delegate;
  }

  @Override
  public void processRecord(final TypedRecord<T> command) {
    // Check authorization
    final var resource = delegate.getResource(command);
    final var isAuthorized =
        authorizationCheckBehavior.isAuthorized(
            command,
            resource.resourceType,
            resource.permissionType,
            delegate.getResourceIds(resource.resource));

    // Reject if not authorized
    if (isAuthorized) {
      delegate.processRecord(command, resource.resource);
    } else {
      rejectionWriter.appendRejection(command, RejectionType.UNAUTHORIZED, resource.errorMessage);
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.UNAUTHORIZED, resource.errorMessage);
    }
  }

  public static final class AuthorizationResource<Resource> {
    private final Resource resource;
    private final AuthorizationResourceType resourceType;
    private final PermissionType permissionType;
    private final String errorMessage;

    public AuthorizationResource(
        final Resource resource,
        final AuthorizationResourceType resourceType,
        final PermissionType permissionType,
        final String bpmnProcessId,
        final String errorMessage) {
      this.resource = resource;
      this.resourceType = resourceType;
      this.permissionType = permissionType;
      this.errorMessage = errorMessage;
    }
  }

  public interface AuthorizedProcessorInterface<T extends UnifiedRecordValue, Resource> {
    void processRecord(final TypedRecord<T> command, final Resource resource);

    AuthorizationResource<Resource> getResource(final TypedRecord<T> command);

    Map<String, String> getResourceIds(Resource resource);
  }
}
