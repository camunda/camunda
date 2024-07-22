/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.scale;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.value.ScaleRecordValue;
import org.agrona.DirectBuffer;

public class ScaleRecord extends UnifiedRecordValue implements ScaleRecordValue {

  // routing info
  private final IntegerProperty currentPartitionCountProp =
      new IntegerProperty("currentPartitionCount", -1);
  private final IntegerProperty newPartitionCountProp =
      new IntegerProperty("newPartitionCount", -1);

  // relocation correlationKey
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");

  // Relocating msg subscription
  private final ObjectProperty<MessageSubscriptionRecord> commandValueForFollowupProperty =
      new ObjectProperty<>("msgSubscriptionToRelocate", new MessageSubscriptionRecord());

  public ScaleRecord() {
    super(2);
    declareProperty(currentPartitionCountProp).declareProperty(newPartitionCountProp);
  }

  @Override
  public RoutingInfoRecordValue getRoutingInfo() {
    return new RoutingInfoRecord(
        currentPartitionCountProp.getValue(), newPartitionCountProp.getValue());
  }

  public void setRoutingInfo(final int currentPartitionCount, final int newPartitionCount) {
    currentPartitionCountProp.setValue(currentPartitionCount);
    newPartitionCountProp.setValue(newPartitionCount);
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKeyProp.getValue();
  }

  public ScaleRecord setCorrelationKey(final String correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public ScaleRecord setCorrelationKey(final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  record RoutingInfoRecord(int currentPartitionCount, int newPartitionCount)
      implements RoutingInfoRecordValue {}
}
