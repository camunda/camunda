/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.cases;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

/**
 * Creates a case for an existing process instance. The command is only written by the engine itself
 * when it creates a root instance of a process that is marked as a case, so no authorization or
 * tenant checks are applied.
 */
@NullMarked
@ExcludeAuthorizationCheck
public final class CaseCreateProcessor implements TypedRecordProcessor<CaseRecord> {

  public static final String PROCESS_INSTANCE_NOT_FOUND_MESSAGE =
      "Expected to create case for process instance with key '%d', but no such process instance was found";

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;
  private final ElementInstanceState elementInstanceState;

  public CaseCreateProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ElementInstanceState elementInstanceState) {
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void processRecord(final TypedRecord<CaseRecord> command) {
    final var caseRecord = command.getValue();
    final long processInstanceKey = caseRecord.getProcessInstanceKey();

    final var processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance == null
        || processInstance.getValue().getBpmnElementType() != BpmnElementType.PROCESS) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          PROCESS_INSTANCE_NOT_FOUND_MESSAGE.formatted(processInstanceKey));
      return;
    }

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), CaseIntent.CREATED, caseRecord);
  }
}
