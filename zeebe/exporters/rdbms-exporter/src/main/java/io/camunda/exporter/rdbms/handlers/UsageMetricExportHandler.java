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

import io.camunda.db.rdbms.write.domain.UsageMetricDbModel;
import io.camunda.db.rdbms.write.domain.UsageMetricDbModel.EventTypeDbModel;
import io.camunda.db.rdbms.write.service.UsageMetricWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsageMetricExportHandler implements RdbmsExportHandler<UsageMetricRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(UsageMetricExportHandler.class);

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
    mapRecord(record).forEach(usageMetricWriter::create);
  }

  private List<UsageMetricDbModel> mapRecord(final Record<UsageMetricRecordValue> record) {
    final var value = record.getValue();
    final List<UsageMetricDbModel> list = new ArrayList<>();
    for (final Entry<String, Long> entry : value.getCounterValues().entrySet()) {
      final var eventType = mapEventType(value.getEventType());
      if (eventType != null) {
        final UsageMetricDbModel usageMetricDbModel =
            new UsageMetricDbModel(
                record.getKey(),
                DateUtil.toOffsetDateTime(value.getStartTime()),
                entry.getKey(),
                eventType,
                entry.getValue(),
                record.getPartitionId());
        list.add(usageMetricDbModel);
      } else {
        LOGGER.warn("Unsupported event type: {}", value.getEventType());
      }
    }
    return list;
  }

  private EventTypeDbModel mapEventType(final EventType eventType) {
    return switch (eventType) {
      case RPI -> RPI;
      case EDI -> EDI;
      default -> null;
    };
  }
}
