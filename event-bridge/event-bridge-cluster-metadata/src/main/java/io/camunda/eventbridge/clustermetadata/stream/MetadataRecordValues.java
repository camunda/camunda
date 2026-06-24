/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import java.util.HashMap;
import java.util.Map;

/**
 * Supplies the {@link RecordValues} the metadata {@code StreamProcessor} uses to deserialize its
 * log records — wired via {@code StreamProcessorBuilder.recordValues(...)}.
 *
 * <p>The metadata group runs on a dedicated Raft partition that no engine touches, so its records
 * are tagged with a placeholder {@link ValueType} ({@link #TOPIC_VALUE_TYPE}) that this custom
 * mapping resolves to {@link TopicRecord}. This avoids adding an event-bridge value to the shared
 * protocol enum. The {@code StreamProcessor} dispatches by {@code RecordType}, not by intent, so
 * the tag is purely a deserialization key.
 *
 * <p>TODO(event-bridge): borrowing an engine {@link ValueType} is a PoC shortcut to be revisited;
 * see the planned event-bridge value-type abstraction.
 */
public final class MetadataRecordValues {

  // Opaque tag for the metadata stream's own record kind on its dedicated Raft partition. No engine
  // runs there, so the log never carries a real SCALE record — reusing that ValueType as a local
  // deserialization key cannot collide. The constant is arbitrary; it carries no engine meaning.
  public static final ValueType TOPIC_VALUE_TYPE = ValueType.SCALE;

  private MetadataRecordValues() {}

  public static RecordValues create() {
    final Map<ValueType, UnifiedRecordValue> values =
        new HashMap<>(UnifiedRecordValue.allRecordsMap());
    values.put(TOPIC_VALUE_TYPE, new TopicRecord());
    return new RecordValues(values);
  }
}
