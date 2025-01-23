/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import java.util.Collection;

public final class AdHocSubProcessTransformer implements ModelElementTransformer<AdHocSubProcess> {

  @Override
  public Class<AdHocSubProcess> getType() {
    return AdHocSubProcess.class;
  }

  @Override
  public void transform(final AdHocSubProcess element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final var executableAdHocSubProcess =
        process.getElementById(element.getId(), ExecutableAdHocSubProcess.class);

    final Collection<AbstractFlowElement> childElements =
        executableAdHocSubProcess.getChildElements();
    setAdHocActivities(executableAdHocSubProcess, childElements);
  }

  private static void setAdHocActivities(
      final ExecutableAdHocSubProcess executableAdHocSubProcess,
      final Collection<AbstractFlowElement> childElements) {
    childElements.stream()
        .filter(ExecutableFlowNode.class::isInstance)
        .map(ExecutableFlowNode.class::cast)
        .filter(flowElement -> flowElement.getIncoming().isEmpty())
        .forEach(executableAdHocSubProcess::addAdHocActivity);
  }
}
