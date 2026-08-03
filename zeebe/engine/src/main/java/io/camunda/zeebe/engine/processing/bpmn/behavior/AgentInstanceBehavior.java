/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceBatchRecord;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceBatchIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class AgentInstanceBehavior {

  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;

  public AgentInstanceBehavior(final Writers writers, final KeyGenerator keyGenerator) {
    commandWriter = writers.command();
    this.keyGenerator = keyGenerator;
  }

  /**
   * Triggers the batched completion of every agent instance still associated with the given process
   * instance, by writing a single {@link AgentInstanceBatchIntent#COMPLETE} command.
   */
  public void completeAgentInstancesOfProcessInstance(final BpmnElementContext context) {
    final long processInstanceKey = context.getProcessInstanceKey();
    commandWriter.appendFollowUpCommand(
        keyGenerator.nextKey(),
        AgentInstanceBatchIntent.COMPLETE,
        new AgentInstanceBatchRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(context.getProcessDefinitionKey()));
  }
}
