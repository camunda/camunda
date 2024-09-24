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

public abstract class AuthorizableProcessDefinitionProcessor<T extends UnifiedRecordValue, Resource>
    implements TypedRecordProcessor<T> {

  private static final String BPMN_PROCESS_ID_RECOURCE_ID = "bpmnProcessId";
  private static final AuthorizationResourceType AUTHORIZATION_RESOURCE_TYPE =
      AuthorizationResourceType.PROCESS_DEFINITION;

  private final AuthorizationCheckBehavior authorizationCheckBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public AuthorizableProcessDefinitionProcessor(
      final AuthorizationCheckBehavior authorizationCheckBehavior, final Writers writers) {
    this.authorizationCheckBehavior = authorizationCheckBehavior;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<T> command) {
    // Check authorization
    final var resource = getResource(command);
    final var isAuthorized =
        authorizationCheckBehavior.isAuthorized(
            command,
            AUTHORIZATION_RESOURCE_TYPE,
            resource.permissionType,
            resource.getResourceIds());

    // Reject if not authorized
    if (isAuthorized) {
      processRecord(command, resource.resource);
    } else {
      rejectionWriter.appendRejection(command, RejectionType.UNAUTHORIZED, resource.errorMessage);
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.UNAUTHORIZED, resource.errorMessage);
    }
  }

  public abstract void processRecord(final TypedRecord<T> command, final Resource resource);

  public abstract AuthorizationResource getResource(final TypedRecord<T> command);

  public final class AuthorizationResource {
    private final Resource resource;
    private final PermissionType permissionType;
    private final String bpmnProcessId;
    private final String errorMessage;

    public AuthorizationResource(
        final Resource resource,
        final PermissionType permissionType,
        final String bpmnProcessId,
        final String errorMessage) {
      this.resource = resource;
      this.permissionType = permissionType;
      this.bpmnProcessId = bpmnProcessId;
      this.errorMessage = errorMessage;
    }

    public Map<String, String> getResourceIds() {
      return Map.of(BPMN_PROCESS_ID_RECOURCE_ID, bpmnProcessId);
    }
  }
}
