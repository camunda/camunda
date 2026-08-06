/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AgentInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

/**
 * Handles {@code AGENT_INSTANCE:COMPLETE}, which has two meanings depending on whether {@code
 * agentInstanceKey} is set on the command:
 *
 * <ul>
 *   <li>set — completes exactly that agent instance.
 *   <li>unset ({@code -1}, only {@code processInstanceKey} set) — "batch completion": completes one
 *       agent instance still belonging to that process instance, then re-appends the same command
 *       as a follow-up so the next one is picked up on the next cycle. Once none remain, the
 *       command is rejected {@code NOT_FOUND} — the rejection doubles as the signal that the
 *       process instance's agent instances are fully cleaned up. At most two records are written
 *       per cycle (the {@code COMPLETED} event and the re-issued {@code COMPLETE}), so this relies
 *       on the general follow-up-command mechanism rather than a bespoke batch limit or cursor.
 * </ul>
 */
@ExcludeAuthorizationCheck
public final class AgentInstanceCompleteProcessor
    implements TypedRecordProcessor<AgentInstanceRecord>, SuspensionAware<AgentInstanceRecord> {

  private static final String ERROR_MSG_NOT_FOUND =
      "Expected to complete agent instance with key '%d', but no such agent instance was found.";
  private static final String ERROR_MSG_NONE_REMAINING =
      "No remaining agent instances to complete for process instance with key '%d'.";

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final AgentInstanceState agentInstanceState;

  public AgentInstanceCompleteProcessor(
      final Writers writers, final ProcessingState processingState) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    agentInstanceState = processingState.getAgentInstanceState();
  }

  @Override
  public void processRecord(final TypedRecord<AgentInstanceRecord> command) {
    final long agentInstanceKey = command.getValue().getAgentInstanceKey();
    if (agentInstanceKey == -1L) {
      completeNextOfProcessInstance(command);
    } else {
      completeSingle(command, agentInstanceKey);
    }
  }

  private void completeSingle(
      final TypedRecord<AgentInstanceRecord> command, final long agentInstanceKey) {
    final var current = agentInstanceState.getRecord(agentInstanceKey);
    if (current == null) {
      rejectionWriter.appendRejection(
          command, RejectionType.NOT_FOUND, ERROR_MSG_NOT_FOUND.formatted(agentInstanceKey));
      return;
    }

    appendCompleted(agentInstanceKey, current);
  }

  private void completeNextOfProcessInstance(final TypedRecord<AgentInstanceRecord> command) {
    final long processInstanceKey = command.getValue().getProcessInstanceKey();
    final Long agentInstanceKey =
        agentInstanceState.findFirstAgentInstanceKeyByProcessInstanceKey(processInstanceKey);
    if (agentInstanceKey == null) {
      rejectionWriter.appendRejection(
          command, RejectionType.NOT_FOUND, ERROR_MSG_NONE_REMAINING.formatted(processInstanceKey));
      return;
    }

    appendCompleted(agentInstanceKey, agentInstanceState.getRecord(agentInstanceKey));
    // re-chain: other agent instances may still be left for this process instance
    commandWriter.appendFollowUpCommand(
        command.getKey(),
        AgentInstanceIntent.COMPLETE,
        new AgentInstanceRecord().setProcessInstanceKey(processInstanceKey));
  }

  private void appendCompleted(final long agentInstanceKey, final AgentInstanceRecord current) {
    current.setStatus(AgentInstanceStatus.COMPLETED);
    current.setChangedAttributes(List.of(AgentInstanceRecord.ATTR_STATUS));
    stateWriter.appendFollowUpEvent(agentInstanceKey, AgentInstanceIntent.COMPLETED, current);
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<AgentInstanceRecord> record) {
    return SuspensionBehavior.BUFFER;
  }
}
