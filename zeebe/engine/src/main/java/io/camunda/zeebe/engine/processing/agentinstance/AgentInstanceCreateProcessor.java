/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
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

  // CAUTION: callers may parse this message to extract the existing agentInstanceKey from the
  // second '%d'. Wording changes that alter the position of the numeric values are a breaking
  // contract change — update connector-side parsing logic in sync.
  private static final String ERROR_MSG_AGENT_INSTANCE_ALREADY_EXISTS =
      "Expected to associate element instance with key '%d' with an agent instance, but it is already associated with agent instance with key '%d'.";

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
  private final CslAuthorizationCheck cslCheck;
  private final PermissionsBehavior permissionsBehavior;
  private final KeyGenerator keyGenerator;

  public AgentInstanceCreateProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final CslAuthorizationCheck cslCheck,
      final PermissionsBehavior permissionsBehavior,
      final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    elementInstanceState = processingState.getElementInstanceState();
    processState = processingState.getProcessState();
    this.cslCheck = cslCheck;
    this.permissionsBehavior = permissionsBehavior;
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
    final var isAuthorized =
        cslCheck
            .checkTenant(
                command,
                elementInstanceValue.getTenantId(),
                command.getValue(),
                new Rejection(
                    RejectionType.NOT_FOUND,
                    ERROR_MSG_ELEMENT_INSTANCE_NOT_FOUND.formatted(elementInstanceKey)))
            .flatMap(
                value ->
                    permissionsBehavior.isAuthorizedWithResourceIdentifiers(
                        command,
                        AuthorizationResourceType.PROCESS_DEFINITION,
                        PermissionType.UPDATE_PROCESS_INSTANCE,
                        elementInstanceValue.getBpmnProcessId()));
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    // Reject CREATE when an agent instance already exists for this element instance. The existence
    // check runs before the active-state and element-type guards so that a late retry against an
    // element instance that has since left ACTIVE (e.g. parked in COMPLETING behind an incident)
    // gets ALREADY_EXISTS rather than INVALID_STATE. Both the stream rejection and the HTTP
    // response are consistent: no success event is emitted.
    final var existingAgentInstanceKey = elementInstance.getAgentInstanceKey();
    if (existingAgentInstanceKey != -1L) {
      writeRejection(
          command,
          RejectionType.ALREADY_EXISTS,
          ERROR_MSG_AGENT_INSTANCE_ALREADY_EXISTS.formatted(
              elementInstanceKey, existingAgentInstanceKey));
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
            .setElementInstanceKeys(List.of(elementInstanceKey))
            .setElementId(elementInstanceValue.getElementId())
            .setBpmnProcessId(elementInstanceValue.getBpmnProcessId())
            .setProcessInstanceKey(elementInstanceValue.getProcessInstanceKey())
            .setRootProcessInstanceKey(elementInstanceValue.getRootProcessInstanceKey())
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
    responseWriter.writeAcceptedResponseOnCommand(
        agentInstanceKey, AgentInstanceIntent.CREATED, event, command);
  }

  private void writeRejection(
      final TypedRecord<AgentInstanceRecord> command,
      final RejectionType rejectionType,
      final String reason) {
    rejectionWriter.appendRejection(command, rejectionType, reason);
    responseWriter.writeRejectedResponseOnCommand(command, rejectionType, reason);
  }
}
