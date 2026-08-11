/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationMigrateProcessor.SafetyCheckFailedException;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.AgentDefinitionState;
import io.camunda.zeebe.engine.state.immutable.AgentInstanceState;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;

public class ProcessInstanceMigrationAgentInstanceBehavior {

  private static final long UNSET_AGENT_DEFINITION_KEY = -1L;

  private final StateWriter stateWriter;
  private final AgentInstanceState agentInstanceState;
  private final AgentDefinitionState agentDefinitionState;

  public ProcessInstanceMigrationAgentInstanceBehavior(
      final StateWriter stateWriter,
      final AgentInstanceState agentInstanceState,
      final AgentDefinitionState agentDefinitionState) {
    this.stateWriter = stateWriter;
    this.agentInstanceState = agentInstanceState;
    this.agentDefinitionState = agentDefinitionState;
  }

  /**
   * Re-points every agent instance of the given process instance at the target process definition,
   * re-resolving its {@code agentDefinitionKey} to the target version's agent definition.
   *
   * @param mappedElementIds the source-to-target element id mapping resolved for this migration; an
   *     agent instance whose {@code elementId} has no entry keeps its current id
   */
  public void migrateAgentInstances(
      final long processInstanceKey,
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> mappedElementIds) {
    agentInstanceState
        .getAgentInstanceKeysByProcessInstanceKey(processInstanceKey)
        .forEach(
            agentInstanceKey ->
                migrateAgentInstance(
                    agentInstanceKey,
                    processInstanceKey,
                    targetProcessDefinition,
                    mappedElementIds));
  }

  private void migrateAgentInstance(
      final long agentInstanceKey,
      final long processInstanceKey,
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> mappedElementIds) {
    final var record = agentInstanceState.getRecord(agentInstanceKey);
    if (record == null) {
      throw new SafetyCheckFailedException(
          String.format(
              """
              Expected to migrate an agent instance for process instance with key '%d', \
              but could not find agent instance with key '%d'. \
              Please report this as a bug""",
              processInstanceKey, agentInstanceKey));
    }
    final var targetElementId =
        mappedElementIds.getOrDefault(record.getElementId(), record.getElementId());

    stateWriter.appendFollowUpEvent(
        agentInstanceKey,
        AgentInstanceIntent.MIGRATED,
        record
            .setProcessDefinitionKey(targetProcessDefinition.getKey())
            .setProcessDefinitionVersion(targetProcessDefinition.getVersion())
            .setBpmnProcessId(BufferUtil.bufferAsString(targetProcessDefinition.getBpmnProcessId()))
            .setVersionTag(targetProcessDefinition.getVersionTag())
            .setElementId(targetElementId)
            .setAgentDefinitionKey(
                resolveAgentDefinitionKey(targetProcessDefinition, targetElementId)));
  }

  /**
   * Resolves the agent definition key the migrated agent instance should point at in the target
   * process definition. Returns {@code -1} when the target element carries no agent definition,
   * which is the allowed case of migrating an agent element onto a plain (non-agent) element.
   */
  private long resolveAgentDefinitionKey(
      final DeployedProcess targetProcessDefinition, final String targetElementId) {
    final var targetKey =
        agentDefinitionState.getAgentDefinitionKey(
            targetProcessDefinition.getKey(), BufferUtil.wrapString(targetElementId));
    return targetKey == null ? UNSET_AGENT_DEFINITION_KEY : targetKey;
  }
}
