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
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class AgentInstanceProcessors {

  private AgentInstanceProcessors() {}

  public static void addAgentInstanceProcessors(
      final KeyGenerator keyGenerator,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers) {
    typedRecordProcessors.onCommand(
        ValueType.AGENT_INSTANCE,
        AgentInstanceIntent.CREATE,
        new AgentInstanceCreateProcessor(keyGenerator, writers.state()));
    typedRecordProcessors.onCommand(
        ValueType.AGENT_INSTANCE,
        AgentInstanceIntent.UPDATE,
        new AgentInstanceLifecycleProcessor(writers.state(), AgentInstanceIntent.UPDATED));
    typedRecordProcessors.onCommand(
        ValueType.AGENT_INSTANCE,
        AgentInstanceIntent.DELETE,
        new AgentInstanceLifecycleProcessor(writers.state(), AgentInstanceIntent.DELETED));
  }

  @ExcludeAuthorizationCheck
  static final class AgentInstanceCreateProcessor
      implements TypedRecordProcessor<AgentInstanceRecord> {

    private final KeyGenerator keyGenerator;
    private final StateWriter stateWriter;

    AgentInstanceCreateProcessor(final KeyGenerator keyGenerator, final StateWriter stateWriter) {
      this.keyGenerator = keyGenerator;
      this.stateWriter = stateWriter;
    }

    @Override
    public void processRecord(final TypedRecord<AgentInstanceRecord> command) {
      final var record = command.getValue();
      final long key = keyGenerator.nextKey();
      record.setAgentInstanceKey(key);
      stateWriter.appendFollowUpEvent(key, AgentInstanceIntent.CREATED, record);
    }
  }

  @ExcludeAuthorizationCheck
  static final class AgentInstanceLifecycleProcessor
      implements TypedRecordProcessor<AgentInstanceRecord> {

    private final StateWriter stateWriter;
    private final AgentInstanceIntent followUpIntent;

    AgentInstanceLifecycleProcessor(
        final StateWriter stateWriter, final AgentInstanceIntent followUpIntent) {
      this.stateWriter = stateWriter;
      this.followUpIntent = followUpIntent;
    }

    @Override
    public void processRecord(final TypedRecord<AgentInstanceRecord> command) {
      final var record = command.getValue();
      stateWriter.appendFollowUpEvent(record.getAgentInstanceKey(), followUpIntent, record);
    }
  }
}
