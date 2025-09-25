/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.common.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.common.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;

public final class SubProcessTransformer implements ModelElementTransformer<SubProcess> {

  @Override
  public Class<SubProcess> getType() {
    return SubProcess.class;
  }

  @Override
  public void transform(final SubProcess element, final TransformContext context) {
    final ExecutableProcess currentProcess = context.getCurrentProcess();
    final ExecutableFlowElementContainer subprocess =
        currentProcess.getElementById(element.getId(), ExecutableFlowElementContainer.class);

    if (element.triggeredByEvent()) {
      transformEventSubprocess(element, currentProcess, subprocess);
    }
  }

  private void transformEventSubprocess(
      final SubProcess element,
      final ExecutableProcess currentProcess,
      final ExecutableFlowElementContainer subprocess) {

    // set the element type explicitly because the element name is equal to an embedded subprocess
    subprocess.setElementType(BpmnElementType.EVENT_SUB_PROCESS);

    if (element.getScope() instanceof FlowNode) {
      final FlowNode scope = (FlowNode) element.getScope();
      final ExecutableFlowElementContainer parentSubProc =
          currentProcess.getElementById(scope.getId(), ExecutableFlowElementContainer.class);

      parentSubProc.attach(subprocess);
    } else {
      // top-level start event
      currentProcess.attach(subprocess);
    }

    final ExecutableStartEvent startEvent = subprocess.getStartEvents().iterator().next();

    startEvent.setEventSubProcess(subprocess.getId());
  }
}
