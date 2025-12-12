/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

public class ProcessInstanceCreationHelper {
  private final AuthorizationCheckBehavior authCheckBehavior;

  public ProcessInstanceCreationHelper(final AuthorizationCheckBehavior authCheckBehavior) {
    this.authCheckBehavior = authCheckBehavior;
  }

  public Either<Rejection, DeployedProcess> isAuthorized(
      final TypedRecord<ProcessInstanceCreationRecord> command,
      final DeployedProcess deployedProcess) {
    final var processId = bufferAsString(deployedProcess.getBpmnProcessId());
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.CREATE_PROCESS_INSTANCE)
            .tenantId(command.getValue().getTenantId())
            .addResourceId(processId)
            .build();

    final var isAuthorized = authCheckBehavior.isAuthorizedOrInternalCommand(request);
    if (isAuthorized.isRight()) {
      return Either.right(deployedProcess);
    }

    final var rejection = isAuthorized.getLeft();
    final String errorMessage =
        RejectionType.NOT_FOUND.equals(rejection.type())
            ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                "create an instance of process",
                command.getValue().getProcessDefinitionKey(),
                "such process")
            : rejection.reason();
    return Either.left(new Rejection(rejection.type(), errorMessage));
  }
}
