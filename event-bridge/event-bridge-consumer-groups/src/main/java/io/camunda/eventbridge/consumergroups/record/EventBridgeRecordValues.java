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
 * <p>The coordinator runs on a dedicated Raft partition that no engine touches, so its records are
 * tagged with a placeholder {@link ValueType} ({@link #OFFSET_VALUE_TYPE}) that this custom mapping
 * resolves to {@link OffsetCommitRecord}. This avoids adding an event-bridge value to the shared
 * protocol enum. The {@code StreamProcessor} dispatches by {@code RecordType}, not by intent, so
 * the tag is purely a deserialization key. Promoting it to a dedicated {@code ValueType} is the
 * clean graduation step once this leaves PoC status.
 *
 * <p>The engine's full value map is retained so platform-internal records (e.g. {@code
 * ValueType.ERROR} written on a processing error) still deserialize on replay.
 */
public final class EventBridgeRecordValues {

  // Opaque tags for the coordinator's own record kinds on its dedicated Raft partition. No engine
  // runs there, so the log never carries real CHECKPOINT/CLOCK/SCALE records — reusing those
  // ValueTypes as local deserialization keys cannot collide. The chosen constants are arbitrary;
  // they carry no engine meaning here (e.g. TOPIC is not a "scale" record). The StreamProcessor
  // dispatches by ValueType + RecordType, not by intent.
  //
  // TODO(event-bridge): borrowing engine ValueTypes is a PoC shortcut. The shared zeebe-protocol
  // ValueType enum cannot cleanly host event-bridge-specific constants (it would be an engine ->
  // event-bridge layering violation), so the real graduation is for the coordinator to own its
  // record (de)serialization instead of riding the engine's ValueType-keyed RecordValues. Until
  // then these tags are safe but deliberately misleading by name.
  public static final ValueType OFFSET_VALUE_TYPE = ValueType.CHECKPOINT;
  public static final ValueType MEMBERSHIP_VALUE_TYPE = ValueType.CLOCK;
  public static final ValueType REBALANCE_VALUE_TYPE = ValueType.SCALE;

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
