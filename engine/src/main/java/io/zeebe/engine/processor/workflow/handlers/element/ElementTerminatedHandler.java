/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.element;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.WorkflowInstanceLifecycle;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.handlers.AbstractTerminalStateHandler;
import io.zeebe.engine.processor.workflow.handlers.IncidentResolver;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.Optional;

/**
 * Once terminated, consumes its token, and if it is the last active element in its flow scope, will
 * terminate the flow scope. If it has no flow scope (e.g. the process), does nothing.
 *
 * @param <T>
 */
public class ElementTerminatedHandler<T extends ExecutableFlowNode>
    extends AbstractTerminalStateHandler<T> {
  private final IncidentResolver incidentResolver;

  public ElementTerminatedHandler(final IncidentResolver incidentResolver) {
    super();
    this.incidentResolver = incidentResolver;
  }

  @Override
  protected boolean shouldHandleState(final BpmnStepContext<T> context) {
    return super.shouldHandleState(context) && isStateSameAsElementState(context);
  }

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
    final boolean isScopeTerminating =
        flowScopeInstance != null
            && WorkflowInstanceLifecycle.canTransition(
                flowScopeInstance.getState(), WorkflowInstanceIntent.ELEMENT_TERMINATED);

    incidentResolver.resolveIncidents(context);

    if (isScopeTerminating && isLastActiveExecutionPathInScope(context)) {
      context
          .getOutput()
          .appendFollowUpEvent(
              flowScopeInstance.getKey(),
              WorkflowInstanceIntent.ELEMENT_TERMINATED,
              flowScopeInstance.getValue());
    } else if (wasInterrupted(flowScopeInstance)) {
      publishInterruptingEventSubproc(context, flowScopeInstance);
    }

    return super.handleState(context);
  }

  private void publishInterruptingEventSubproc(
      final BpmnStepContext<T> context, final ElementInstance flowScopeInstance) {
    final Optional<IndexedRecord> eventSubprocOptional =
        context.getElementInstanceState().getDeferredRecords(flowScopeInstance.getKey()).stream()
            .filter(r -> r.getKey() == context.getFlowScopeInstance().getInterruptingEventKey())
            .findFirst();

    if (eventSubprocOptional.isPresent()) {
      final IndexedRecord eventSubproc = eventSubprocOptional.get();

      eventSubproc.getValue().setFlowScopeKey(flowScopeInstance.getKey());
      context
          .getOutput()
          .appendFollowUpEvent(
              eventSubproc.getKey(), eventSubproc.getState(), eventSubproc.getValue());
    }
  }

  private boolean wasInterrupted(final ElementInstance flowScopeInstance) {
    return flowScopeInstance != null
        && flowScopeInstance.getNumberOfActiveTokens() == 2
        && flowScopeInstance.isInterrupted()
        && flowScopeInstance.isActive();
  }
}
