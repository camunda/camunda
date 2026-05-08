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
import io.camunda.zeebe.protocol.record.value.AuditLogProcessInstanceRelated;
import java.util.Map;
import java.util.Set;
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
  private final Set<String> ordinalBasedIndexes;
  private final NoopIndexLocator noopIndexLocator = new NoopIndexLocator();
  private final Map<Integer, String> ordinalSuffixes = new ConcurrentHashMap<>();

  public FakeOrdinalIndexLocatorProvider(final Set<String> ordinalBasedIndexes) {
    this.ordinalBasedIndexes = ordinalBasedIndexes;
  }

  @Override
  public IndexLocator createIndexLocator(final Record<?> record) {
    if (record.getValue() instanceof final AuditLogProcessInstanceRelated processInstanceRelated) {
      final long rootProcessInstanceKey = processInstanceRelated.getRootProcessInstanceKey();
      if (rootProcessInstanceKey > 0) {
        final int ordinal = getOrdinal(rootProcessInstanceKey);
        final var suffix = getOrCreateOrdinalSuffix(ordinal);
        return new SingleSuffixOrdinalIndexLocator(suffix);
      }
    }
    return noopIndexLocator;
  }

  private static int getOrdinal(final long rootProcessInstanceKey) {
    final long key = Protocol.decodeKeyInPartition(rootProcessInstanceKey);
    return (int) (key / IDS_PER_ORDINAL);
  }

  private String getOrCreateOrdinalSuffix(final int ordinal) {
    var suffix = ordinalSuffixes.get(ordinal);
    if (suffix == null) {
      final var newSuffix = createOrdinalSuffix(ordinal);
      suffix = ordinalSuffixes.putIfAbsent(ordinal, newSuffix);
      if (suffix == null) {
        LOGGER.info("New ordinal started: {} ({} ordinals total)", ordinal, ordinalSuffixes.size());
        suffix = newSuffix;
      }
    }
    return suffix;
  }

  private String createOrdinalSuffix(final int ordinal) {
    return "ord" + Strings.padStart(String.valueOf(ordinal), 5, '0');
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
      if (!ordinalBasedIndexes.contains(baseIndexName)) {
        return baseIndexName;
      }

      return baseIndexName + suffix;
    }
  }
}
