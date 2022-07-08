/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableInclusiveGateway;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.InclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;

public final class InclusiveGatewayTransformer
    implements ModelElementTransformer<InclusiveGateway> {

  @Override
  public Class<InclusiveGateway> getType() {
    return InclusiveGateway.class;
  }

  @Override
  public void transform(final InclusiveGateway element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableInclusiveGateway gateway =
        process.getElementById(element.getId(), ExecutableInclusiveGateway.class);

    transformDefaultFlow(element, process, gateway);
  }

  private void transformDefaultFlow(
      final InclusiveGateway element,
      final ExecutableProcess process,
      final ExecutableInclusiveGateway gateway) {
    final SequenceFlow defaultFlowElement = element.getDefault();

    if (defaultFlowElement != null) {
      final String defaultFlowId = defaultFlowElement.getId();
      final ExecutableSequenceFlow defaultFlow =
          process.getElementById(defaultFlowId, ExecutableSequenceFlow.class);

      gateway.setDefaultFlow(defaultFlow);
    }
  }
}
