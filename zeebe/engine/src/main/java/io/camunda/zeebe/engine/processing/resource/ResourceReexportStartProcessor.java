/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceReexportRecord;
import io.camunda.zeebe.protocol.record.intent.ResourceReexportIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@ExcludeAuthorizationCheck
public class ResourceReexportStartProcessor
    implements TypedRecordProcessor<ResourceReexportRecord> {

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;

  public ResourceReexportStartProcessor(final Writers writers) {
    stateWriter = writers.state();
    commandWriter = writers.command();
  }

  @Override
  public void processRecord(final TypedRecord<ResourceReexportRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getKey(), ResourceReexportIntent.STARTED, command.getValue());
    commandWriter.appendFollowUpCommand(
        command.getKey(), ResourceReexportIntent.REEXPORT, new ResourceReexportRecord());
  }
}
