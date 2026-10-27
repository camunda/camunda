/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedMessageSubscriptionTypeFilter;
import io.camunda.gateway.protocol.model.MessageSubscriptionTypeEnum;
import io.camunda.gateway.protocol.model.MessageSubscriptionTypeFilterProperty;

public class MessageSubscriptionTypePropertyDeserializer
    extends FilterDeserializer<MessageSubscriptionTypeFilterProperty, MessageSubscriptionTypeEnum> {

  @Override
  protected Class<? extends MessageSubscriptionTypeFilterProperty> getFinalType() {
    return AdvancedMessageSubscriptionTypeFilter.class;
  }

  @Override
  protected Class<MessageSubscriptionTypeEnum> getImplicitValueType() {
    return MessageSubscriptionTypeEnum.class;
  }

  @Override
  protected MessageSubscriptionTypeFilterProperty createFromImplicitValue(
      final MessageSubscriptionTypeEnum value) {
    return AdvancedMessageSubscriptionTypeFilter.Builder.create().$eq(value).build();
  }
}
