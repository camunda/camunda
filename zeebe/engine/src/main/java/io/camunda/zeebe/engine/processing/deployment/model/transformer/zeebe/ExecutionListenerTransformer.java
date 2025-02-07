/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExecutionListenerTransformer {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionListenerTransformer.class);

  public void transform(
      final ExecutableFlowNode executableFlowNode,
      final Collection<ZeebeExecutionListener> executionListeners,
      final ExpressionLanguage expressionLanguage) {

    executionListeners.forEach(
        listener -> addListenerToFlowNode(listener, executableFlowNode, expressionLanguage));
  }

  private void addListenerToFlowNode(
      final ZeebeExecutionListener listener,
      final ExecutableFlowNode flowNode,
      final ExpressionLanguage expressionLanguage) {

    try {
      flowNode.addListener(
          listener.getEventType(),
          expressionLanguage.parseExpression(listener.getType()),
          expressionLanguage.parseExpression(listener.getRetries()));
    } catch (final Exception e) {
      LOGGER.warn(
          "Failed to transform execution listener for flow node '{}', caught an exception: '{}: {}. Ignoring this listener definition.'",
          BufferUtil.bufferAsString(flowNode.getId()),
          e.getClass().getSimpleName(),
          e.getMessage());
    }
  }
}
