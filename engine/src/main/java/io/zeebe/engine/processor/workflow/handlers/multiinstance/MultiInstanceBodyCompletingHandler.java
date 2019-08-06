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
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.function.Function;

public class MultiInstanceBodyCompletingHandler extends AbstractMultiInstanceBodyHandler {

  private final CatchEventSubscriber catchEventSubscriber;

  public MultiInstanceBodyCompletingHandler(
      Function<BpmnStep, BpmnStepHandler> innerHandlerLookup,
      CatchEventSubscriber catchEventSubscriber) {
    super(WorkflowInstanceIntent.ELEMENT_COMPLETED, innerHandlerLookup);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  @Override
  protected boolean handleMultiInstanceBody(BpmnStepContext<ExecutableMultiInstanceBody> context) {
    catchEventSubscriber.unsubscribeFromEvents(context);
    return true;
  }
}
