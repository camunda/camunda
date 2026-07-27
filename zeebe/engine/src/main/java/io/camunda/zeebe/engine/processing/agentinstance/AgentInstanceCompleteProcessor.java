/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
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

@ExcludeAuthorizationCheck
public final class AgentInstanceCompleteProcessor
    implements TypedRecordProcessor<AgentInstanceRecord> {

  private static final String ERROR_MSG_NOT_FOUND =
      "Expected to complete agent instance with key '%d', but no such agent instance was found.";

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final AgentInstanceState agentInstanceState;

  public AgentInstanceCompleteProcessor(
      final Writers writers, final ProcessingState processingState) {
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    agentInstanceState = processingState.getAgentInstanceState();
  }

  @Override
  public void processRecord(final TypedRecord<AgentInstanceRecord> command) {
    final long agentInstanceKey = command.getKey();
    final var current = agentInstanceState.getRecord(agentInstanceKey);
    if (current == null) {
      rejectionWriter.appendRejection(
          command, RejectionType.NOT_FOUND, ERROR_MSG_NOT_FOUND.formatted(agentInstanceKey));
      return;
    }

    current.setStatus(AgentInstanceStatus.COMPLETED);
    current.setChangedAttributes(List.of(AgentInstanceRecord.ATTR_STATUS));
    stateWriter.appendFollowUpEvent(agentInstanceKey, AgentInstanceIntent.COMPLETED, current);
  }
}
