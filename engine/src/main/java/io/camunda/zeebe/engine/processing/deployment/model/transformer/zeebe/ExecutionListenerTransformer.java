/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListeners;
import io.camunda.zeebe.protocol.record.value.ExecutionListenerEventType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public final class ExecutionListenerTransformer {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  public void transform(
      final FlowNode element,
      final ExecutableFlowNode executableFlowNode,
      final ZeebeExecutionListeners executionListeners,
      final ExpressionLanguage expressionLanguage) {

    filterAndLogInvalidListeners(
            element, executionListeners.getExecutionListeners(), executableFlowNode)
        .forEach(
            listener -> addListenerToFlowNode(listener, executableFlowNode, expressionLanguage));
  }

  private List<ZeebeExecutionListener> filterAndLogInvalidListeners(
      final FlowNode element,
      final Collection<ZeebeExecutionListener> listeners,
      final ExecutableFlowNode executableFlowNode) {
    final var validListeners =
        listeners.stream().filter(this::isValidExecutionListener).collect(Collectors.toList());

    if (validListeners.size() < listeners.size()) {
      final List<ZeebeExecutionListener> invalidListeners = new ArrayList<>(listeners);
      invalidListeners.removeAll(validListeners);
      LOG.warn(
          "Ignoring invalid execution listeners {} defined for '{}' element with id='{}'.",
          invalidListeners,
          executableFlowNode.getElementType(),
          element.getId());
    }

    return validListeners;
  }

  private void addListenerToFlowNode(
      final ZeebeExecutionListener listener,
      final ExecutableFlowNode flowNode,
      final ExpressionLanguage expressionLanguage) {

    flowNode.addListener(
        fromZeebeExecutionListenerEventType(listener.getEventType()),
        expressionLanguage.parseExpression(listener.getType()),
        expressionLanguage.parseExpression(listener.getRetries()));
  }

  private ExecutionListenerEventType fromZeebeExecutionListenerEventType(
      final ZeebeExecutionListenerEventType eventType) {
    return switch (eventType) {
      case ZeebeExecutionListenerEventType.start -> ExecutionListenerEventType.START;
      case ZeebeExecutionListenerEventType.end -> ExecutionListenerEventType.END;
    };
  }

  public boolean isValidExecutionListener(final ZeebeExecutionListener listener) {
    return listener != null
        && listener.getEventType() != null
        && StringUtils.isNotBlank(listener.getType())
        && StringUtils.isNotBlank(listener.getRetries());
  }
}
