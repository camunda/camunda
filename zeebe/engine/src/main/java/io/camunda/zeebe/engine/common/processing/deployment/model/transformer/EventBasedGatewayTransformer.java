/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableEventBasedGateway;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.common.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.common.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.EventBasedGateway;
import java.util.List;
import java.util.stream.Collectors;

public final class EventBasedGatewayTransformer
    implements ModelElementTransformer<EventBasedGateway> {

  @Override
  public Class<EventBasedGateway> getType() {
    return EventBasedGateway.class;
  }

  @Override
  public void transform(final EventBasedGateway element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableEventBasedGateway gateway =
        process.getElementById(element.getId(), ExecutableEventBasedGateway.class);

    final List<ExecutableCatchEvent> connectedEvents = getConnectedCatchEvents(gateway);
    gateway.setEvents(connectedEvents);
  }

  private List<ExecutableCatchEvent> getConnectedCatchEvents(
      final ExecutableEventBasedGateway gateway) {
    return gateway.getOutgoing().stream()
        .map(e -> (ExecutableCatchEvent) e.getTarget())
        .collect(Collectors.toList());
  }
}
