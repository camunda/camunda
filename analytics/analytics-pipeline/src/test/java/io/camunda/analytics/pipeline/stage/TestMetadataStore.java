/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A non-durable {@link MetadataStore} for stage-reload tests. Beyond the plain in-memory spec
 * store, it can {@link #hide}/{@link #unhide} a dataset — simulating a removal (and a re-add) so
 * the incremental-reload paths can be exercised even though the SPI itself is create-only.
 */
final class TestMetadataStore implements MetadataStore {

  private final MeterIdStore meterIdStore = new InMemoryMeterIdStore();
  private final TestSpecStore specStore = new TestSpecStore();

  @Override
  public void migrate() {}

  @Override
  public MeterIdStore meterIdStore() {
    return meterIdStore;
  }

  @Override
  public DatasetSpecStore datasetSpecStore() {
    return specStore;
  }

  @Override
  public ReportSpecStore reportSpecStore() {
    return new NoopReportSpecStore();
  }

  @Override
  public void close() {}

  /** Simulates removing a dataset: it disappears from search and the change probe. */
  void hide(final long cubeId) {
    specStore.hidden.add(cubeId);
  }

  /** Simulates re-adding a previously removed dataset under its original id. */
  void unhide(final long cubeId) {
    specStore.hidden.remove(cubeId);
  }

  private static final class TestSpecStore implements DatasetSpecStore {

    private final Map<Long, RegisteredDataset> byId = new LinkedHashMap<>();
    private final Set<Long> hidden = new HashSet<>();

    @Override
    public boolean isEmpty() {
      return specCount() == 0;
    }

    @Override
    public long specCount() {
      return byId.keySet().stream().filter(id -> !hidden.contains(id)).count();
    }

    @Override
    public void create(final RegisteredDataset spec) {
      if (byId.putIfAbsent(spec.cubeId(), spec) != null) {
        throw new IllegalStateException("duplicate cubeId " + spec.cubeId());
      }
    }

    @Override
    public Optional<RegisteredDataset> read(final long cubeId) {
      return hidden.contains(cubeId) ? Optional.empty() : Optional.ofNullable(byId.get(cubeId));
    }

    @Override
    public List<RegisteredDataset> search(final DatasetSpecQuery query) {
      final List<RegisteredDataset> out = new ArrayList<>();
      for (final RegisteredDataset spec : byId.values()) {
        if (hidden.contains(spec.cubeId())) {
          continue;
        }
        final DatasetDeclaration declaration = spec.declaration();
        if ((query.name() == null || query.name().equals(declaration.name()))
            && (query.sourceFact() == null || query.sourceFact() == declaration.sourceFact())
            && (query.kind() == null || query.kind() == declaration.kind())) {
          out.add(spec);
        }
      }
      return out;
    }
  }

  private static final class NoopReportSpecStore implements ReportSpecStore {

    @Override
    public ReportDefinition create(final ReportDefinition report) {
      throw new UnsupportedOperationException("reports are not part of the stage-reload tests");
    }

    @Override
    public Optional<ReportDefinition> read(final long reportId) {
      return Optional.empty();
    }

    @Override
    public List<ReportDefinition> search() {
      return List.of();
    }

    @Override
    public void delete(final long reportId) {}
  }
}
