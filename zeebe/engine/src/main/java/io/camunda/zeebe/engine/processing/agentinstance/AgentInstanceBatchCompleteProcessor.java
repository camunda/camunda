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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AgentInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.collections.MutableInteger;
import org.agrona.collections.MutableLong;

/**
 * Completes the agent instances of a process instance in bounded-size batches, so that the
 * individual {@link AgentInstanceIntent#COMPLETE} commands issued for a process instance with a
 * large number of agent instances never risk exceeding the maximum record-batch size in a single
 * write. Follows up with another {@link AgentInstanceBatchIntent#COMPLETE} command carrying a
 * resume cursor when more agent instances remain, and finishes with {@link
 * AgentInstanceBatchIntent#COMPLETED} once every agent instance of the process instance has been
 * visited.
 */
@ExcludeAuthorizationCheck
public final class AgentInstanceBatchCompleteProcessor
    implements TypedRecordProcessor<AgentInstanceBatchRecord> {

  private static final int FOLLOWUP_COMMAND_SAFETY_MARGIN =
      new AgentInstanceBatchRecord()
          .setProcessInstanceKey(Long.MAX_VALUE)
          .setProcessDefinitionKey(Long.MAX_VALUE)
          .setAgentInstanceKey(Long.MAX_VALUE)
          .getLength();

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final AgentInstanceState agentInstanceState;
  private final int batchLimit;

  public AgentInstanceBatchCompleteProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final AgentInstanceState agentInstanceState,
      final int batchLimit) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.keyGenerator = keyGenerator;
    this.agentInstanceState = agentInstanceState;
    this.batchLimit = batchLimit;
  }

  @Override
  public void processRecord(final TypedRecord<AgentInstanceBatchRecord> record) {
    final var value = record.getValue();
    final var processInstanceKey = value.getProcessInstanceKey();
    final var visited = new MutableInteger(0);
    final var lastVisitedKey = new MutableLong(value.getAgentInstanceKey());

    final var hasMore =
        agentInstanceState.visitAgentInstanceKeysByProcessInstanceKey(
            processInstanceKey,
            value.getAgentInstanceKey(),
            agentInstanceKey -> {
              final var command =
                  new AgentInstanceRecord()
                      .setAgentInstanceKey(agentInstanceKey)
                      .setProcessInstanceKey(processInstanceKey);
              if (!commandWriter.canWriteCommandOfLength(
                      command.getLength() + FOLLOWUP_COMMAND_SAFETY_MARGIN)
                  || visited.get() >= batchLimit) {
                return false;
              }
              commandWriter.appendFollowUpCommand(
                  agentInstanceKey, AgentInstanceIntent.COMPLETE, command);
              lastVisitedKey.set(agentInstanceKey);
              visited.increment();
              return true;
            });

    if (hasMore) {
      final var nextBatch =
          new AgentInstanceBatchRecord()
              .setProcessInstanceKey(processInstanceKey)
              .setProcessDefinitionKey(value.getProcessDefinitionKey())
              .setAgentInstanceKey(lastVisitedKey.get());
      commandWriter.appendFollowUpCommand(
          keyGenerator.nextKey(), AgentInstanceBatchIntent.COMPLETE, nextBatch);
    } else {
      stateWriter.appendFollowUpEvent(record.getKey(), AgentInstanceBatchIntent.COMPLETED, value);
    }
  }
}
