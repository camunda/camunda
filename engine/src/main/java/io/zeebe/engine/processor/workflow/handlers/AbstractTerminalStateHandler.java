/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.IndexedRecord;
import java.util.List;

/**
 * Primarily to clean up once we reach final state.
 *
 * @param <T>
 */
public class AbstractTerminalStateHandler<T extends ExecutableFlowElement>
    extends AbstractHandler<T> {
  public AbstractTerminalStateHandler() {
    super(null);
  }

  @Override
  public void handle(final BpmnStepContext<T> context) {
    super.handle(context);

    // currently we always cleanup whether or not the state was successfully handled, which is fine
    // as by convention we shouldn't perform anything in terminal state which might fail
    context.getStateDb().getEventScopeInstanceState().deleteInstance(context.getKey());
    context.getElementInstanceState().removeInstance(context.getKey());
  }

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
    if (flowScopeInstance != null) {
      context.getStateDb().getElementInstanceState().consumeToken(flowScopeInstance.getKey());
    }

    return true;
  }

  protected void publishDeferredRecords(final BpmnStepContext<T> context) {
    final List<IndexedRecord> deferredRecords =
        context.getElementInstanceState().getDeferredRecords(context.getKey());
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();

    for (final IndexedRecord record : deferredRecords) {
      record.getValue().setFlowScopeKey(flowScopeInstance.getKey());
      context
          .getOutput()
          .appendFollowUpEvent(record.getKey(), record.getState(), record.getValue());

      context.getStateDb().getElementInstanceState().spawnToken(flowScopeInstance.getKey());
    }
  }

  protected boolean isLastActiveExecutionPathInScope(final BpmnStepContext<T> context) {
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();

    if (flowScopeInstance == null) {
      return false;
    }

    final int activePaths = flowScopeInstance.getNumberOfActiveTokens();
    if (activePaths < 0) {
      throw new IllegalStateException(
          String.format(
              "Expected number of active paths to be positive but got %d for instance %s",
              activePaths, flowScopeInstance));
    }

    return activePaths == 1;
  }
}
