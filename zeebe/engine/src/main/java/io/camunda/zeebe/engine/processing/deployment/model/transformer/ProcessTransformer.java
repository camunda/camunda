/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.ExecutionListenerTransformer;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListeners;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.Optional;

public final class ProcessTransformer implements ModelElementTransformer<Process> {

  private final ExecutionListenerTransformer executionListenerTransformer =
      new ExecutionListenerTransformer();

  @Override
  public Class<Process> getType() {
    return Process.class;
  }

  @Override
  public void transform(final Process element, final TransformContext context) {

    final String id = element.getId();
    final ExecutableProcess process = new ExecutableProcess(id);

    process.setElementType(
        BpmnElementType.bpmnElementTypeFor(element.getElementType().getTypeName()));

    context.addProcess(process);
    context.setCurrentProcess(process);
    transformExecutionListeners(element, process, context.getExpressionLanguage());
  }

  private void transformExecutionListeners(
      final Process element,
      final ExecutableProcess flowNode,
      final ExpressionLanguage expressionLanguage) {

    Optional.ofNullable(element.getSingleExtensionElement(ZeebeExecutionListeners.class))
        .ifPresent(
            listeners ->
                executionListenerTransformer.transform(
                    element, flowNode, listeners.getExecutionListeners(), expressionLanguage));
  }
}
