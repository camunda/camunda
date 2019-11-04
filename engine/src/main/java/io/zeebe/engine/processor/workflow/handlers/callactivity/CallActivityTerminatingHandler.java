/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.callactivity;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCallActivity;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementTerminatingHandler;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class CallActivityTerminatingHandler
    extends ActivityElementTerminatingHandler<ExecutableCallActivity> {

  public CallActivityTerminatingHandler(CatchEventSubscriber catchEventSubscriber) {
    super(null, catchEventSubscriber);
  }

  @Override
  protected boolean handleState(BpmnStepContext<ExecutableCallActivity> context) {
    if (!super.handleState(context)) {
      return false;
    }

    final var calledChildInstanceKey = context.getElementInstance().getCalledChildInstanceKey();
    final var childInstance = context.getElementInstanceState().getInstance(calledChildInstanceKey);

    if (childInstance != null && childInstance.canTerminate()) {
      context
          .getOutput()
          .appendFollowUpEvent(
              calledChildInstanceKey,
              WorkflowInstanceIntent.ELEMENT_TERMINATING,
              childInstance.getValue());

    } else {
      // child instance is already completed or terminated
      transitionTo(context, WorkflowInstanceIntent.ELEMENT_TERMINATED);
    }

    return true;
  }
}
