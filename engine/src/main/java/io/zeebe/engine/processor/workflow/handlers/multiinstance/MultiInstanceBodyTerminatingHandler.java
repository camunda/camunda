/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.multiinstance;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMultiInstanceBody;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.List;
import java.util.function.Consumer;

public final class MultiInstanceBodyTerminatingHandler extends AbstractMultiInstanceBodyHandler {

  private final CatchEventSubscriber catchEventSubscriber;

  public MultiInstanceBodyTerminatingHandler(
      final Consumer<BpmnStepContext<?>> handlerLookup,
      final CatchEventSubscriber catchEventSubscriber,
      final ExpressionProcessor expressionProcessor) {
    super(WorkflowInstanceIntent.ELEMENT_TERMINATED, handlerLookup, expressionProcessor);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  @Override
  protected boolean shouldHandleState(final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    return isStateSameAsElementState(context);
  }

  @Override
  protected boolean handleMultiInstanceBody(
      final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    catchEventSubscriber.unsubscribeFromEvents(context);

    final List<ElementInstance> children =
        context.getElementInstanceState().getChildren(context.getElementInstance().getKey());

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

    // child tokens are not consumed when the flow scope is terminating
    final int zombies =
        context.getElementInstance().getNumberOfActiveTokens() - terminatedChildInstances;
    for (int z = 0; z < zombies; z++) {
      context.getElementInstanceState().consumeToken(context.getKey());
    }

    return terminatedChildInstances == 0;
  }
}
