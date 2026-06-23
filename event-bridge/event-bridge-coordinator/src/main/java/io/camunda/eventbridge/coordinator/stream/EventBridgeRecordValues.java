/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.stream;

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

  // Opaque tags for the two coordinator record kinds on the dedicated coordinator partition (no
  // engine runs there, so reusing these ValueTypes is safe; the StreamProcessor dispatches by the
  // deserialized value type / RecordType, not by intent).
  public static final ValueType OFFSET_VALUE_TYPE = ValueType.CHECKPOINT;
  public static final ValueType GROUP_METADATA_VALUE_TYPE = ValueType.CLOCK;

  private EventBridgeRecordValues() {}

  public static RecordValues create() {
    final Map<ValueType, UnifiedRecordValue> values =
        new HashMap<>(UnifiedRecordValue.allRecordsMap());
    values.put(OFFSET_VALUE_TYPE, new OffsetCommitRecord());
    values.put(GROUP_METADATA_VALUE_TYPE, new GroupMetadataRecord());
    return new RecordValues(values);
  }
}
