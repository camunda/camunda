/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.record;

import io.camunda.exporter.kafka.config.RecordConfiguration;
import io.camunda.exporter.kafka.config.RecordsConfiguration;
import io.camunda.exporter.kafka.producer.KafkaExportRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class RecordHandler {

  private static final int SCHEMA_VERSION = 1;
  // Envelope prefix is a constant — no Jackson node allocation needed per record.
  private static final String ENVELOPE_PREFIX =
      "{\"schemaVersion\":" + SCHEMA_VERSION + ",\"record\":";

  // EnumSet uses a bitfield for O(1) lookup — faster than Set.of() (hash table) on the hot path.
  private static final Set<ValueType> PARTITION_ONE_VALUE_TYPES =
      EnumSet.of(
          ValueType.PROCESS,
          ValueType.DECISION,
          ValueType.DECISION_REQUIREMENTS,
          ValueType.FORM,
          ValueType.USER,
          ValueType.TENANT,
          ValueType.ROLE,
          ValueType.GROUP,
          ValueType.AUTHORIZATION,
          ValueType.MAPPING_RULE);

  private final RecordsConfiguration recordsConfiguration;

  public RecordHandler(final RecordsConfiguration recordsConfiguration) {
    this.recordsConfiguration = recordsConfiguration;
  }

  /**
   * Returns {@code true} if this record should be excluded because it is a definition type that
   * must only be exported from partition 1, but arrived on a different partition.
   *
   * <p>The {@link io.camunda.exporter.kafka.record.KafkaRecordFilter} handles record-type filtering
   * upstream; this method only gates on the partition scope.
   */
  public boolean isExcludedByPartitionScope(final Record<?> record) {
    return requiresPartitionOne(record.getValueType()) && record.getPartitionId() != 1;
  }

  /**
   * Returns {@code true} if this record passes both the partition-scope check and the configured
   * record-type filter. Useful for direct callers and unit tests that bypass the upstream filter.
   */
  public boolean isAllowed(final Record<?> record) {
    if (isExcludedByPartitionScope(record)) {
      return false;
    }
    final RecordConfiguration config = recordsConfiguration.forType(record.getValueType());
    return config.allowedTypes().contains(record.getRecordType());
  }

  public KafkaExportRecord toKafkaExportRecord(final Record<?> record) {
    final RecordConfiguration config = recordsConfiguration.forType(record.getValueType());
    // Simple string concatenation — avoids creating an ObjectNode graph per record on the hot path.
    final String key = record.getPartitionId() + "-" + record.getPosition();
    final String valueJson = ENVELOPE_PREFIX + record.getValue().toJson() + "}";

    final String tenantId =
        record.getValue() instanceof TenantOwned tenantOwned
            ? tenantOwned.getTenantId()
            : TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    final String safeTenantId =
        tenantId == null || tenantId.isBlank() ? TenantOwned.DEFAULT_TENANT_IDENTIFIER : tenantId;
    final String brokerVersion =
        record.getBrokerVersion() == null || record.getBrokerVersion().isBlank()
            ? "unknown"
            : record.getBrokerVersion();

    final Map<String, String> headers =
        Map.of(
            "valueType",
            record.getValueType().name(),
            "intent",
            record.getIntent().name(),
            "partitionId",
            Integer.toString(record.getPartitionId()),
            "recordType",
            record.getRecordType().name(),
            "tenantId",
            safeTenantId,
            "brokerVersion",
            brokerVersion);

    return new KafkaExportRecord(
        config.topic(), key, valueJson, headers, record.getPartitionId(), record.getPosition());
  }

  private static boolean requiresPartitionOne(final ValueType valueType) {
    return PARTITION_ONE_VALUE_TYPES.contains(valueType);
  }
}
