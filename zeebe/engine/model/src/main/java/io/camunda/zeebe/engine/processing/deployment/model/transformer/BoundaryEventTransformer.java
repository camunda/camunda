/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.Activity;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;

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

    if (element.isMessage()) {
      element.setEventType(BpmnEventType.MESSAGE);
    } else if (element.isTimer()) {
      element.setEventType(BpmnEventType.TIMER);
    } else if (element.isError()) {
      element.setEventType(BpmnEventType.ERROR);
    } else if (element.isEscalation()) {
      element.setEventType(BpmnEventType.ESCALATION);
    } else if (element.isCompensation()) {
      element.setEventType(BpmnEventType.COMPENSATION);
    } else if (element.isConditional()) {
      element.setEventType(BpmnEventType.CONDITIONAL);
    }

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
