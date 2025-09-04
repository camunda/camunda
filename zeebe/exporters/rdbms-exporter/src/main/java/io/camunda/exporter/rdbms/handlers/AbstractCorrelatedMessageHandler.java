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
import io.camunda.db.rdbms.write.service.CorrelatedMessageWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.time.Instant;
import java.util.Set;

public abstract class AbstractCorrelatedMessageHandler<T extends RecordValue> implements RdbmsExportHandler<T> {

  protected final CorrelatedMessageWriter correlatedMessageWriter;

  public AbstractCorrelatedMessageHandler(final CorrelatedMessageWriter correlatedMessageWriter) {
    this.correlatedMessageWriter = correlatedMessageWriter;
  }

  @Override
  public boolean canExport(final Record<T> record) {
    return getSupportedIntents().contains(record.getIntent());
  }

  @Override
  public void export(final Record<T> record) {
    if (getSupportedIntents().contains(record.getIntent())) {
      correlatedMessageWriter.create(map(record));
    }
  }

  protected abstract Set<Intent> getSupportedIntents();

  protected abstract CorrelatedMessageDbModel map(Record<T> record);

  protected CorrelatedMessageDbModel.CorrelatedMessageDbModelBuilder buildBaseModel(final Record<T> record) {
    return new CorrelatedMessageDbModel.CorrelatedMessageDbModelBuilder()
        .correlationTime(toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .partitionId(record.getPartitionId());
  }
}