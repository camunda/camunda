/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;

public final class AgentInstanceCreateProcessor
    implements TypedRecordProcessor<AgentInstanceRecord> {

  private static final String ERROR_MSG_ELEMENT_INSTANCE_NOT_FOUND =
      "Expected to create agent instance for element instance with key '%d', but no such element instance was found.";
  private static final String ERROR_MSG_ELEMENT_INSTANCE_NOT_ACTIVE =
      "Expected to create agent instance for element instance with key '%d', but it is not active.";
  private static final String ERROR_MSG_UNSUPPORTED_ELEMENT_TYPE =
      "Expected to create agent instance for element instance with key '%d', but its BPMN element type '%s' is not supported. Supported types are: %s.";

  private static final List<BpmnElementType> SUPPORTED_ELEMENT_TYPES =
      List.of(BpmnElementType.AD_HOC_SUB_PROCESS, BpmnElementType.SERVICE_TASK);

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;

  public AgentInstanceCreateProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    elementInstanceState = processingState.getElementInstanceState();
    processState = processingState.getProcessState();
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(final TypedRecord<AgentInstanceRecord> command) {
    final var commandValue = command.getValue();
    final var elementInstanceKey = commandValue.getElementInstanceKey();

    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    if (elementInstance == null) {
      writeRejection(
          command,
          RejectionType.NOT_FOUND,
          ERROR_MSG_ELEMENT_INSTANCE_NOT_FOUND.formatted(elementInstanceKey));
      return;
    }

    if (!elementInstance.isActive()) {
      writeRejection(
          command,
          RejectionType.INVALID_STATE,
          ERROR_MSG_ELEMENT_INSTANCE_NOT_ACTIVE.formatted(elementInstanceKey));
      return;
    }

    final var elementValue = elementInstance.getValue();
    final var bpmnElementType = elementValue.getBpmnElementType();
    if (!SUPPORTED_ELEMENT_TYPES.contains(bpmnElementType)) {
      writeRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          ERROR_MSG_UNSUPPORTED_ELEMENT_TYPE.formatted(
              elementInstanceKey, bpmnElementType, SUPPORTED_ELEMENT_TYPES));
      return;
    }

    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
            .tenantId(elementValue.getTenantId())
            .addResourceId(elementValue.getBpmnProcessId())
            .build();
    final var authResult = authCheckBehavior.isAuthorizedOrInternalCommand(authRequest);
    if (authResult.isLeft()) {
      final var rejection = authResult.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    // Materialise identity fields from the element instance and force INITIALIZING status.
    // Metrics/limits/tools/changedAttributes are reset to their defaults so the CREATED event
    // carries a clean baseline regardless of what the CREATE command attempted to set.
    final var deployedProcess =
        processState.getProcessByKeyAndTenant(
            elementValue.getProcessDefinitionKey(), elementValue.getTenantId());
    final var versionTag = deployedProcess == null ? "" : deployedProcess.getVersionTag();

    final var agentInstanceKey = keyGenerator.nextKey();
    final var event =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setElementId(elementValue.getElementId())
            .setProcessInstanceKey(elementValue.getProcessInstanceKey())
            .setProcessDefinitionKey(elementValue.getProcessDefinitionKey())
            .setProcessDefinitionVersion(elementValue.getVersion())
            .setVersionTag(versionTag == null ? "" : versionTag)
            .setTenantId(elementValue.getTenantId())
            .setStatus(AgentInstanceStatus.INITIALIZING);

    // Copy the definition from the command so it is preserved on the CREATED event.
    final var commandDefinition = commandValue.getDefinition();
    event
        .getDefinition()
        .setModel(commandDefinition.getModel())
        .setProvider(commandDefinition.getProvider())
        .setSystemPrompt(commandDefinition.getSystemPrompt());

    stateWriter.appendFollowUpEvent(agentInstanceKey, AgentInstanceIntent.CREATED, event);
    responseWriter.writeEventOnCommand(
        agentInstanceKey, AgentInstanceIntent.CREATED, event, command);
  }

  private void writeRejection(
      final TypedRecord<AgentInstanceRecord> command,
      final RejectionType rejectionType,
      final String reason) {
    rejectionWriter.appendRejection(command, rejectionType, reason);
    responseWriter.writeRejectionOnCommand(command, rejectionType, reason);
  }
}
