/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.model.transformer;

import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.protocol.record.value.BpmnElementType;

public final class IntermediateCatchEventTransformer
    implements ModelElementTransformer<IntermediateCatchEvent> {

  @Override
  public Class<IntermediateCatchEvent> getType() {
    return IntermediateCatchEvent.class;
  }

  @Override
  public void transform(final IntermediateCatchEvent element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableCatchEventElement executableElement =
        process.getElementById(element.getId(), ExecutableCatchEventElement.class);

    final var isConnectedToEventBasedGateway =
        executableElement.getIncoming().stream()
            .map(ExecutableSequenceFlow::getSource)
            .anyMatch(source -> source.getElementType() == BpmnElementType.EVENT_BASED_GATEWAY);

    executableElement.setConnectedToEventBasedGateway(isConnectedToEventBasedGateway);
  }
}
