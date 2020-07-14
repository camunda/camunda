/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.model.transformer;

import io.zeebe.engine.processing.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.protocol.record.value.BpmnElementType;

public final class ProcessTransformer implements ModelElementTransformer<Process> {

  @Override
  public Class<Process> getType() {
    return Process.class;
  }

  @Override
  public void transform(final Process element, final TransformContext context) {

    final String id = element.getId();
    final ExecutableWorkflow workflow = new ExecutableWorkflow(id);

    workflow.setElementType(
        BpmnElementType.bpmnElementTypeFor(element.getElementType().getTypeName()));

    context.addWorkflow(workflow);
    context.setCurrentWorkflow(workflow);
  }
}
