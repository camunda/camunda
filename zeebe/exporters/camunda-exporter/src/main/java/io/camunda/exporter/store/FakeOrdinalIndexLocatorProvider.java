/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.AuditLogProcessInstanceRelated;
import java.util.Set;

public class FakeOrdinalIndexLocatorProvider implements IndexLocatorProvider {
  private final Set<String> ordinalBasedIndexes;
  private final NoopIndexLocator noopIndexLocator = new NoopIndexLocator();

  public FakeOrdinalIndexLocatorProvider(final Set<String> ordinalBasedIndexes) {
    this.ordinalBasedIndexes = ordinalBasedIndexes;
  }

  @Override
  public IndexLocator createIndexLocator(final Record<?> record) {
    if (record.getValue() instanceof final AuditLogProcessInstanceRelated processInstanceRelated) {
      final long rootProcessInstanceKey = processInstanceRelated.getRootProcessInstanceKey();
      if (rootProcessInstanceKey > 0) {
        return new FixedOrdinalIndexLocator((int) (rootProcessInstanceKey % 2));
      }
    }
    return noopIndexLocator;
  }

  static class NoopIndexLocator implements IndexLocator {

    @Override
    public String getIndexLocation(final ExporterEntity<?> entity, final String baseIndexName) {
      return baseIndexName;
    }
  }

  class FixedOrdinalIndexLocator implements IndexLocator {
    private final int ordinal;

    public FixedOrdinalIndexLocator(final int ordinal) {
      this.ordinal = ordinal;
    }

    @Override
    public String getIndexLocation(final ExporterEntity<?> entity, final String baseIndexName) {
      if (!ordinalBasedIndexes.contains(baseIndexName)) {
        return baseIndexName;
      }
      return baseIndexName + "ord%05d".formatted(ordinal);
    }
  }
}
