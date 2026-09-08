/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistorybatch;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryBatchRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

public final class AgentHistoryBatchRecord extends UnifiedRecordValue
    implements AgentHistoryBatchRecordValue {

  private final LongProperty agentInstanceKeyProp = new LongProperty("agentInstanceKey", -1L);
  private final ArrayProperty<StringValue> historyItemIdsProp =
      new ArrayProperty<>("historyItemIds", StringValue::new);

  public AgentHistoryBatchRecord() {
    super(2);
    declareProperty(agentInstanceKeyProp).declareProperty(historyItemIdsProp);
  }

  @Override
  public long getAgentInstanceKey() {
    return agentInstanceKeyProp.getValue();
  }

  public AgentHistoryBatchRecord setAgentInstanceKey(final long agentInstanceKey) {
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

  public AgentHistoryBatchRecord setHistoryItemIds(final List<String> historyItemIds) {
    historyItemIdsProp.reset();
    if (historyItemIds != null) {
      historyItemIds.forEach(id -> historyItemIdsProp.add().wrap(BufferUtil.wrapString(id)));
    }
    return this;
  }
}
