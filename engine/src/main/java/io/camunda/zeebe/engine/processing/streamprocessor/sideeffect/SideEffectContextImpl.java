/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.sideeffect;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;

public class SideEffectContextImpl implements SideEffectContext {

  private final SubscriptionCommandSender subscriptionCommandSender;

  public SideEffectContextImpl(final SubscriptionCommandSender subscriptionCommandSender) {
    this.subscriptionCommandSender = subscriptionCommandSender;
  }

  @Override
  public SubscriptionCommandSender getSiSubscriptionCommandSender() {
    return subscriptionCommandSender;
  }
}
