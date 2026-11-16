/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistorybatch;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryBatchRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class AgentHistoryBatchRecordV1 extends UnifiedRecordValue
    implements AgentHistoryBatchRecordValue {

  private final LongProperty agentInstanceKeyProp = new LongProperty("agentInstanceKey", -1L);
  private final ArrayProperty<StringValue> historyItemIdsProp =
      new ArrayProperty<>("historyItemIds", StringValue::new);
  private final IntegerProperty storageOrdinalProp = new IntegerProperty("storageOrdinal", 0);

  public AgentHistoryBatchRecordV1() {
    super(3);
    declareProperty(agentInstanceKeyProp)
        .declareProperty(historyItemIdsProp)
        .declareProperty(storageOrdinalProp);
  }

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKeyProp.getValue();
  }

  public AgentHistoryBatchRecordV1 setAgentInstanceKey(final long agentInstanceKey) {
    agentInstanceKeyProp.setValue(agentInstanceKey);
    return this;
  }

  @Override
  public List<String> getHistoryItemIds() {
    return historyItemIdsProp.stream()
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .toList();
  }

  public AgentHistoryBatchRecordV1 setHistoryItemIds(final List<String> historyItemIds) {
    historyItemIdsProp.reset();
    if (historyItemIds != null) {
      historyItemIds.forEach(id -> historyItemIdsProp.add().wrap(BufferUtil.wrapString(id)));
    }
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinalProp.getValue();
  }

  public AgentHistoryBatchRecordV1 setStorageOrdinal(final int storageOrdinal) {
    storageOrdinalProp.setValue(storageOrdinal);
    return this;
  }
}
