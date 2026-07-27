/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.usage;

import static io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType.EDI;
import static io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType.RPI;
import static io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType.TU;

import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.usage.AbstractUsageMetricExportedHandler.Batch;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractUsageMetricExportedHandler<
        T extends ExporterEntity<T>, B extends Batch<B, T>>
    implements ExportHandler<B, UsageMetricRecordValue> {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final String indexName;

  public AbstractUsageMetricExportedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.USAGE_METRIC;
  }

  @Override
  public boolean handlesRecord(final Record<UsageMetricRecordValue> record) {
    return UsageMetricIntent.EXPORTED.equals(record.getIntent())
        && !EventType.NONE.equals(record.getValue().getEventType());
  }

  @Override
  public List<String> generateIds(final Record<UsageMetricRecordValue> record) {
    return Collections.singletonList(String.valueOf(record.getKey()));
  }

  @Override
  public void updateEntity(final Record<UsageMetricRecordValue> record, final B batch) {

    final var recordValue = record.getValue();
    final var eventType = mapEventType(recordValue.getEventType());

    if (eventType == null) {
      logger.warn("Unsupported event type: {}", recordValue.getEventType());
      return;
    }

    extractMetrics(record, eventType).forEach(batch::addMetric);
  }

  @Override
  public void flush(final TargetIndex index, final B batch, final BatchRequest batchRequest) {
    batch.getMetrics().forEach(e -> batchRequest.add(index, e));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  protected abstract Stream<T> extractMetrics(
      Record<UsageMetricRecordValue> record, UsageMetricsEventType eventType);

  private UsageMetricsEventType mapEventType(final EventType eventType) {
    return switch (eventType) {
      case RPI -> RPI;
      case EDI -> EDI;
      case TU -> TU;
      default -> null;
    };
  }

  abstract static class Batch<B extends Batch<B, T>, T extends ExporterEntity<T>>
      implements ExporterEntity<B> {
    private final String id;
    private final List<T> metrics = new ArrayList<>();

    Batch(final String id) {
      this.id = id;
    }

    void addMetric(final T metric) {
      metrics.add(metric);
    }

    List<T> getMetrics() {
      return metrics;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public B setId(final String id) {
      throw new UnsupportedOperationException("Not allowed to set an id");
    }
  }
}
