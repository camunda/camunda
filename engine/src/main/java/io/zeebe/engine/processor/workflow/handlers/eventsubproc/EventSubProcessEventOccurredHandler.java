/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.eventsubproc;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.List;
import org.agrona.DirectBuffer;

public final class EventSubProcessEventOccurredHandler<T extends ExecutableStartEvent>
    extends EventOccurredHandler<T> {
  private final WorkflowInstanceRecord containerRecord = new WorkflowInstanceRecord();

  public EventSubProcessEventOccurredHandler() {
    super(null);
  }

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    final WorkflowInstanceRecord event = context.getValue();

    final long scopeKey = context.getValue().getFlowScopeKey();
    final EventTrigger triggeredEvent = getTriggeredEvent(context, scopeKey);
    if (triggeredEvent == null) {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error("No triggered event for key {}", context.getKey());
      return false;
    } else if (context.getFlowScopeInstance().getInterruptingEventKey() != -1) {
      return false;
    }

    final ExecutableStartEvent startEvent = context.getElement();
    prepareActivateContainer(context, event);

    long interruptingKey = -1;
    if (startEvent.interrupting()) {
      interruptingKey = handleInterrupting(context, triggeredEvent, scopeKey);
    } else {
      processEventTrigger(context, context.getKey(), context.getKey(), triggeredEvent);
      context
          .getOutput()
          .appendFollowUpEvent(
              context.getKey(), WorkflowInstanceIntent.ELEMENT_ACTIVATING, containerRecord);
    }

    final ElementInstance scope = context.getElementInstanceState().getInstance(scopeKey);
    scope.spawnToken();
    scope.setInterruptingEventKey(interruptingKey);
    context.getElementInstanceState().updateInstance(scope);

    return true;
  }

  @Override
  protected boolean shouldHandleState(final BpmnStepContext<T> context) {
    return isElementActive(context.getFlowScopeInstance());
  }

  private long handleInterrupting(
      final BpmnStepContext<T> context, final EventTrigger triggeredEvent, final long scopeKey) {
    final boolean waitForTermination = interruptParentScope(context);

    if (waitForTermination) {
      return deferEvent(context, context.getKey(), scopeKey, containerRecord, triggeredEvent);
    } else {
      final long eventKey =
          context
              .getOutput()
              .appendNewEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING, containerRecord);
      processEventTrigger(context, context.getKey(), eventKey, triggeredEvent);
      return eventKey;
    }
  }

  private boolean interruptParentScope(final BpmnStepContext<T> context) {
    final long scopeKey = context.getValue().getFlowScopeKey();
    final List<ElementInstance> children = context.getElementInstanceState().getChildren(scopeKey);

    int terminatedChildInstances = 0;

    for (final ElementInstance child : children) {
      if (child.canTerminate()) {
        context
            .getOutput()
            .appendFollowUpEvent(
                child.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, child.getValue());

        terminatedChildInstances += 1;
      }
    }

    // consume all other active tokens (e.g. tokens waiting at a joining gateway)
    final int zombies =
        context.getFlowScopeInstance().getNumberOfActiveTokens() - terminatedChildInstances;
    for (int z = 0; z < zombies; z++) {
      context.getElementInstanceState().consumeToken(scopeKey);
    }

    return terminatedChildInstances > 0;
  }

  private void prepareActivateContainer(
      final BpmnStepContext<T> context, final WorkflowInstanceRecord event) {
    final DirectBuffer subprocessId = context.getElement().getEventSubProcess();
    containerRecord.reset();
    containerRecord
        .setElementId(subprocessId)
        .setBpmnElementType(BpmnElementType.SUB_PROCESS)
        .setBpmnProcessId(event.getBpmnProcessId())
        .setWorkflowKey(event.getWorkflowKey())
        .setVersion(event.getVersion())
        .setWorkflowInstanceKey(event.getWorkflowInstanceKey())
        .setFlowScopeKey(event.getFlowScopeKey());
  }
}
