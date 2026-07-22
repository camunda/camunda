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
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Finalizes local deletion of a {@link PersistedProcessState#DRAINING} definition once its last
 * active instance completes or terminates. On this partition it appends the ordinary {@link
 * ProcessIntent#DELETING}/{@link ProcessIntent#DELETED} follow-up events (physically removing the
 * definition locally, reusing the existing appliers), then emits a {@link
 * ProcessIntent#DELETE_COMPLETE} command so {@code ProcessDeleteCompleteProcessor} can aggregate
 * the per-partition reports cluster-wide.
 *
 * <p>Known limitation: {@link ElementInstanceState#hasActiveProcessInstances} excludes banned
 * instances, which never complete/terminate, so a definition whose last instance is banned stays
 * {@code DRAINING} until that instance is resolved.
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
    final var process =
        processState.getProcessByKeyAndTenant(
            context.getProcessDefinitionKey(), context.getTenantId());
    if (process == null || process.getState() != PersistedProcessState.DRAINING) {
      return;
    }

    final var bannedInstances = bannedInstanceState.getBannedProcessInstanceKeys();
    if (elementInstanceState.hasActiveProcessInstances(process.getKey(), bannedInstances)) {
      // the definition is still draining; other instances are still running
      return;
    }

    final var processRecord =
        new ProcessRecord()
            .setBpmnProcessId(process.getBpmnProcessId())
            .setVersion(process.getVersion())
            .setVersionTag(process.getVersionTag())
            .setKey(process.getKey())
            .setResourceName(process.getResourceName())
            .setTenantId(process.getTenantId())
            .setDeploymentKey(process.getDeploymentKey());
    // the locally-minted key identifies the reporting partition to ProcessDeleteCompleteProcessor
    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, ProcessIntent.DELETING, processRecord);
    stateWriter.appendFollowUpEvent(key, ProcessIntent.DELETED, processRecord);
    commandWriter.appendFollowUpCommand(key, ProcessIntent.DELETE_COMPLETE, processRecord);
  }
}
