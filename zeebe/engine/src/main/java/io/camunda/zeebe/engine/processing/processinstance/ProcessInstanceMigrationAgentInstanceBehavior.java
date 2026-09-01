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
   * Rejects the migration when it would leave any agent instance of the process instance without a
   * backing agent definition of the same {@code AgentDefinitionType} — because an agent instance
   * must always belong to an agent definition, and its type must not change. Every agent instance
   * is validated, including orphaned ones whose owning element already completed and is therefore
   * no longer part of the migrated element tree that {@link
   * ProcessInstanceMigrationMigrateProcessor}'s per-element validation walks.
   *
   * @param mappedElementIds the source-to-target element id mapping resolved for this migration; an
   *     agent instance whose {@code elementId} has no entry keeps its current id
   */
  public void validateAgentInstanceMigrations(
      final long processInstanceKey,
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> mappedElementIds) {
    agentInstanceState
        .getAgentInstanceKeysByProcessInstanceKey(processInstanceKey)
        .forEach(
            agentInstanceKey ->
                validateAgentInstanceMigration(
                    agentInstanceKey,
                    processInstanceKey,
                    sourceProcessDefinition,
                    targetProcessDefinition,
                    mappedElementIds));
  }

  private void validateAgentInstanceMigration(
      final long agentInstanceKey,
      final long processInstanceKey,
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> mappedElementIds) {
    final var record = agentInstanceState.getRecord(agentInstanceKey);
    if (record == null) {
      // a missing record is surfaced as a bug by the re-resolution pass; nothing to validate here
      return;
    }
    final var sourceElementId = record.getElementId();
    final var targetElementId = mappedElementIds.getOrDefault(sourceElementId, sourceElementId);
    ProcessInstanceMigrationPreconditions.requireCompatibleAgentDefinition(
        sourceProcessDefinition,
        targetProcessDefinition,
        sourceElementId,
        targetElementId,
        processInstanceKey);
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
            .setProcessDefinitionVersionTag(targetProcessDefinition.getVersionTag())
            .setElementId(targetElementId)
            .setAgentDefinitionKey(
                resolveAgentDefinitionKey(
                    targetProcessDefinition,
                    targetElementId,
                    agentInstanceKey,
                    processInstanceKey)));
  }

  /**
   * Resolves the agent definition key the migrated agent instance should point at in the target
   * process definition. An agent instance must always belong to an agent definition, so the target
   * element having none is a broken state that the migration preconditions already reject; if it is
   * still reached here it is a bug rather than a rejectable user error.
   */
  private long resolveAgentDefinitionKey(
      final DeployedProcess targetProcessDefinition,
      final String targetElementId,
      final long agentInstanceKey,
      final long processInstanceKey) {
    final var targetKey =
        agentDefinitionState.getAgentDefinitionKey(
            targetProcessDefinition.getKey(), BufferUtil.wrapString(targetElementId));
    if (targetKey == null) {
      throw new SafetyCheckFailedException(
          String.format(
              """
              Expected to migrate agent instance with key '%d' of process instance with key '%d' \
              to target element with id '%s', but that element has no agent definition. \
              An agent instance must always belong to an agent definition. \
              Please report this as a bug""",
              agentInstanceKey, processInstanceKey, targetElementId));
    }
    return targetKey;
  }
}
