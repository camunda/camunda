/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.db.rdbms.write.domain.UsageMetricDbModel.EventTypeDbModel.EDI;
import static io.camunda.db.rdbms.write.domain.UsageMetricDbModel.EventTypeDbModel.RPI;
import static io.camunda.db.rdbms.write.domain.UsageMetricDbModel.EventTypeDbModel.TU;

import io.camunda.db.rdbms.write.domain.UsageMetricDbModel;
import io.camunda.db.rdbms.write.domain.UsageMetricDbModel.EventTypeDbModel;
import io.camunda.db.rdbms.write.domain.UsageMetricTUDbModel;
import io.camunda.db.rdbms.write.service.UsageMetricTUWriter;
import io.camunda.db.rdbms.write.service.UsageMetricWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record UsageMetricExportHandler(
    UsageMetricWriter usageMetricWriter, UsageMetricTUWriter usageMetricTUWriter)
    implements RdbmsExportHandler<UsageMetricRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(UsageMetricExportHandler.class);

  @Override
  public boolean canExport(final Record<UsageMetricRecordValue> record) {
    return UsageMetricIntent.EXPORTED.equals(record.getIntent())
        && !EventType.NONE.equals(record.getValue().getEventType());
  }

  @Override
  public void export(final Record<UsageMetricRecordValue> usageMetricRecordValue) {
    final var dbModelTuple = mapRecord(usageMetricRecordValue);
    dbModelTuple.getLeft().forEach(usageMetricWriter::create);
    dbModelTuple.getRight().forEach(usageMetricTUWriter::create);
  }

  private Tuple<List<UsageMetricDbModel>, List<UsageMetricTUDbModel>> mapRecord(
      final Record<UsageMetricRecordValue> usageMetricRecordValue) {
    final var value = usageMetricRecordValue.getValue();
    final var eventType = mapEventType(value.getEventType());

    if (eventType == null) {
      LOGGER.warn("Unsupported event type: {}", value.getEventType());
      return new Tuple<>(List.of(), List.of());
    }

    final var startTime = DateUtil.toOffsetDateTime(value.getStartTime());
    final var endTime = DateUtil.toOffsetDateTime(value.getEndTime());
    final var recordKey = usageMetricRecordValue.getKey();
    final var partitionId = usageMetricRecordValue.getPartitionId();

    final var usageMetricList = new ArrayList<UsageMetricDbModel>();
    final var usageMetricTUList = new ArrayList<UsageMetricTUDbModel>();

    value
        .getCounterValues()
        .forEach(
            (key, val) ->
                usageMetricList.add(
                    new UsageMetricDbModel.Builder()
                        .key(recordKey)
                        .startTime(startTime)
                        .endTime(endTime)
                        .tenantId(key)
                        .eventType(eventType)
                        .value(val)
                        .partitionId(partitionId)
                        .build()));
    value.getSetValues().entrySet().stream()
        .flatMap(
            entry ->
                entry.getValue().stream()
                    .map(
                        val ->
                            new UsageMetricTUDbModel.Builder()
                                .key(recordKey)
                                .startTime(startTime)
                                .endTime(endTime)
                                .tenantId(entry.getKey())
                                .assigneeHash(val)
                                .partitionId(partitionId)
                                .build()))
        .forEach(usageMetricTUList::add);

    return new Tuple<>(usageMetricList, usageMetricTUList);
  }

  private EventTypeDbModel mapEventType(final EventType eventType) {
    return switch (eventType) {
      case RPI -> RPI;
      case EDI -> EDI;
      case TU -> TU;
      default -> null;
    };
  }
}
