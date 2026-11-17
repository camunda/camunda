/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Invoked by the primary suspension gate (see {@code Engine#process}) in place of a command's usual
 * {@link TypedRecordProcessor}, whenever the command is classified {@code BUFFER} (see {@code
 * SuspensionAware}) and its target process instance is currently {@code SUSPENDING} or {@code
 * SUSPENDED}.
 *
 * <p>Buffers the command, in FIFO order, as a {@link BufferedCommandRecord}; it is written back to
 * the log verbatim once the process instance is drained during resume. No client response is
 * written here: buffered commands are (or become, via distribution) internal commands and are
 * re-issued once the process instance resumes, at which point the normal command lifecycle produces
 * the response. The buffered record carries the storage ordinal of the process instance.
 */
public final class CommandBufferingBehavior {

  private final KeyGenerator keyGenerator;
  private final Writers writers;
  private final ElementInstanceState elementInstanceState;
  private final SuspensionMetrics suspensionMetrics;

  public CommandBufferingBehavior(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ElementInstanceState elementInstanceState,
      final SuspensionMetrics suspensionMetrics) {
    this.keyGenerator = keyGenerator;
    this.writers = writers;
    this.elementInstanceState = elementInstanceState;
    this.suspensionMetrics = suspensionMetrics;
  }

  /**
   * Buffers a command for the given process instance, to be written when it is resumed. The process
   * instance key is passed in rather than read off the command value, since external {@code
   * JOB}/{@code INCIDENT}/{@code USER_TASK} commands don't carry it on the wire; the buffered
   * record is indexed by this key, so it must be the real instance key for the command to drain on
   * resume.
   */
  public void bufferCommand(
      final long commandKey,
      final ValueType valueType,
      final Intent intent,
      final UnifiedRecordValue commandValue,
      final long processInstanceKey) {
    final String tenantId =
        commandValue instanceof final TenantOwned tenantOwned
            ? tenantOwned.getTenantId()
            : TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    final long processDefinitionKey =
        commandValue instanceof final ProcessInstanceRelated processInstanceRelated
            ? processInstanceRelated.getProcessDefinitionKey()
            : -1;

    final var bufferedCommandRecord =
        new BufferedCommandRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setTenantId(tenantId)
            .setStorageOrdinal(resolveStorageOrdinal(commandValue, processInstanceKey))
            .setCommandKey(commandKey)
            .setValueType(valueType)
            .setIntent(intent)
            .setCommandValue(commandValue);

    final long bufferedCommandKey = keyGenerator.nextKey();
    writers
        .state()
        .appendFollowUpEvent(
            bufferedCommandKey, BufferedCommandIntent.BUFFERED, bufferedCommandRecord);
    suspensionMetrics.commandBuffered();
  }

  /**
   * Returns the ordinal of the process instance, read from its root element instance. The command
   * value is not used first, because clients send JOB, USER_TASK and INCIDENT commands without an
   * ordinal, so they arrive with the default 0 even when the instance has a real ordinal.
   */
  private int resolveStorageOrdinal(
      final UnifiedRecordValue commandValue, final long processInstanceKey) {
    final var root = elementInstanceState.getInstance(processInstanceKey);
    if (root != null) {
      return root.getValue().getStorageOrdinal();
    }
    // The instance is gone, so its ordinal is unknown. Use the ordinal on the command if it has
    // one. Otherwise, use the main index, where these records were written before ordinals.
    return commandValue instanceof final StorageOrdinalRelated related
        ? related.getStorageOrdinal()
        : StorageOrdinalRelated.MAIN_INDEX;
  }
}
