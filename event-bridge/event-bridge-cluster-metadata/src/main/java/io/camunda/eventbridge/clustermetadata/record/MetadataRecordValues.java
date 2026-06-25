/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.record;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import java.util.HashMap;
import java.util.Map;

/**
 * Supplies the {@link RecordValues} the metadata {@code StreamProcessor} uses to deserialize its
 * log records — wired via {@code StreamProcessorBuilder.recordValues(...)}.
 *
 * <p>The metadata group runs on a dedicated Raft partition that no engine touches. Its records
 * carry the first-class {@link ValueType#EVENT_BRIDGE_TOPIC}, which the platform resolves to {@link
 * io.camunda.zeebe.protocol.record.intent.MetadataIntent}; this custom mapping supplies the
 * matching {@link TopicRecord} (which lives here, not in the engine's {@code UnifiedRecordValue}
 * registry).
 */
public final class MetadataRecordValues {

  // First-class event-bridge value types for the metadata stream's record kinds (see protocol.xml).
  public static final ValueType TOPIC_VALUE_TYPE = ValueType.EVENT_BRIDGE_TOPIC;
  public static final ValueType BROKER_VALUE_TYPE = ValueType.EVENT_BRIDGE_BROKER;

  private MetadataRecordValues() {}

  public static RecordValues create() {
    final Map<ValueType, UnifiedRecordValue> values =
        new HashMap<>(UnifiedRecordValue.allRecordsMap());
    values.put(TOPIC_VALUE_TYPE, new TopicRecord());
    values.put(BROKER_VALUE_TYPE, new BrokerRecord());
    return new RecordValues(values);
  }
}
