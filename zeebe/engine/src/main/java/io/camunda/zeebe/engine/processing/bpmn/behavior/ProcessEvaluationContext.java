/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ProcessEvaluationContext implements ScopedEvaluationContext {

  private final long scopeKey;
  private final Supplier<BpmnStateBehavior> stateBehaviorSupplier;

  public ProcessEvaluationContext(final Supplier<BpmnStateBehavior> stateBehaviorSupplier) {
    this(stateBehaviorSupplier, 0);
  }

  public ProcessEvaluationContext(
      final Supplier<BpmnStateBehavior> stateBehaviorSupplier, final long scopeKey) {
    this.scopeKey = scopeKey;
    this.stateBehaviorSupplier = stateBehaviorSupplier;
  }

  @Override
  public ScopedEvaluationContext scoped(final long scopeKey) {
    return new ProcessEvaluationContext(stateBehaviorSupplier, scopeKey);
  }

  @Override
  public Object getVariable(final String variableName) {
    if (variableName.equals("elements")) {
      return getElements();
    }

    return null;
  }

  @Override
  public Stream<String> getVariables() {
    return Stream.of("elements");
  }

  private Object getElements() {
    final BpmnStateBehavior stateBehavior = stateBehaviorSupplier.get();

    final ElementInstance elementInstance = stateBehavior.getElementInstance(scopeKey);

    if (elementInstance != null) {
      final long processDefinitionKey = elementInstance.getValue().getProcessDefinitionKey();
      final String tenantId = elementInstance.getValue().getTenantId();

      return stateBehavior
          .getProcess(processDefinitionKey, tenantId)
          .map(DeployedProcess::getProcess)
          .map(
              process ->
                  process.getFlowElements().stream()
                      .map(ProcessEvaluationContext::getElement)
                      .toList())
          .orElse(List.of());
    }

    return null;
  }

  private static Map<String, Object> getElement(final AbstractFlowElement flowElement) {
    final Map<String, Object> context = new HashMap<>();
    context.put("id", BufferUtil.bufferAsString(flowElement.getId()));
    context.put(
        "name",
        Optional.ofNullable(flowElement.getName())
            .filter(buffer -> buffer.capacity() > 0)
            .map(BufferUtil::bufferAsString)
            .orElse(""));
    context.put("type", flowElement.getElementType().name());
    context.put(
        "documentation",
        Optional.ofNullable(flowElement.getDocumentation())
            .filter(buffer -> buffer.capacity() > 0)
            .map(BufferUtil::bufferAsString)
            .orElse(""));

    if (flowElement instanceof final ExecutableAdHocSubProcess adHocSubProcess) {
      context.put(
          "adHocActivities",
          adHocSubProcess.getAdHocActivitiesById().values().stream()
              .map(ProcessEvaluationContext::getElement)
              .toList());
    }

    return context;
  }
}
