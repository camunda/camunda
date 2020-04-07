/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.activity;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.IOMappingHelper;
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatingHandler;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.List;

public class ActivityElementActivatingHandler<T extends ExecutableActivity>
    extends ElementActivatingHandler<T> {
  private final CatchEventSubscriber catchEventSubscriber;

  public ActivityElementActivatingHandler(
      final CatchEventSubscriber catchEventSubscriber,
      final ExpressionProcessor expressionProcessor) {
    super(expressionProcessor);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  public ActivityElementActivatingHandler(
      final WorkflowInstanceIntent nextState,
      final CatchEventSubscriber catchEventSubscriber,
      final ExpressionProcessor expressionProcessor) {
    super(nextState, expressionProcessor);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  public ActivityElementActivatingHandler(
      final WorkflowInstanceIntent nextState,
      final IOMappingHelper ioMappingHelper,
      final CatchEventSubscriber catchEventSubscriber) {
    super(nextState, ioMappingHelper);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    if (!super.handleState(context)) {
      return false;
    }

    final List<ExecutableCatchEvent> eventSubprocesses = context.getElement().getEvents();
    return eventSubprocesses.isEmpty() || catchEventSubscriber.subscribeToEvents(context);
  }
}
