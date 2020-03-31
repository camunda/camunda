/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.el.Expression;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.ExtensionElements;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import io.zeebe.util.buffer.BufferUtil;

public final class MessageTransformer implements ModelElementTransformer<Message> {

  @Override
  public Class<Message> getType() {
    return Message.class;
  }

  @Override
  public void transform(final Message element, final TransformContext context) {

    final String id = element.getId();
    final ExecutableMessage executableElement = new ExecutableMessage(id);

    final ExtensionElements extensionElements = element.getExtensionElements();

    if (extensionElements != null) {
      final ZeebeSubscription subscription =
          extensionElements.getElementsQuery().filterByType(ZeebeSubscription.class).singleResult();
      final Expression correlationKeyExpression =
          context.getExpressionLanguage().parseExpression(subscription.getCorrelationKey());

      executableElement.setCorrelationKeyExpression(correlationKeyExpression);
    }

    if (element.getName() != null) {
      executableElement.setMessageName(BufferUtil.wrapString(element.getName()));
      context.addMessage(executableElement);
    }
  }
}
