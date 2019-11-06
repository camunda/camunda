/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.container;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatedHandler;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.List;

public class ContainerElementActivatedHandler<T extends ExecutableFlowElementContainer>
    extends ElementActivatedHandler<T> {
  private final WorkflowState workflowState;

  public ContainerElementActivatedHandler(final WorkflowState workflowState) {
    this(null, workflowState);
  }

  public ContainerElementActivatedHandler(
      final WorkflowInstanceIntent nextState, final WorkflowState workflowState) {
    super(nextState);
    this.workflowState = workflowState;
  }

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    if (!super.handleState(context)) {
      return false;
    }

    final ExecutableFlowElementContainer element = context.getElement();
    final var noneStartEvent = element.getNoneStartEvent();
    final var deferredRecord = getDeferredRecord(context);

    if (deferredRecord != null) {
      // workflow instance is created by an event
      // - the corresponding start event is deferred
      publishDeferredRecord(context, deferredRecord);

    } else if (noneStartEvent != null) {
      // workflow instance is create via command or call activity,
      // or subprocess is activated
      // - activate none none start event
      activateStartEvent(context, noneStartEvent);

    } else {
      // event subprocess is activated
      // - activate the corresponding start event
      final var startEvent = element.getStartEvents().get(0);
      activateStartEvent(context, startEvent);
    }

    context
        .getStateDb()
        .getElementInstanceState()
        .spawnToken(context.getElementInstance().getKey());

    return true;
  }

  private void activateStartEvent(
      final BpmnStepContext<T> context, final ExecutableStartEvent startEvent) {
    final WorkflowInstanceRecord value = context.getValue();

    value.setElementId(startEvent.getId());
    value.setBpmnElementType(startEvent.getElementType());
    value.setFlowScopeKey(context.getKey());
    context.getOutput().appendNewEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING, value);
  }

  private IndexedRecord getDeferredRecord(final BpmnStepContext<T> context) {
    final long scopeKey = context.getKey();
    final List<IndexedRecord> deferredRecords =
        context.getElementInstanceState().getDeferredRecords(scopeKey);

    if (deferredRecords.size() > 1) {
      throw new IllegalStateException(
          String.format(
              "Expected one deferred token at %s but found %d.",
              context.getElementInstance(), deferredRecords.size()));
    }

    if (deferredRecords.isEmpty()) {
      return null;

    } else {
      final IndexedRecord deferredRecord = deferredRecords.get(0);
      workflowState
          .getElementInstanceState()
          .removeStoredRecord(scopeKey, deferredRecord.getKey(), Purpose.DEFERRED);

      return deferredRecord;
    }
  }

  private void publishDeferredRecord(
      final BpmnStepContext<T> context, final IndexedRecord deferredRecord) {
    context
        .getOutput()
        .appendFollowUpEvent(
            deferredRecord.getKey(), deferredRecord.getState(), deferredRecord.getValue());
  }
}
