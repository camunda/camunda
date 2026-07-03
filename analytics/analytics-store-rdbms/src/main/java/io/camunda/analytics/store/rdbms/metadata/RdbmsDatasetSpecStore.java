/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata;

import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dataset.store.DatasetSpecQuery;
import io.camunda.analytics.dataset.store.DatasetSpecStore;
import io.camunda.analytics.store.rdbms.metadata.row.SpecRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * The RDBMS {@link DatasetSpecStore}: one {@code ANALYTICS_DATASET_SPEC} row per dataset — the
 * searchable scalar columns ({@code name}/{@code source_fact}/{@code kind}) plus the full {@link
 * RegisteredDataset} as a JSON {@code SPEC} column. The spec is small, config-like, mostly
 * immutable and read wholesale, so it is stored as a document rather than normalized into child
 * tables: {@code create} inserts, {@code read} looks up by id, and {@code search} filters the
 * scalar columns via a dynamic MyBatis {@code WHERE}; the JSON is deserialized back into the domain
 * model.
 */
final class RdbmsDatasetSpecStore implements DatasetSpecStore {

  private final SqlSessionFactory sessionFactory;
  private final SpecJson specJson = new SpecJson();

  RdbmsDatasetSpecStore(final SqlSessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public boolean isEmpty() {
    try (SqlSession session = sessionFactory.openSession()) {
      return session.getMapper(DatasetSpecMapper.class).countSpecs() == 0;
    }
  }

  @Override
  public void create(final RegisteredDataset spec) {
    try (SqlSession session = sessionFactory.openSession()) {
      session
          .getMapper(DatasetSpecMapper.class)
          .insertSpec(
              new SpecRow(
                  spec.cubeId(),
                  spec.declaration().name(),
                  spec.declaration().sourceFact().name(),
                  spec.declaration().kind().name(),
                  specJson.toJson(spec)));
      session.commit();
    }
  }

  @Override
  public Optional<RegisteredDataset> read(final long cubeId) {
    try (SqlSession session = sessionFactory.openSession()) {
      final SpecRow row = session.getMapper(DatasetSpecMapper.class).selectSpecById(cubeId);
      return row == null ? Optional.empty() : Optional.of(specJson.fromJson(row.getSpec()));
    }
  }

  @Override
  public List<RegisteredDataset> search(final DatasetSpecQuery query) {
    try (SqlSession session = sessionFactory.openSession()) {
      final List<SpecRow> rows =
          session
              .getMapper(DatasetSpecMapper.class)
              .searchSpecs(
                  query.name(),
                  query.sourceFact() == null ? null : query.sourceFact().name(),
                  query.kind() == null ? null : query.kind().name());
      final List<RegisteredDataset> specs = new ArrayList<>(rows.size());
      for (final SpecRow row : rows) {
        specs.add(specJson.fromJson(row.getSpec()));
      }
      return specs;
    }
  }
}
