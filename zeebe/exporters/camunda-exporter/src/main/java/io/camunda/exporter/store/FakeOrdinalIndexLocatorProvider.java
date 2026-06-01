/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import com.google.common.base.Strings;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.AuditLogProcessInstanceRelated;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationRelated;
import io.camunda.zeebe.protocol.record.value.OrdinalKeyBased;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeOrdinalIndexLocatorProvider implements IndexLocatorProvider {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(FakeOrdinalIndexLocatorProvider.class);
  private static final long APPROX_IDS_BETWEEN_ROOT_PROCESS_INSTANCES = 20;
  // aim for roughly 1 hours worth at 300 PI/s (across 3 partitions)
  private static final long IDS_PER_ORDINAL =
      100 * 60 * 60 * APPROX_IDS_BETWEEN_ROOT_PROCESS_INSTANCES;
  private final NoopIndexLocator noopIndexLocator = new NoopIndexLocator();
  private final Map<Integer, String> ordinalSuffixes = new ConcurrentHashMap<>();

  public FakeOrdinalIndexLocatorProvider() {}

  @Override
  public IndexLocator createIndexLocator(final Record<?> record) {
    if (record.getValue() instanceof final OrdinalKeyBased ordinalKeyBased) {
      return createIndexLocator(ordinalKeyBased);
    }

    final long ordinalBaseKey = getOrdinalBaseKey(record.getValue());
    if (ordinalBaseKey > 0) {
      LOGGER.warn(
          "Processing a record we expect to have an ordinal using fallback behavior. Record key: {}, "
              + "position: {}, type: {}, value type: {}.",
          record.getKey(),
          record.getPosition(),
          record.getRecordType(),
          record.getValueType());
      final int ordinal = getOrdinal(ordinalBaseKey);
      final var suffix = getOrCreateOrdinalSuffix("exporter", ordinal);
      return new SingleSuffixOrdinalIndexLocator(suffix);
    }

    LOGGER.warn(
        "Processing a record that is not an instance of OrdinalKeyBased. Record key: {}, "
            + "position: {}, type: {}, value type: {}. This may lead to suboptimal index usage.",
        record.getKey(),
        record.getPosition(),
        record.getRecordType(),
        record.getValueType());

    return noopIndexLocator;
  }

  @Override
  public IndexLocator createIndexLocator(final OrdinalKeyBased ordinalKeyBased) {
    final var suffix = getOrCreateOrdinalSuffix("ord", ordinalKeyBased.getOrdinalKey());
    return new SingleSuffixOrdinalIndexLocator(suffix);
  }

  private long getOrdinalBaseKey(final RecordValue recordValue) {
    return switch (recordValue) {
      case final ProcessInstanceRelated pi -> pi.getProcessInstanceKey();
      case final AuditLogProcessInstanceRelated audit -> audit.getRootProcessInstanceKey();
      case final BatchOperationItemValue boi -> boi.getRootProcessInstanceKey();
      case final BatchOperationRelated bo -> bo.getBatchOperationKey();
      default -> -1L;
    };
  }

  private static int getOrdinal(final long rootProcessInstanceKey) {
    final long key = Protocol.decodeKeyInPartition(rootProcessInstanceKey);
    return (int) (key / IDS_PER_ORDINAL);
  }

  private String getOrCreateOrdinalSuffix(final String prefix, final int ordinal) {
    var suffix = ordinalSuffixes.get(ordinal);
    if (suffix == null) {
      final var newSuffix = createOrdinalSuffix(prefix, ordinal);
      suffix = ordinalSuffixes.putIfAbsent(ordinal, newSuffix);
      if (suffix == null) {
        LOGGER.info("New ordinal started: {} ({} ordinals total)", ordinal, ordinalSuffixes.size());
        suffix = newSuffix;
      }
    }
    return suffix;
  }

  private String createOrdinalSuffix(final String prefix, final int ordinal) {
    return prefix + Strings.padStart(String.valueOf(ordinal), 5, '0');
  }

  static class NoopIndexLocator implements IndexLocator {

    @Override
    public String getIndexLocation(final ExporterEntity<?> entity, final String baseIndexName) {
      return baseIndexName;
    }
  }

  static class SingleSuffixOrdinalIndexLocator implements IndexLocator {
    private final String suffix;

    public SingleSuffixOrdinalIndexLocator(final String suffix) {
      this.suffix = suffix;
    }

    @Override
    public String getIndexLocation(final ExporterEntity<?> entity, final String baseIndexName) {
      return baseIndexName + suffix;
    }
  }
}
