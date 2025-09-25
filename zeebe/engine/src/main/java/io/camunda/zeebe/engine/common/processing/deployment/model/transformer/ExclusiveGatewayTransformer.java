/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableExclusiveGateway;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.common.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.common.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;

public final class ExclusiveGatewayTransformer
    implements ModelElementTransformer<ExclusiveGateway> {

  @Override
  public Class<ExclusiveGateway> getType() {
    return ExclusiveGateway.class;
  }

  @Override
  public void transform(final ExclusiveGateway element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableExclusiveGateway gateway =
        process.getElementById(element.getId(), ExecutableExclusiveGateway.class);

    transformDefaultFlow(element, process, gateway);
  }

  private void transformDefaultFlow(
      final ExclusiveGateway element,
      final ExecutableProcess process,
      final ExecutableExclusiveGateway gateway) {
    final SequenceFlow defaultFlowElement = element.getDefault();

    if (defaultFlowElement != null) {
      final String defaultFlowId = defaultFlowElement.getId();
      final ExecutableSequenceFlow defaultFlow =
          process.getElementById(defaultFlowId, ExecutableSequenceFlow.class);

      gateway.setDefaultFlow(defaultFlow);
    }
  }
}
