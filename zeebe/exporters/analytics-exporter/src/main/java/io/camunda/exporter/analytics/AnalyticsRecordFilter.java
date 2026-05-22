/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Set;

/**
 * Record filter for the analytics exporter with three filtering layers:
 *
 * <ol>
 *   <li><b>Record type</b> — only {@link RecordType#EVENT} records pass; commands and rejections
 *       are skipped.
 *   <li><b>Value type</b> — only value types with registered handlers pass (e.g. {@link
 *       ValueType#PROCESS_INSTANCE_CREATION}).
 *   <li><b>Partition ownership</b> — only events whose key encodes the local partition ID pass.
 * </ol>
 *
 * <p>Layers 1 and 2 are metadata-based and evaluated in phase 1 of the broker's filter pipeline
 * (before record deserialization). Layer 3 runs in phase 2 on the deserialized record, but only for
 * the small subset that passed phase 1.
 *
 * <h3>Partition filtering rationale</h3>
 *
 * <p>Zeebe distributes certain commands (deployments, identity, tenants, etc.) to all partitions.
 * Each partition writes follow-up events for the distributed command, but the event keys preserve
 * the originating partition's ID — encoded in the upper 13 bits of the 64-bit key via {@link
 * Protocol#encodePartitionId(int, long)}.
 *
 * <p>Without this filter, the analytics exporter on every partition would emit the same logical
 * event, leading to N× duplication in a cluster with N partitions.
 *
 * <p>The key-based check works because all {@code DistributedTypedRecordProcessor} implementations
 * in the engine preserve the originating partition ID in follow-up event keys — either via {@code
 * command.getKey()} directly, or via keys embedded in the distributed command's value that were
 * generated on the originating partition.
 */
record AnalyticsRecordFilter(Set<ValueType> acceptedValueTypes, int partitionId)
    implements RecordFilter {

  AnalyticsRecordFilter {
    acceptedValueTypes = Set.copyOf(acceptedValueTypes);
  }

  @Override
  public boolean acceptType(final RecordType recordType) {
    return recordType == RecordType.EVENT;
  }

  @Override
  public boolean acceptValue(final ValueType valueType) {
    return acceptedValueTypes.contains(valueType);
  }

  @Override
  public boolean acceptRecord(final Record<?> record) {
    return Protocol.decodePartitionId(record.getKey()) == partitionId;
  }
}
