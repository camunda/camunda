/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExectuablePublishMessage;
import io.camunda.zeebe.engine.processing.deployment.model.element.PublishMessageProperties;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class BpmnMessageBehavior {

  private static final Duration DEFAULT_MSG_TTL = Duration.ofHours(1);

  private final MessageRecord messageRecord =
      new MessageRecord().setVariables(DocumentValue.EMPTY_DOCUMENT);
  private final KeyGenerator keyGenerator;
  private final VariableState variableState;
  private final TypedCommandWriter commandWriter;
  private final ExpressionProcessor expressionBehavior;

  public BpmnMessageBehavior(
      final KeyGenerator keyGenerator,
      final VariableState variableState,
      final Writers writers,
      final ExpressionProcessor expressionBehavior) {
    this.keyGenerator = keyGenerator;
    this.expressionBehavior = expressionBehavior;
    this.variableState = variableState;
    commandWriter = writers.command();
  }

  public Either<Failure, ?> publishMessage(
      final ExectuablePublishMessage publishMessage, final BpmnElementContext context) {

    final var variables =
        variableState.getVariablesLocalAsDocument(context.getElementInstanceKey());

    final var publishMessageProps = publishMessage.getPublishMessageProperties();
    return evaluateMessageExpressions(publishMessageProps, context.getElementInstanceKey())
        .map(
            properties -> {
              writeMessagePublishCommand(properties, variables);
              return null;
            });
  }

  private Either<Failure, MessageProperties> evaluateMessageExpressions(
      final PublishMessageProperties publishMessageProps, final long scopeKey) {
    return Either.<Failure, MessageProperties>right(new MessageProperties())
        .flatMap(p -> evalMessageNameExp(publishMessageProps, scopeKey).map(p::messageName))
        .flatMap(p -> evalMessageIdExp(publishMessageProps, scopeKey).map(p::messageId))
        .flatMap(p -> evalCorrelationKeyExp(publishMessageProps, scopeKey).map(p::correlationKey))
        .flatMap(p -> evalTimeToLiveExp(publishMessageProps, scopeKey).map(p::timeToLive));
  }

  private Either<Failure, String> evalMessageNameExp(
      final PublishMessageProperties publishMessageProps, final long scopeKey) {
    return expressionBehavior.evaluateStringExpression(
        publishMessageProps.getMessageName(), scopeKey);
  }

  private Either<Failure, String> evalMessageIdExp(
      final PublishMessageProperties publishMessageProps, final long scopeKey) {
    final Expression messageId = publishMessageProps.getMessageId();
    if (messageId == null) {
      return Either.right(null);
    }
    return expressionBehavior.evaluateStringExpression(
        publishMessageProps.getMessageId(), scopeKey);
  }

  private Either<Failure, String> evalCorrelationKeyExp(
      final PublishMessageProperties publishMessageProps, final long scopeKey) {
    return expressionBehavior.evaluateMessageCorrelationKeyExpression(
        publishMessageProps.getCorrelationKey(), scopeKey);
  }

  private Either<Failure, Long> evalTimeToLiveExp(
      final PublishMessageProperties publishMessageProps, final long scopeKey) {
    final Expression timeToLive = publishMessageProps.getTimeToLive();
    if (timeToLive == null) {
      return Either.right(null);
    }

    return expressionBehavior
        .evaluateIntervalExpression(timeToLive, scopeKey)
        .map(
            interval -> {
              final var currentTimeMillis = ActorClock.currentTimeMillis();
              return interval.toEpochMilli(currentTimeMillis) - currentTimeMillis;
            });
  }

  private void writeMessagePublishCommand(
      final MessageProperties properties, final DirectBuffer variables) {

    messageRecord.reset();
    messageRecord.setName(properties.getMessageName());
    messageRecord.setCorrelationKey(properties.getCorrelationKey());
    messageRecord.setVariables(variables);
    Optional.ofNullable(properties.getMessageId()).ifPresent(messageRecord::setMessageId);

    final var timeToLive =
        Optional.ofNullable(properties.getTimeToLive()).orElse(DEFAULT_MSG_TTL.toMillis());
    messageRecord.setTimeToLive(timeToLive);

    final var key = keyGenerator.nextKey();
    commandWriter.appendFollowUpCommand(key, MessageIntent.PUBLISH, messageRecord);
  }

  private static final class MessageProperties {

    private String messageName;
    private String messageId;
    private String correlationKey;
    private Long timeToLive;

    public MessageProperties messageName(final String messageName) {
      this.messageName = messageName;
      return this;
    }

    public String getMessageName() {
      return messageName;
    }

    public MessageProperties messageId(final String messageId) {
      this.messageId = messageId;
      return this;
    }

    public String getMessageId() {
      return messageId;
    }

    public MessageProperties correlationKey(final String correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    public String getCorrelationKey() {
      return correlationKey;
    }

    public MessageProperties timeToLive(final Long timeToLive) {
      this.timeToLive = timeToLive;
      return this;
    }

    public Long getTimeToLive() {
      return timeToLive;
    }
  }
}
