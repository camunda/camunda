/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.historydeletion;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

@ExcludeAuthorizationCheck
public class ProcessInstanceDeleteProcessor implements TypedRecordProcessor<ProcessInstanceRecord> {

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  public ProcessInstanceDeleteProcessor(
      final StateWriter stateWriter, final KeyGenerator keyGenerator) {
    this.stateWriter = stateWriter;
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> record) {
    // TODO I'm using the batch operation reference as key here. That's dirty, but there is no easy
    //  way to access it in the applier. For a POC this is good enough.
    stateWriter.appendFollowUpEvent(
        record.getBatchOperationReference(), ProcessInstanceIntent.DELETING, record.getValue());
  }
}
