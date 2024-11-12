/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collections;
import java.util.List;

public final class CallActivityTransformer implements ModelElementTransformer<CallActivity> {

  @Override
  public Class<CallActivity> getType() {
    return CallActivity.class;
  }

  @Override
  public void transform(final CallActivity element, final TransformContext context) {

    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableCallActivity callActivity =
        process.getElementById(element.getId(), ExecutableCallActivity.class);

    transformCalledElement(element, callActivity, context.getExpressionLanguage());
    transformLexicographicIndex(element, context.getProcesses(), callActivity);
  }

  private void transformCalledElement(
      final CallActivity element,
      final ExecutableCallActivity callActivity,
      final ExpressionLanguage expressionLanguage) {

    final ZeebeCalledElement calledElement =
        element.getSingleExtensionElement(ZeebeCalledElement.class);

    final var processId = calledElement.getProcessId();
    final var expression = expressionLanguage.parseExpression(processId);

    callActivity.setCalledElementProcessId(expression);

    final var propagateAllChildVariablesEnabled =
        calledElement.isPropagateAllChildVariablesEnabled();
    callActivity.setPropagateAllChildVariablesEnabled(propagateAllChildVariablesEnabled);

    final var propagateAllParentVariablesEnabled =
        calledElement.isPropagateAllParentVariablesEnabled();
    callActivity.setPropagateAllParentVariablesEnabled(propagateAllParentVariablesEnabled);

    final var bindingType = calledElement.getBindingType();
    callActivity.setBindingType(bindingType);

    final var versionTag = calledElement.getVersionTag();
    callActivity.setVersionTag(versionTag);
  }

  private static void transformLexicographicIndex(
      final CallActivity element,
      final List<ExecutableProcess> processes,
      final ExecutableCallActivity callActivity) {
    final List<String> allCallActivityIds =
        processes.stream()
            .flatMap(p -> p.getFlowElements().stream())
            .filter(ExecutableCallActivity.class::isInstance)
            .map(ca -> BufferUtil.bufferAsString(ca.getId()))
            .sorted()
            .toList();
    final int index = Collections.binarySearch(allCallActivityIds, element.getId());
    callActivity.setLexicographicIndex(index);
  }
}
