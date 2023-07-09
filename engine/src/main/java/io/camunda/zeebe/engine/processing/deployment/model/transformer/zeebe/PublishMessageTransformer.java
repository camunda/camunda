/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExectuablePublishMessage;
import io.camunda.zeebe.engine.processing.deployment.model.element.PublishMessageProperties;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebePublishMessage;
import java.util.Optional;

public final class PublishMessageTransformer {

  public void transform(
      final ExectuablePublishMessage executableElement,
      final TransformContext context,
      final Message message,
      final ZeebePublishMessage publishMessage) {

    if (publishMessage == null) {
      return;
    }

    final var publishMessageProperties =
        Optional.ofNullable(executableElement.getPublishMessageProperties())
            .orElse(new PublishMessageProperties());
    executableElement.setPublishMessageProperties(publishMessageProperties);

    final var expressionLanguage = context.getExpressionLanguage();

    final var messageNameExpression = expressionLanguage.parseExpression(message.getName());
    publishMessageProperties.setMessageName(messageNameExpression);

    final var correlationKeyExpression =
        expressionLanguage.parseExpression(publishMessage.getCorrelationKey());
    publishMessageProperties.setCorrelationKey(correlationKeyExpression);

    final var messageId = publishMessage.getMessageId();
    if (messageId != null) {
      final var messageIdExpression = expressionLanguage.parseExpression(messageId);
      publishMessageProperties.setMessageId(messageIdExpression);
    }

    final var timeToLive = publishMessage.getTimeToLive();
    if (timeToLive != null) {
      final var timeToLiveExpression = expressionLanguage.parseExpression(timeToLive);
      publishMessageProperties.setTimeToLive(timeToLiveExpression);
    }
  }
}
