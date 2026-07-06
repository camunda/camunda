/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.catalog;

import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.MeterIdStore;
import io.camunda.analytics.report.ReportDefinition;
import io.camunda.analytics.serving.spi.DatasetSpecQuery;
import io.camunda.analytics.serving.spi.DatasetSpecStore;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.analytics.serving.spi.ReportSpecStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A non-durable {@link MetadataStore} for serving-layer unit tests: an in-memory spec store plus
 * the shared {@link InMemoryMeterIdStore}. Backend-free, so tests of control-plane logic
 * (provisioning, catalog) don't need RDBMS/ES fixtures.
 */
final class InMemoryMetadataStore implements MetadataStore {

  private final MeterIdStore meterIdStore = new InMemoryMeterIdStore();
  private final DatasetSpecStore datasetSpecStore = new InMemoryDatasetSpecStore();
  private final ReportSpecStore reportSpecStore = new InMemoryReportSpecStore();

  @Override
  public void migrate() {}

  @Override
  public MeterIdStore meterIdStore() {
    return meterIdStore;
  }

  @Override
  public DatasetSpecStore datasetSpecStore() {
    return datasetSpecStore;
  }

  @Override
  public ReportSpecStore reportSpecStore() {
    return reportSpecStore;
  }

  @Override
  public void close() {}

  private static final class InMemoryDatasetSpecStore implements DatasetSpecStore {

    private final Map<Long, RegisteredDataset> byId = new LinkedHashMap<>();

    @Override
    public boolean isEmpty() {
      return byId.isEmpty();
    }

    @Override
    public void create(final RegisteredDataset spec) {
      if (byId.putIfAbsent(spec.cubeId(), spec) != null) {
        throw new IllegalStateException("duplicate cubeId " + spec.cubeId());
      }
    }

    @Override
    public Optional<RegisteredDataset> read(final long cubeId) {
      return Optional.ofNullable(byId.get(cubeId));
    }

    @Override
    public List<RegisteredDataset> search(final DatasetSpecQuery query) {
      final List<RegisteredDataset> out = new ArrayList<>();
      for (final RegisteredDataset spec : byId.values()) {
        final DatasetDeclaration d = spec.declaration();
        if ((query.name() == null || query.name().equals(d.name()))
            && (query.sourceFact() == null || query.sourceFact() == d.sourceFact())
            && (query.kind() == null || query.kind() == d.kind())) {
          out.add(spec);
        }
      }
      return out;
    }
  }

  private static final class InMemoryReportSpecStore implements ReportSpecStore {

    private final Map<Long, ReportDefinition> byId = new LinkedHashMap<>();

    @Override
    public void create(final ReportDefinition report) {
      final long reportId = byId.keySet().stream().mapToLong(Long::longValue).max().orElse(0L) + 1;
      byId.put(
          reportId,
          new ReportDefinition(
              reportId,
              report.name(),
              report.sources(),
              report.groupBy(),
              report.granularityMs(),
              report.combination(),
              report.viz()));
    }

    @Override
    public Optional<ReportDefinition> read(final long reportId) {
      return Optional.ofNullable(byId.get(reportId));
    }

    @Override
    public List<ReportDefinition> search() {
      return new ArrayList<>(byId.values());
    }

    @Override
    public void delete(final long reportId) {
      byId.remove(reportId);
    }
  }
}
