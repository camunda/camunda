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
import io.camunda.zeebe.engine.state.immutable.AgentInstanceState;
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
  private static final String ERROR_MSG_AGENT_INSTANCE_ALREADY_EXISTS =
      "Expected to create agent instance for element instance with key '%d', but an agent instance with key '%d' already exists for it.";

  private static final List<BpmnElementType> SUPPORTED_ELEMENT_TYPES =
      List.of(BpmnElementType.AD_HOC_SUB_PROCESS, BpmnElementType.SERVICE_TASK);

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final AgentInstanceState agentInstanceState;
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
    agentInstanceState = processingState.getAgentInstanceState();
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

    final var elementInstanceValue = elementInstance.getValue();
    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
            .tenantId(elementInstanceValue.getTenantId())
            .addResourceId(elementInstanceValue.getBpmnProcessId())
            .build();
    final var authResult = authCheckBehavior.isAuthorizedOrInternalCommand(authRequest);
    if (authResult.isLeft()) {
      final var rejection = authResult.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    // Idempotent CREATE: if an agent instance already exists, return the existing one to the
    // client (the public API has no 409). Reject on the stream to suppress a duplicate CREATED
    // event, but respond with the existing record so the client sees the same result as the
    // original CREATE. This check runs before the active-state and element-type guards so that a
    // late retry against an element instance that has since left ACTIVE (e.g. parked in
    // COMPLETING behind an incident) still returns the existing record rather than INVALID_STATE.
    final var existingAgentInstanceKey = elementInstance.getAgentInstanceKey();
    if (existingAgentInstanceKey != -1L) {
      final var existingRecord = agentInstanceState.getRecord(existingAgentInstanceKey);
      // The stored record carries the changedAttributes from the last UPDATE that touched it. On
      // CREATED that field is contractually empty (see
      // AgentInstanceRecordValue#getChangedAttributes),
      // so strip it before responding. getRecord returns a fresh deserialization, so this mutation
      // does not leak back into state.
      existingRecord.setChangedAttributes(List.of());
      rejectionWriter.appendRejection(
          command,
          RejectionType.ALREADY_EXISTS,
          ERROR_MSG_AGENT_INSTANCE_ALREADY_EXISTS.formatted(
              elementInstanceKey, existingAgentInstanceKey));
      responseWriter.writeEventOnCommand(
          existingAgentInstanceKey, AgentInstanceIntent.CREATED, existingRecord, command);
      return;
    }

    if (!elementInstance.isActive()) {
      writeRejection(
          command,
          RejectionType.INVALID_STATE,
          ERROR_MSG_ELEMENT_INSTANCE_NOT_ACTIVE.formatted(elementInstanceKey));
      return;
    }

    final var bpmnElementType = elementInstanceValue.getBpmnElementType();
    if (!SUPPORTED_ELEMENT_TYPES.contains(bpmnElementType)) {
      writeRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          ERROR_MSG_UNSUPPORTED_ELEMENT_TYPE.formatted(
              elementInstanceKey, bpmnElementType, SUPPORTED_ELEMENT_TYPES));
      return;
    }

    final var deployedProcess =
        processState.getProcessByKeyAndTenant(
            elementInstanceValue.getProcessDefinitionKey(), elementInstanceValue.getTenantId());

    final var agentInstanceKey = keyGenerator.nextKey();
    final var event =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setElementId(elementInstanceValue.getElementId())
            .setBpmnProcessId(elementInstanceValue.getBpmnProcessId())
            .setProcessInstanceKey(elementInstanceValue.getProcessInstanceKey())
            .setProcessDefinitionKey(elementInstanceValue.getProcessDefinitionKey())
            .setProcessDefinitionVersion(elementInstanceValue.getVersion())
            .setVersionTag(deployedProcess == null ? "" : deployedProcess.getVersionTag())
            .setTenantId(elementInstanceValue.getTenantId())
            .setStatus(AgentInstanceStatus.INITIALIZING);

    event
        .getDefinition()
        .setModel(commandValue.getDefinition().getModel())
        .setProvider(commandValue.getDefinition().getProvider())
        .setSystemPrompt(commandValue.getDefinition().getSystemPrompt());

    event
        .getLimits()
        .setMaxTokens(commandValue.getLimits().getMaxTokens())
        .setMaxModelCalls(commandValue.getLimits().getMaxModelCalls())
        .setMaxToolCalls(commandValue.getLimits().getMaxToolCalls());

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
