/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import io.camunda.exporter.handlers.batchoperation.BatchOperationChunkCreatedHandler;
import io.camunda.exporter.handlers.batchoperation.BatchOperationChunkCreatedItemHandler;
import io.camunda.exporter.handlers.batchoperation.listview.ListViewFromChunkItemHandler;
import io.camunda.search.schema.OrdinalIndexManager;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.AuditLogProcessInstanceRelated;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationRelated;
import io.camunda.zeebe.protocol.record.value.OrdinalKeyBased;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeOrdinalIndexLocatorProvider implements IndexLocatorProvider {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(FakeOrdinalIndexLocatorProvider.class);
  private static final long APPROX_IDS_BETWEEN_ROOT_PROCESS_INSTANCES = 20;
  // aim for roughly 1 hours worth at 300 PI/s (across 3 partitions)
  private static final long IDS_PER_ORDINAL =
      100 * 60 * 60 * APPROX_IDS_BETWEEN_ROOT_PROCESS_INSTANCES;
  private final OrdinalIndexManager ordinalIndexManager;
  private final NoopIndexLocator noopIndexLocator = new NoopIndexLocator();

  public FakeOrdinalIndexLocatorProvider(final OrdinalIndexManager ordinalIndexManager) {
    this.ordinalIndexManager = ordinalIndexManager;
  }

  @Override
  public IndexLocator createIndexLocator(final Record<?> record) {
    if (record.getValue() instanceof final BatchOperationChunkRecordValue chunks) {
      final var ordinalsByKey = extractOrdinalsByKey(chunks);
      final var suffixesByKey =
          ordinalsByKey.entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      e -> ordinalIndexManager.getOrCreateOrdinalSuffix(e.getValue())));
      return new MultiSuffixOrdinalIndexLocator(suffixesByKey);
    } else if (record.getValue() instanceof final OrdinalKeyBased ordinalKeyBased) {
      final var suffix =
          ordinalIndexManager.getOrCreateOrdinalSuffix(ordinalKeyBased.getOrdinalKey());
      return new SingleSuffixOrdinalIndexLocator(suffix);
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
      final var suffix = "exporter" + ordinalIndexManager.getOrCreateOrdinalSuffix(ordinal);
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

  private Map<String, Integer> extractOrdinalsByKey(final BatchOperationChunkRecordValue chunks) {
    // TODO this is not a great approach. In the future we will want to handlers to deal with this
    // themselves.
    final Map<String, Integer> idsToPIs = new HashMap<>();
    idsToPIs.put(String.valueOf(chunks.getBatchOperationKey()), chunks.getOrdinalKey());
    idsToPIs.put(BatchOperationChunkCreatedHandler.generateId(chunks), chunks.getOrdinalKey());
    for (final var item : chunks.getItems()) {
      final var ordinal = item.getOrdinalKey();
      idsToPIs.put(
          BatchOperationChunkCreatedItemHandler.generateId(
              chunks.getBatchOperationKey(), item.getItemKey()),
          ordinal);
      idsToPIs.put(ListViewFromChunkItemHandler.generateId(chunks, item), ordinal);
    }
    return idsToPIs;
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

  static class NoopIndexLocator implements IndexLocator {

    @Override
    public String getIndexLocation(final ExporterEntity<?> entity, final String baseIndexName) {
      return baseIndexName;
    }
  }

  class SingleSuffixOrdinalIndexLocator implements IndexLocator {
    private final String suffix;

    public SingleSuffixOrdinalIndexLocator(final String suffix) {
      this.suffix = suffix;
    }

    @Override
    public String getIndexLocation(final ExporterEntity<?> entity, final String baseIndexName) {
      if (!ordinalIndexManager.isOrdinalBasedIndex(baseIndexName)) {
        return baseIndexName;
      }
      return baseIndexName + suffix;
    }
  }

  class MultiSuffixOrdinalIndexLocator implements IndexLocator {
    private final Map<String, String> suffixesById;

    public MultiSuffixOrdinalIndexLocator(final Map<String, String> suffixesById) {
      this.suffixesById = suffixesById;
    }

    @Override
    public String getIndexLocation(final ExporterEntity<?> entity, final String baseIndexName) {
      if (!ordinalIndexManager.isOrdinalBasedIndex(baseIndexName)) {
        return baseIndexName;
      }
      return baseIndexName + Objects.requireNonNull(suffixesById.get(entity.getId()));
    }
  }
}
