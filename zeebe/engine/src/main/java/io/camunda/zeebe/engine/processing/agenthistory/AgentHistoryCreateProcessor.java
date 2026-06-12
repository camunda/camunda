/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistory;

import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AgentInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class AgentHistoryCreateProcessor implements TypedRecordProcessor<AgentHistoryRecord> {

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final AgentInstanceState agentInstanceState;
  private final JobState jobState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;

  public AgentHistoryCreateProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    agentInstanceState = processingState.getAgentInstanceState();
    jobState = processingState.getJobState();
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(final TypedRecord<AgentHistoryRecord> command) {
    final var commandValue = command.getValue();

    final var agentInstanceKey = commandValue.getAgentInstanceKey();
    final var agentInstanceRecord = agentInstanceState.getRecord(agentInstanceKey);
    if (agentInstanceRecord == null) {
      writeRejection(
          command,
          RejectionType.NOT_FOUND,
          "Expected to create agent history entry for agent instance with key '%d', but no such agent instance was found."
              .formatted(agentInstanceKey));
      return;
    }

    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
            .tenantId(agentInstanceRecord.getTenantId())
            .addResourceId(agentInstanceRecord.getBpmnProcessId())
            .build();
    final var authResult = authCheckBehavior.isAuthorizedOrInternalCommand(authRequest);
    if (authResult.isLeft()) {
      final var rejection = authResult.getLeft();
      writeRejection(command, rejection.type(), rejection.reason());
      return;
    }

    final var jobKey = commandValue.getJobKey();
    if (jobState.getState(jobKey) != JobState.State.ACTIVATED) {
      writeRejection(
          command,
          RejectionType.NOT_FOUND,
          "Expected to create agent history entry for job with key '%d', but the job is not active."
              .formatted(jobKey));
      return;
    }
    final var job = jobState.getJob(jobKey);

    final var jobElementInstanceKey = job.getElementInstanceKey();
    final var commandElementInstanceKey = commandValue.getElementInstanceKey();
    if (jobElementInstanceKey != commandElementInstanceKey) {
      writeRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          "Expected element instance key '%d' for agent history entry, but job '%d' is associated with element instance '%d'."
              .formatted(commandElementInstanceKey, jobKey, jobElementInstanceKey));
      return;
    }

    if (!agentInstanceRecord.getElementInstanceKeys().contains(jobElementInstanceKey)) {
      writeRejection(
          command,
          RejectionType.NOT_FOUND,
          "Expected to create agent history entry for element instance with key '%d', but it is not associated with agent instance with key '%d'."
              .formatted(jobElementInstanceKey, agentInstanceKey));
      return;
    }

    final var historyKey = keyGenerator.nextKey();
    final var event =
        new AgentHistoryRecord()
            .setAgentHistoryKey(historyKey)
            .setAgentInstanceKey(commandValue.getAgentInstanceKey())
            .setElementInstanceKey(jobElementInstanceKey)
            .setProcessInstanceKey(job.getProcessInstanceKey())
            .setRootProcessInstanceKey(job.getRootProcessInstanceKey())
            .setProcessDefinitionKey(job.getProcessDefinitionKey())
            .setTenantId(job.getTenantId())
            .setJobKey(commandValue.getJobKey())
            .setJobLease(commandValue.getJobLease())
            .setIteration(commandValue.getIteration())
            .setRole(commandValue.getRole())
            .setProducedAt(commandValue.getProducedAt())
            .setContent(commandValue.getContent())
            .setToolCalls(commandValue.getToolCalls());

    event
        .getMetrics()
        .setInputTokens(commandValue.getMetrics().getInputTokens())
        .setOutputTokens(commandValue.getMetrics().getOutputTokens())
        .setDurationMs(commandValue.getMetrics().getDurationMs());

    stateWriter.appendFollowUpEvent(historyKey, AgentHistoryIntent.CREATED, event);
    responseWriter.writeEventOnCommand(historyKey, AgentHistoryIntent.CREATED, event, command);

    // TODO: remove once the pending-commit lifecycle is implemented (#55033); at that point
    // COMMITTED will be emitted by AgentHistoryCommitProcessor after the job completes.
    stateWriter.appendFollowUpEvent(historyKey, AgentHistoryIntent.COMMITTED, event);
  }

  private void writeRejection(
      final TypedRecord<AgentHistoryRecord> command,
      final RejectionType rejectionType,
      final String reason) {
    rejectionWriter.appendRejection(command, rejectionType, reason);
    responseWriter.writeRejectionOnCommand(command, rejectionType, reason);
  }
}
