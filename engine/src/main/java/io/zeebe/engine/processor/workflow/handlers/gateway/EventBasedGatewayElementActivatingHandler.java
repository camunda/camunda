/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.gateway;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableEventBasedGateway;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.IOMappingHelper;
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatingHandler;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class EventBasedGatewayElementActivatingHandler<T extends ExecutableEventBasedGateway>
    extends ElementActivatingHandler<T> {
  private final CatchEventSubscriber catchEventSubscriber;

  public EventBasedGatewayElementActivatingHandler(CatchEventSubscriber catchEventSubscriber) {
    this(WorkflowInstanceIntent.ELEMENT_ACTIVATED, catchEventSubscriber);
  }

  public EventBasedGatewayElementActivatingHandler(
      WorkflowInstanceIntent nextState, CatchEventSubscriber catchEventSubscriber) {
    super(nextState);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  public EventBasedGatewayElementActivatingHandler(
      WorkflowInstanceIntent nextState,
      IOMappingHelper ioMappingHelper,
      CatchEventSubscriber catchEventSubscriber) {
    super(nextState, ioMappingHelper);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    return super.handleState(context) && catchEventSubscriber.subscribeToEvents(context);
  }
}
