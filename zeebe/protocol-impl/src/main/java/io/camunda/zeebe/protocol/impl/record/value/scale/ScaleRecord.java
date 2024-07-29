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
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.value.ScaleRecordValue;
import org.agrona.DirectBuffer;

public class ScaleRecord extends UnifiedRecordValue implements ScaleRecordValue {

  // currentPartitionCount
  // newPartitionCount

  private final IntegerProperty currentPartitionCountProp =
      new IntegerProperty("currentPartitionCount", -1);
  private final IntegerProperty newPartitionCountProp =
      new IntegerProperty("newPartitionCount", -1);

  // ScaleRelocateMessageSubscriptionStart
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");
  // TODO: field for new partition id, to avoid re-calculating it.

  // ScaleRelocateMessageSubscriptionApply
  private final ObjectProperty<MessageSubscriptionRecord> messageSubscriptionRecord =
      new ObjectProperty<>("messageSubscriptionRecord", new MessageSubscriptionRecord());

  // ScaleRelocateMessageApply
  private final ObjectProperty<MessageRecord> messageRecord =
      new ObjectProperty<>("messageRecord", new MessageRecord());

  public ScaleRecord() {
    super(4);
    declareProperty(currentPartitionCountProp)
        .declareProperty(newPartitionCountProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(messageSubscriptionRecord);
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

  public void setCorrelationKey(final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
  }

  public MessageSubscriptionRecord getMessageSubscriptionRecord() {
    return messageSubscriptionRecord.getValue();
  }

  public void setMessageSubscriptionRecord(final MessageSubscriptionRecord record) {
    messageSubscriptionRecord.getValue().wrap(record);
  }

  public MessageRecord getMessageRecord() {
    return messageRecord.getValue();
  }

  public void setMessageRecord(final MessageRecord messageRecord) {
    this.messageRecord.getValue().wrap(messageRecord);
  }

  record RoutingInfoRecord(int currentPartitionCount, int newPartitionCount)
      implements RoutingInfoRecordValue {}
}
