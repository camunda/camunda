/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.container;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.EventOutput;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementTerminatingHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.List;

public final class ContainerElementTerminatingHandler<T extends ExecutableFlowElementContainer>
    extends ActivityElementTerminatingHandler<T> {

  public ContainerElementTerminatingHandler(final CatchEventSubscriber catchEventSubscriber) {
    this(WorkflowInstanceIntent.ELEMENT_TERMINATED, catchEventSubscriber);
  }

  public ContainerElementTerminatingHandler(
      final WorkflowInstanceIntent nextState, final CatchEventSubscriber catchEventSubscriber) {
    super(nextState, catchEventSubscriber);
  }

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    if (!super.handleState(context)) {
      return false;
    }

    final ElementInstance elementInstance = context.getElementInstance();
    final EventOutput output = context.getOutput();
    final ElementInstanceState elementInstanceState = context.getElementInstanceState();

    final List<ElementInstance> children =
        elementInstanceState.getChildren(elementInstance.getKey());

    int terminatedChildInstances = 0;

    for (final ElementInstance child : children) {
      if (child.canTerminate()) {
        output.appendFollowUpEvent(
            child.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, child.getValue());

        terminatedChildInstances += 1;
      }
    }

    // child tokens are not consumed when the flow scope is terminating
    final int zombies =
        context.getElementInstance().getNumberOfActiveTokens() - terminatedChildInstances;
    for (int z = 0; z < zombies; z++) {
      context.getElementInstanceState().consumeToken(context.getKey());
    }

    return terminatedChildInstances == 0;
  }
}
