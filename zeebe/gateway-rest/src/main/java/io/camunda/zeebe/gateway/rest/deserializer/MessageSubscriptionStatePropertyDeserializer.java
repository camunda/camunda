/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedMessageSubscriptionStateFilter;
import io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum;
import io.camunda.gateway.protocol.model.MessageSubscriptionStateFilterProperty;

public class MessageSubscriptionStatePropertyDeserializer
    extends FilterDeserializer<
        MessageSubscriptionStateFilterProperty, MessageSubscriptionStateEnum> {

  @Override
  protected Class<? extends MessageSubscriptionStateFilterProperty> getFinalType() {
    return AdvancedMessageSubscriptionStateFilter.class;
  }

  @Override
  protected Class<MessageSubscriptionStateEnum> getImplicitValueType() {
    return MessageSubscriptionStateEnum.class;
  }

  @Override
  protected MessageSubscriptionStateFilterProperty createFromImplicitValue(
      final MessageSubscriptionStateEnum value) {
    final var filter = new AdvancedMessageSubscriptionStateFilter();
    filter.set$Eq(value);
    return filter;
  }
}
