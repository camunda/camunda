/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Invoked by the primary suspension gate (see {@code Engine#process}) in place of a command's usual
 * {@link TypedRecordProcessor}, whenever the command is classified {@code BUFFER} (see {@code
 * SuspensionAware}) and its target process instance is currently {@code SUSPENDED}.
 *
 * <p>Buffers the command, in FIFO order, as a {@link BufferedCommandRecord}; it is written back to
 * the log verbatim once the process instance is drained during resume. No client response is
 * written here: buffered commands are (or become, via distribution) internal commands and are
 * re-issued once the process instance resumes, at which point the normal command lifecycle produces
 * the response.
 */
public final class CommandBufferingBehavior {

  private final KeyGenerator keyGenerator;
  private final Writers writers;

  public CommandBufferingBehavior(final KeyGenerator keyGenerator, final Writers writers) {
    this.keyGenerator = keyGenerator;
    this.writers = writers;
  }

  /**
   * Buffers the command for the given process instance. The key is resolved by the gate (see {@code
   * SuspensionCheck}) rather than read off the command value, since external {@code JOB}/{@code
   * INCIDENT}/{@code USER_TASK} commands don't carry it on the wire; the buffered record is indexed
   * by this key, so it must be the real instance key for the command to drain on resume.
   */
  public void bufferCommand(final TypedRecord<?> command, final long processInstanceKey) {
    final var commandValue = command.getValue();
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
            .setCommandKey(command.getKey())
            .setValueType(command.getValueType())
            .setIntent(command.getIntent())
            .setCommandValue(commandValue);

    final long bufferedCommandKey = keyGenerator.nextKey();
    writers
        .state()
        .appendFollowUpEvent(
            bufferedCommandKey, BufferedCommandIntent.BUFFERED, bufferedCommandRecord);
  }
}
