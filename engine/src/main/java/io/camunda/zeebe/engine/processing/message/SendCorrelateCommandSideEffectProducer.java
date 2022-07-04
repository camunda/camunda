/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectContext;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

final class SendCorrelateCommandSideEffectProducer implements SideEffectProducer {

  private final SubscriptionCommandSender commandSender;
  private final long processInstanceKey;
  private final long elementInstanceKey;
  private final DirectBuffer bpmnProcessIdBuffer;
  private final DirectBuffer messageNameBuffer;
  private final long messageKey;
  private final DirectBuffer variablesBuffer;
  private final DirectBuffer correlationKeyBuffer;

  SendCorrelateCommandSideEffectProducer(
      final SubscriptionCommandSender commandSender,
      final MessageSubscriptionRecord subscriptionRecord) {
    this.commandSender = commandSender;

    processInstanceKey = subscriptionRecord.getProcessInstanceKey();
    elementInstanceKey = subscriptionRecord.getElementInstanceKey();
    bpmnProcessIdBuffer = BufferUtil.cloneBuffer(subscriptionRecord.getBpmnProcessIdBuffer());
    messageNameBuffer = BufferUtil.cloneBuffer(subscriptionRecord.getMessageNameBuffer());
    messageKey = subscriptionRecord.getMessageKey();
    variablesBuffer = BufferUtil.cloneBuffer(subscriptionRecord.getVariablesBuffer());
    correlationKeyBuffer = BufferUtil.cloneBuffer(subscriptionRecord.getCorrelationKeyBuffer());
  }

  @Override
  public boolean produce(final SideEffectContext context) {
    return commandSender.correlateProcessMessageSubscription(
        processInstanceKey,
        elementInstanceKey,
        bpmnProcessIdBuffer,
        messageNameBuffer,
        messageKey,
        variablesBuffer,
        correlationKeyBuffer);
  }
}
