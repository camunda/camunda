/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.multiinstance;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.BpmnStepHandler;
import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMultiInstanceBody;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.List;
import java.util.function.Function;

public class MultiInstanceBodyTerminatingHandler extends AbstractMultiInstanceBodyHandler {

  private final CatchEventSubscriber catchEventSubscriber;

  public MultiInstanceBodyTerminatingHandler(
      Function<BpmnStep, BpmnStepHandler> innerHandlerLookup,
      CatchEventSubscriber catchEventSubscriber) {
    super(WorkflowInstanceIntent.ELEMENT_TERMINATED, innerHandlerLookup);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<ExecutableMultiInstanceBody> context) {
    return isStateSameAsElementState(context);
  }

  @Override
  protected boolean handleMultiInstanceBody(BpmnStepContext<ExecutableMultiInstanceBody> context) {
    catchEventSubscriber.unsubscribeFromEvents(context);

    final List<ElementInstance> children =
        context.getElementInstanceState().getChildren(context.getElementInstance().getKey());

    for (final ElementInstance child : children) {
      if (child.canTerminate()) {
        context
            .getOutput()
            .appendFollowUpEvent(
                child.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, child.getValue());
      }
    }

    final boolean transitionToTerminated = children.isEmpty();
    return transitionToTerminated;
  }
}
