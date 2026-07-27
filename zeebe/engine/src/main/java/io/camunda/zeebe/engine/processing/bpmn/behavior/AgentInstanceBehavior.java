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
import io.camunda.zeebe.engine.state.immutable.AgentInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;

public final class AgentInstanceBehavior {

  private final TypedCommandWriter commandWriter;
  private final AgentInstanceState agentInstanceState;

  public AgentInstanceBehavior(final ProcessingState processingState, final Writers writers) {
    commandWriter = writers.command();
    agentInstanceState = processingState.getAgentInstanceState();
  }

  /** Completes every agent instance still associated with the given process instance. */
  public void completeAgentInstancesOfProcessInstance(final BpmnElementContext context) {
    final long processInstanceKey = context.getProcessInstanceKey();
    for (final long agentInstanceKey :
        agentInstanceState.getAgentInstanceKeysByProcessInstanceKey(processInstanceKey)) {
      commandWriter.appendFollowUpCommand(
          agentInstanceKey,
          AgentInstanceIntent.COMPLETE,
          new AgentInstanceRecord()
              .setAgentInstanceKey(agentInstanceKey)
              .setProcessInstanceKey(processInstanceKey));
    }
  }
}
