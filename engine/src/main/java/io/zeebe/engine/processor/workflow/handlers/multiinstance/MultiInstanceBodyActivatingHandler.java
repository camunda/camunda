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
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public final class MultiInstanceBodyActivatingHandler extends AbstractMultiInstanceBodyHandler {

  private final CatchEventSubscriber catchEventSubscriber;

  public MultiInstanceBodyActivatingHandler(
      final Consumer<BpmnStepContext<?>> innerHandler,
      final CatchEventSubscriber catchEventSubscriber,
      final ExpressionProcessor expressionProcessor) {
    super(WorkflowInstanceIntent.ELEMENT_ACTIVATED, innerHandler, expressionProcessor);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  @Override
  protected boolean handleMultiInstanceBody(
      final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    final Optional<List<DirectBuffer>> results = readInputCollectionVariable(context);
    if (results.isEmpty()) {
      return false;
    }
    catchEventSubscriber.subscribeToEvents(context);
    return true;
  }
}
