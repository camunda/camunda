/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@ExcludeAuthorizationCheck
public final class ProcessInstanceCreationCreateWithResultProcessor
    implements TypedRecordProcessor<ProcessInstanceCreationRecord> {

  private final ProcessInstanceCreationCreateProcessor createProcessor;
  private final MutableElementInstanceState elementInstanceState;
  private final AwaitProcessInstanceResultMetadata awaitResultMetadata =
      new AwaitProcessInstanceResultMetadata();

  public ProcessInstanceCreationCreateWithResultProcessor(
      final ProcessInstanceCreationCreateProcessor createProcessor,
      final MutableElementInstanceState elementInstanceState) {
    this.createProcessor = createProcessor;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceCreationRecord> command) {
    createProcessor.processRecord(command);

    final ArrayProperty<StringValue> fetchVariables = command.getValue().fetchVariables();
    awaitResultMetadata
        .setRequestId(command.getRequestId())
        .setRequestStreamId(command.getRequestStreamId())
        .setFetchVariables(fetchVariables);

    elementInstanceState.setAwaitResultRequestMetadata(
        command.getValue().getProcessInstanceKey(), awaitResultMetadata);
  }
}
