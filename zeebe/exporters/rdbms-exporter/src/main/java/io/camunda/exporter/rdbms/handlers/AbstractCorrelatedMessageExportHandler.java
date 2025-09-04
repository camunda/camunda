/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.exporter.rdbms.utils.DateUtil.toOffsetDateTime;

import io.camunda.db.rdbms.write.domain.CorrelatedMessageDbModel;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageDbModel.Builder;
import io.camunda.db.rdbms.write.service.CorrelatedMessageWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.time.Instant;

public abstract class AbstractCorrelatedMessageExportHandler<T extends RecordValue>
    implements RdbmsExportHandler<T> {

  private final CorrelatedMessageWriter correlatedMessageWriter;

  public AbstractCorrelatedMessageExportHandler(
      final CorrelatedMessageWriter correlatedMessageWriter) {
    this.correlatedMessageWriter = correlatedMessageWriter;
  }

  @Override
  public void export(final Record<T> record) {
    correlatedMessageWriter.create(map(record));
  }

  protected abstract void mapValue(final T value, final CorrelatedMessageDbModel.Builder builder);

  private CorrelatedMessageDbModel map(final Record<T> record) {
    final CorrelatedMessageDbModel.Builder builder =
        new Builder()
            .correlationTime(toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
            .partitionId(record.getPartitionId())
            .subscriptionKey(record.getKey());
    mapValue(record.getValue(), builder);
    return builder.build();
  }
}
