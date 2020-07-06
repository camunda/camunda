/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.model.transformer;

import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processing.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.SubProcess;

public final class SubProcessTransformer implements ModelElementTransformer<SubProcess> {

  @Override
  public Class<SubProcess> getType() {
    return SubProcess.class;
  }

  @Override
  public void transform(final SubProcess element, final TransformContext context) {
    final ExecutableWorkflow currentWorkflow = context.getCurrentWorkflow();
    final ExecutableFlowElementContainer subprocess =
        currentWorkflow.getElementById(element.getId(), ExecutableFlowElementContainer.class);

    if (element.triggeredByEvent()) {
      transformEventSubprocess(element, currentWorkflow, subprocess);
    }
  }

  private void transformEventSubprocess(
      final SubProcess element,
      final ExecutableWorkflow currentWorkflow,
      final ExecutableFlowElementContainer subprocess) {

    if (element.getScope() instanceof FlowNode) {
      final FlowNode scope = (FlowNode) element.getScope();
      final ExecutableFlowElementContainer parentSubProc =
          currentWorkflow.getElementById(scope.getId(), ExecutableFlowElementContainer.class);

      parentSubProc.attach(subprocess);
    } else {
      // top-level start event
      currentWorkflow.attach(subprocess);
    }

    final ExecutableStartEvent startEvent = subprocess.getStartEvents().iterator().next();

    startEvent.setEventSubProcess(subprocess.getId());
  }
}
