/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.recover;

import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * In-memory {@link BatchRequest} for unit tests. Records every added entity per index and counts
 * executions. Can be configured to fail on {@link #execute()} to exercise the failure path. Only
 * the operations used by the recovery ({@code add}, {@code execute}) are implemented.
 */
@SuppressWarnings("rawtypes")
final class FakeBatchRequest implements BatchRequest {

  final List<Added> added = new ArrayList<>();
  int executeCount;
  private final boolean failOnExecute;

  FakeBatchRequest() {
    this(false);
  }

  FakeBatchRequest(final boolean failOnExecute) {
    this.failOnExecute = failOnExecute;
  }

  List<Added> addedTo(final String index) {
    return added.stream().filter(a -> a.index().name().equals(index)).toList();
  }

  @Override
  public BatchRequest withMetrics(final CamundaExporterMetrics metrics) {
    return this;
  }

  @Override
  public BatchRequest withMaxBytes(final long maxBulkBytes) {
    return this;
  }

  @Override
  public BatchRequest add(final TargetIndex index, final ExporterEntity entity) {
    added.add(new Added(index, entity));
    return this;
  }

  // --- Unused operations ---------------------------------------------------

  @Override
  public BatchRequest addWithId(
      final TargetIndex index, final String id, final ExporterEntity entity) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BatchRequest addWithRouting(
      final TargetIndex index, final ExporterEntity entity, final String routing) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BatchRequest upsert(
      final TargetIndex index,
      final String id,
      final ExporterEntity entity,
      final Map<String, Object> updateFields) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BatchRequest upsertWithRouting(
      final TargetIndex index,
      final String id,
      final ExporterEntity entity,
      final Map<String, Object> updateFields,
      final String routing) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BatchRequest upsertWithScript(
      final TargetIndex index,
      final String id,
      final ExporterEntity entity,
      final String script,
      final Map<String, Object> parameters) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BatchRequest upsertWithScriptAndRouting(
      final TargetIndex index,
      final String id,
      final ExporterEntity entity,
      final String script,
      final Map<String, Object> parameters,
      final String routing) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BatchRequest update(
      final TargetIndex index, final String id, final Map<String, Object> updateFields) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BatchRequest update(
      final TargetIndex index, final String id, final ExporterEntity entity) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BatchRequest updateWithScript(
      final TargetIndex index,
      final String id,
      final String script,
      final Map<String, Object> parameters) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BatchRequest delete(final TargetIndex index, final String id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BatchRequest deleteWithRouting(
      final TargetIndex index, final String id, final String routing) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void execute(final BiConsumer<String, Error> customErrorHandlers)
      throws PersistenceException {
    executeCount++;
    if (failOnExecute) {
      throw new PersistenceException("simulated bulk failure");
    }
  }

  @Override
  public void executeWithRefresh() throws PersistenceException {
    execute();
  }

  record Added(TargetIndex index, ExporterEntity entity) {}
}
