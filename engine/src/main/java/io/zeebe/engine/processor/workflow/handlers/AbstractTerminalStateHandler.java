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
  public void handle(BpmnStepContext<T> context) {
    super.handle(context);

    // currently we always cleanup whether or not the state was successfully handled, which is fine
    // as by convention we shouldn't perform anything in terminal state which might fail
    context.getStateDb().getEventScopeInstanceState().deleteInstance(context.getKey());
    context.getElementInstanceState().removeInstance(context.getKey());
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
    if (flowScopeInstance != null) {
      flowScopeInstance.consumeToken();
      context.getStateDb().getElementInstanceState().updateInstance(flowScopeInstance);
    }

    return true;
  }

  protected void publishDeferredRecords(BpmnStepContext<T> context) {
    final List<IndexedRecord> deferredRecords =
        context.getElementInstanceState().getDeferredRecords(context.getKey());
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();

    for (final IndexedRecord record : deferredRecords) {
      record.getValue().setFlowScopeKey(flowScopeInstance.getKey());
      context
          .getOutput()
          .appendFollowUpEvent(record.getKey(), record.getState(), record.getValue());
      flowScopeInstance.spawnToken();
    }
    context.getStateDb().getElementInstanceState().updateInstance(flowScopeInstance);
  }

  protected boolean isLastActiveExecutionPathInScope(BpmnStepContext<T> context) {
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();

    if (flowScopeInstance == null) {
      return false;
    }

    final int activePaths = flowScopeInstance.getNumberOfActiveTokens();
    assert activePaths >= 0 : "number of active paths should never be negative";

    return activePaths == 1;
  }
}
