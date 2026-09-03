/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Finalizes local deletion of {@link PersistedProcessState#DRAINING} definitions. A definition is
 * finalized either when
 *
 * <ul>
 *   <li>its last active instance completes or terminates ({@link #finalizeDeletionIfDraining})
 *   <li>the last instance is migrated away
 *   <li>for a definition inherited by a freshly bootstrapped partition that holds none of its
 *       instances, when that partition finishes bootstrapping ({@link
 *       #finalizeDrainingDefinitionsWithoutLocalInstances})
 * </ul>
 *
 * <p>Banned instances are excluded from the active-instance check, as they never complete or
 * terminate.
 */
public final class BpmnProcessDeletionBehavior {

  private final ProcessState processState;
  private final ElementInstanceState elementInstanceState;
  private final BannedInstanceState bannedInstanceState;
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;

  public BpmnProcessDeletionBehavior(
      final ProcessState processState,
      final ElementInstanceState elementInstanceState,
      final BannedInstanceState bannedInstanceState,
      final TypedCommandWriter commandWriter,
      final StateWriter stateWriter,
      final KeyGenerator keyGenerator) {
    this.processState = processState;
    this.elementInstanceState = elementInstanceState;
    this.bannedInstanceState = bannedInstanceState;
    this.commandWriter = commandWriter;
    this.stateWriter = stateWriter;
    this.keyGenerator = keyGenerator;
  }

  /**
   * Reports that this partition has finished draining the completed/terminated instance's process
   * definition, if it is draining and no active instances remain. No-op otherwise.
   */
  public void finalizeDeletionIfDraining(final BpmnElementContext context) {
    final var intent = context.getIntent();
    if (intent != ProcessInstanceIntent.ELEMENT_COMPLETED
        && intent != ProcessInstanceIntent.ELEMENT_TERMINATED) {
      // only a completed or terminated instance frees the definition to drain
      return;
    }

    finalizeDeletionIfDraining(context.getProcessDefinitionKey(), context.getTenantId());
  }

  public void finalizeDeletionIfDraining(final long processDefinitionKey, final String tenantId) {
    final var process = processState.getProcessByKeyAndTenant(processDefinitionKey, tenantId);
    if (process == null || process.getState() != PersistedProcessState.DRAINING) {
      return;
    }

    final var bannedInstances = bannedInstanceState.getBannedProcessInstanceKeys();
    if (elementInstanceState.hasActiveProcessInstances(process.getKey(), bannedInstances)) {
      // the definition is still draining; other instances are still running
      return;
    }

    finalizeDrain(process.getPersistedProcess());
  }

  /**
   * Reconciles every {@code DRAINING} definition on this partition that has no local active
   * instances, finalizing its drain. A freshly bootstrapped partition inherits {@code DRAINING}
   * definitions through the snapshot but holds none of their instances.
   */
  public void finalizeDrainingDefinitionsWithoutLocalInstances() {
    final var bannedInstances = bannedInstanceState.getBannedProcessInstanceKeys();
    processState.forEachProcess(
        null,
        process -> {
          if (process.getState() == PersistedProcessState.DRAINING
              && !elementInstanceState.hasActiveProcessInstances(
                  process.getKey(), bannedInstances)) {
            finalizeDrain(process);
          }
          return true;
        });
  }

  /**
   * Removes the definition from local state ({@link ProcessIntent#DELETING} / {@link
   * ProcessIntent#DELETED}) and reports the drain to the deployment partition ({@link
   * ProcessIntent#DELETE_COMPLETE}). Callers must ensure the definition is {@code DRAINING} and has
   * no remaining active instances on this partition.
   */
  private void finalizeDrain(final PersistedProcess process) {
    final var processRecord =
        new ProcessRecord()
            .setBpmnProcessId(process.getBpmnProcessId())
            .setVersion(process.getVersion())
            .setVersionTag(process.getVersionTag())
            .setKey(process.getKey())
            .setResourceName(process.getResourceName())
            .setTenantId(process.getTenantId())
            .setDeploymentKey(process.getDeploymentKey());
    finalizeDeletion(processRecord);
  }

  public void finalizeDeletion(final ProcessRecord processRecord) {
    // the locally-minted key identifies the reporting partition to ProcessDeleteCompleteProcessor
    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, ProcessIntent.DELETING, processRecord);
    stateWriter.appendFollowUpEvent(key, ProcessIntent.DELETED, processRecord);
    commandWriter.appendFollowUpCommand(key, ProcessIntent.DELETE_COMPLETE, processRecord);
  }
}
