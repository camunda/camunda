/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableMessage;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableReceiveTask;
import io.camunda.zeebe.engine.common.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.common.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.ReceiveTask;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;

public final class ReceiveTaskTransformer implements ModelElementTransformer<ReceiveTask> {

  @Override
  public Class<ReceiveTask> getType() {
    return ReceiveTask.class;
  }

  @Override
  public void transform(final ReceiveTask element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableReceiveTask executableElement =
        process.getElementById(element.getId(), ExecutableReceiveTask.class);

    final Message message = element.getMessage();
    final ExecutableMessage executableMessage = context.getMessage(message.getId());
    executableElement.setMessage(executableMessage);
    executableElement.setEventType(BpmnEventType.MESSAGE);
  }
}
