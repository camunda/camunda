/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.UsageMetricDbModel;
import io.camunda.db.rdbms.write.service.UsageMetricWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import java.util.List;

public class UsageMetricExportHandler implements RdbmsExportHandler<UsageMetricRecordValue> {

  private final UsageMetricWriter usageMetricWriter;

  public UsageMetricExportHandler(final UsageMetricWriter usageMetricWriter) {
    this.usageMetricWriter = usageMetricWriter;
  }

  @Override
  public boolean canExport(final Record<UsageMetricRecordValue> record) {
    return record.getIntent() != null
        && record.getIntent() instanceof final UsageMetricIntent intent
        && intent.equals(UsageMetricIntent.EXPORTED)
        && !record.getValue().getEventType().equals(EventType.NONE);
  }

  @Override
  public void export(final Record<UsageMetricRecordValue> record) {
    final UsageMetricRecordValue value = record.getValue();
    System.out.println("value = " + value);
    map(record).forEach(usageMetricWriter::create);
  }

  private List<UsageMetricDbModel> map(final Record<UsageMetricRecordValue> record) {
    final var value = record.getValue();
    return value.getValues().entrySet().stream()
        .map(
            entry ->
                new UsageMetricDbModel(
                    DateUtil.toOffsetDateTime(value.getStartTime()),
                    DateUtil.toOffsetDateTime(value.getEndTime()),
                    entry.getKey(),
                    value.getEventType(),
                    entry.getValue(),
                    record.getPartitionId(),
                    null))
        .toList();
  }
}
