/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.record;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import java.util.HashMap;
import java.util.Map;

/**
 * Supplies the {@link RecordValues} the coordinator's {@code StreamProcessor} uses to deserialize
 * its log records — wired via {@code StreamProcessorBuilder.recordValues(...)}.
 *
 * <p>The coordinator runs on a dedicated Raft partition that no engine touches. Its records carry
 * first-class event-bridge {@link ValueType}s, which the platform resolves to their {@link
 * io.camunda.zeebe.protocol.record.intent.CoordinatorIntent}; this custom mapping supplies the
 * matching record value classes (which live here, not in the engine's {@code UnifiedRecordValue}
 * registry).
 *
 * <p>The engine's full value map is retained so platform-internal records (e.g. {@code
 * ValueType.ERROR} written on a processing error) still deserialize on replay.
 */
public final class EventBridgeRecordValues {

  // First-class event-bridge value types for the coordinator's record kinds (see protocol.xml).
  public static final ValueType OFFSET_VALUE_TYPE = ValueType.EVENT_BRIDGE_OFFSET;
  public static final ValueType MEMBERSHIP_VALUE_TYPE = ValueType.EVENT_BRIDGE_MEMBERSHIP;
  public static final ValueType REBALANCE_VALUE_TYPE = ValueType.EVENT_BRIDGE_REBALANCE;

  private EventBridgeRecordValues() {}

  public static RecordValues create() {
    final Map<ValueType, UnifiedRecordValue> values =
        new HashMap<>(UnifiedRecordValue.allRecordsMap());
    values.put(OFFSET_VALUE_TYPE, new OffsetCommitRecord());
    values.put(MEMBERSHIP_VALUE_TYPE, new MembershipRecord());
    values.put(REBALANCE_VALUE_TYPE, new RebalanceRecord());
    return new RecordValues(values);
  }
}
