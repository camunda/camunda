/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnAdHocSubProcessBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class AdHocSubProcessInstructionCompleteProcessor
    implements TypedRecordProcessor<AdHocSubProcessInstructionRecord> {

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final BpmnAdHocSubProcessBehavior adHocSubProcessBehavior;

  public AdHocSubProcessInstructionCompleteProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final BpmnBehaviors bpmnBehaviors) {
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    elementInstanceState = processingState.getElementInstanceState();
    adHocSubProcessBehavior = bpmnBehaviors.adHocSubProcessBehavior();
  }

  @Override
  public void processRecord(final TypedRecord<AdHocSubProcessInstructionRecord> record) {
    final var recordValue = record.getValue();
    final var ahspElementInstance =
        elementInstanceState.getInstance(recordValue.getAdHocSubProcessInstanceKey());

    adHocSubProcessBehavior.completionConditionFulfilled(
        createBpmnElementContext(ahspElementInstance), recordValue.isCancelRemainingInstances());

    stateWriter.appendFollowUpEvent(
        recordValue.getAdHocSubProcessInstanceKey(),
        AdHocSubProcessInstructionIntent.COMPLETED,
        record.getValue());
  }

  private BpmnElementContext createBpmnElementContext(final ElementInstance elementInstance) {
    final var context = new BpmnElementContextImpl();
    context.init(elementInstance.getKey(), elementInstance.getValue(), elementInstance.getState());
    return context;
  }
}
