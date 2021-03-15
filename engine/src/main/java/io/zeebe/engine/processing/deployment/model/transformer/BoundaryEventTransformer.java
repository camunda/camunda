/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.transformer;

import io.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;
import io.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.BoundaryEvent;

public final class BoundaryEventTransformer implements ModelElementTransformer<BoundaryEvent> {
  @Override
  public Class<BoundaryEvent> getType() {
    return BoundaryEvent.class;
  }

  @Override
  public void transform(final BoundaryEvent event, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableBoundaryEvent element =
        process.getElementById(event.getId(), ExecutableBoundaryEvent.class);

    element.setInterrupting(event.cancelActivity());

    attachToActivity(event, process, element);
  }

  private void attachToActivity(
      final BoundaryEvent event,
      final ExecutableProcess process,
      final ExecutableBoundaryEvent element) {
    final Activity attachedToActivity = event.getAttachedTo();
    final ExecutableActivity attachedToElement =
        process.getElementById(attachedToActivity.getId(), ExecutableActivity.class);

    attachedToElement.attach(element);
  }
}
