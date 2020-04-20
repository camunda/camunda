/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.nwe;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.EventOutput;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.WorkflowEngineState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public final class BpmnElementContextImpl implements BpmnElementContext {

  private long elementInstanceKey;

  private WorkflowInstanceRecord recordValue;
  private WorkflowInstanceIntent intent;

  private final BpmnStepContext<?> stepContext;

  public BpmnElementContextImpl(final ZeebeState zeebeState) {
    this(createStepContext(zeebeState));
  }

  private BpmnElementContextImpl(final BpmnStepContext<?> stepContext) {
    this.stepContext = stepContext;
  }

  private static BpmnStepContext<?> createStepContext(final ZeebeState zeebeState) {
    final BpmnStepContext<?> stepContext;
    final var eventOutput =
        new EventOutput(
            new WorkflowEngineState(1, zeebeState.getWorkflowState()),
            zeebeState.getKeyGenerator());
    stepContext = new BpmnStepContext<>(zeebeState.getWorkflowState(), eventOutput);
    return stepContext;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getFlowScopeKey() {
    return recordValue.getFlowScopeKey();
  }

  @Override
  public long getWorkflowInstanceKey() {
    return recordValue.getWorkflowInstanceKey();
  }

  @Override
  public long getWorkflowKey() {
    return recordValue.getWorkflowKey();
  }

  @Override
  public int getWorkflowVersion() {
    return recordValue.getVersion();
  }

  @Override
  public DirectBuffer getBpmnProcessId() {
    return recordValue.getBpmnProcessIdBuffer();
  }

  @Override
  public DirectBuffer getElementId() {
    return recordValue.getElementIdBuffer();
  }

  @Override
  public BpmnElementType getBpmnElementType() {
    return recordValue.getBpmnElementType();
  }

  @Override
  public <T extends ExecutableFlowElement> BpmnStepContext<T> toStepContext() {
    return (BpmnStepContext<T>) stepContext;
  }

  @Override
  public WorkflowInstanceRecord getRecordValue() {
    return recordValue;
  }

  @Override
  public WorkflowInstanceIntent getIntent() {
    return intent;
  }

  @Override
  public BpmnElementContext copy(
      final long elementInstanceKey,
      final WorkflowInstanceRecord recordValue,
      final WorkflowInstanceIntent intent) {

    final var copy = new BpmnElementContextImpl(stepContext);
    copy.elementInstanceKey = elementInstanceKey;
    copy.recordValue = recordValue;
    copy.intent = intent;
    // TODO (saig0): init the step context
    return copy;
  }

  public void init(
      final TypedRecord<WorkflowInstanceRecord> record,
      final WorkflowInstanceIntent intent,
      final ExecutableFlowElement element,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    elementInstanceKey = record.getKey();
    recordValue = record.getValue();
    this.intent = intent;

    stepContext.init(elementInstanceKey, recordValue, intent);
    stepContext.setElement(element);
    stepContext.setStreamWriter(streamWriter);
    stepContext.getOutput().setStreamWriter(streamWriter);

    stepContext.getSideEffect().clear();
    sideEffect.accept(stepContext.getSideEffect());
  }

  @Override
  public String toString() {
    return "{"
        + "intent="
        + intent
        + ", elementId="
        + bufferAsString(getElementId())
        + ", bpmnElementType="
        + getBpmnElementType()
        + ", elementInstanceKey="
        + getElementInstanceKey()
        + ", flowScopeKey="
        + getFlowScopeKey()
        + ", workflowInstanceKey="
        + getWorkflowInstanceKey()
        + ", bpmnProcessId="
        + bufferAsString(getBpmnProcessId())
        + ", workflowVersion="
        + getWorkflowVersion()
        + ", workflowKey="
        + getWorkflowKey()
        + '}';
  }
}
