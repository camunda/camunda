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
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExecutionListenerTransformer {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionListenerTransformer.class);

  public void transform(
      final BaseElement element,
      final ExecutableFlowNode executableFlowNode,
      final Collection<ZeebeExecutionListener> executionListeners,
      final ExpressionLanguage expressionLanguage) {

    if (executionListeners.isEmpty()) {
      // No execution listeners defined, nothing to do
      return;
    }

    final var taskHeaders = transformTaskHeaders(element);

    executionListeners.forEach(
        listener -> {
          try {
            addListenerToFlowNode(listener, executableFlowNode, expressionLanguage, taskHeaders);
          } catch (final Exception e) {
            // This is an additional safety, as our transformers currently require additional
            // safeguards to avoid breaking the partition on replay
            LOGGER.warn(
                """
                Failed to transform execution listener for flow node '{}', \
                caught an unexpected exception: '{}': {}. Ignoring this listener definition.""",
                BufferUtil.bufferAsString(executableFlowNode.getId()),
                e.getClass().getSimpleName(),
                e.getMessage());
          }
        });
  }

  private Map<String, String> transformTaskHeaders(final BaseElement element) {
    final var zeebeTaskHeaders = element.getSingleExtensionElement(ZeebeTaskHeaders.class);
    if (zeebeTaskHeaders == null) {
      return Map.of();
    }
    return zeebeTaskHeaders.getHeaders().stream()
        .filter(this::isValidHeader)
        .collect(Collectors.toMap(ZeebeHeader::getKey, ZeebeHeader::getValue));
  }

  private void addListenerToFlowNode(
      final ZeebeExecutionListener listener,
      final ExecutableFlowNode flowNode,
      final ExpressionLanguage expressionLanguage,
      final Map<String, String> taskHeaders) {
    Either.<Error, ZeebeExecutionListener>right(listener)
        .flatMap(l -> requireNotNull(l, () -> l, "listener"))
        .flatMap(l -> requireNotNull(l, l::getEventType, "eventType"))
        .flatMap(l -> requireNotNull(l, l::getType, "type"))
        .flatMap(l -> requireNotNull(l, l::getRetries, "retries"))
        .ifRightOrLeft(
            ok ->
                flowNode.addListener(
                    listener.getEventType(),
                    expressionLanguage.parseExpression(listener.getType()),
                    expressionLanguage.parseExpression(listener.getRetries()),
                    taskHeaders),
            error ->
                LOGGER.debug(
                    """
                    Failed to transform execution listener for flow node '%s', \
                    because %s. Ignoring this listener definition."""
                        .formatted(BufferUtil.bufferAsString(flowNode.getId()), error.reason())));
  }

  private Either<Error, ZeebeExecutionListener> requireNotNull(
      final ZeebeExecutionListener listener,
      final Supplier<?> attributeSupplier,
      final String attributeName) {
    if (attributeSupplier.get() == null) {
      return Either.left(new Error("'%s' is null".formatted(attributeName)));
    }
    return Either.right(listener);
  }

  public boolean isValidHeader(final ZeebeHeader header) {
    return header != null
        && header.getValue() != null
        && !header.getValue().isEmpty()
        && header.getKey() != null
        && !header.getKey().isEmpty();
  }

  private record Error(String reason) {}
}
