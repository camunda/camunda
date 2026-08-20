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
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.Set;

/**
 * Record filter for the analytics exporter with four filtering layers:
 *
 * <ol>
 *   <li><b>Record type</b> — only {@link RecordType#EVENT} records pass; commands and rejections
 *       are skipped.
 *   <li><b>Value type</b> — only value types with registered handlers pass (e.g. {@link
 *       ValueType#PROCESS_INSTANCE}).
 *   <li><b>Intent</b> — only intents with registered handlers pass. This is an intentional
 *       over-approximation: the set is flat across all value types, so a record may pass intent
 *       filtering even if its (ValueType, Intent) pair has no handler. Exact routing happens in
 *       {@link HandlerRegistry#handle}.
 *   <li><b>Partition ownership</b> — only events whose ownership key encodes the local partition ID
 *       pass. See {@link #partitionOwnershipKey(Record)}.
 * </ol>
 *
 * <p>Layers 1-3 are metadata-based and evaluated in phase 1 of the broker's filter pipeline (before
 * record deserialization). Layer 4 runs in phase 2 on the deserialized record, but only for the
 * small subset that passed phase 1.
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
 * <p>Most {@code DistributedTypedRecordProcessor} implementations in the engine preserve the
 * originating partition ID in follow-up event keys — either via {@code command.getKey()} directly,
 * or via keys embedded in the distributed command's value that were generated on the originating
 * partition. {@code ResourceDeletionDeleteProcessor} is the exception: it appends the {@code
 * PROCESS}, {@code DECISION} and {@code FORM} {@code DELETED} follow-up events under a key from its
 * own {@code keyGenerator}, so on every partition {@code record.getKey()} decodes to that
 * partition. For those value types {@link #partitionOwnershipKey(Record)} substitutes the
 * definition key carried in the record value, which was minted on the originating partition.
 */
record AnalyticsRecordFilter(
    Set<ValueType> acceptedValueTypes, Set<Intent> acceptedIntents, int partitionId)
    implements RecordFilter {

  AnalyticsRecordFilter {
    acceptedValueTypes = Set.copyOf(acceptedValueTypes);
    acceptedIntents = Set.copyOf(acceptedIntents);
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
  public boolean acceptIntent(final Intent intent) {
    return acceptedIntents.contains(intent);
  }

  @Override
  public boolean acceptRecord(final Record<?> record) {
    return Protocol.decodePartitionId(partitionOwnershipKey(record)) == partitionId;
  }

  /**
   * Returns the key whose encoded partition ID identifies the partition that owns the record.
   *
   * <p>For deployment definitions the value's definition key is authoritative, because the engine
   * does not always derive the event key from it. For {@code CREATED} the two are the same key, so
   * routing all of these value types through the value is equivalent to {@link Record#getKey()}
   * there.
   */
  private static long partitionOwnershipKey(final Record<?> record) {
    return switch (record.getValueType()) {
      case PROCESS -> ((Process) record.getValue()).getProcessDefinitionKey();
      case DECISION -> ((DecisionRecordValue) record.getValue()).getDecisionKey();
      case FORM -> ((Form) record.getValue()).getFormKey();
      default -> record.getKey();
    };
  }
}
