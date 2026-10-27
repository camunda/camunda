/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.waitstate;

import io.camunda.db.rdbms.write.service.WaitStateWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.WaitStateRelated;
import io.camunda.zeebe.util.VisibleForTesting;

/**
 * Deletes a wait-state row when a process element leaves its waiting state (e.g. job completed,
 * user task completed). The row is identified by the same stable entity key used on insertion.
 *
 * @param <R> the record value type handled by the injected transformer
 */
public class WaitStateRemoveHandler<R extends RecordValue & WaitStateRelated>
    implements RdbmsExportHandler<R> {

  private final WaitStateWriter waitStateWriter;
  private final WaitStateTransformer<R> transformer;

  public WaitStateRemoveHandler(
      final WaitStateWriter waitStateWriter, final WaitStateTransformer<R> transformer) {
    this.waitStateWriter = waitStateWriter;
    this.transformer = transformer;
  }

  @Override
  public boolean canExport(final Record<R> record) {
    return transformer.triggersRemoval(record);
  }

  @Override
  public void export(final Record<R> record) {
    waitStateWriter.delete(record.getKey());
  }

  @VisibleForTesting
  public WaitStateTransformer<R> getTransformer() {
    return transformer;
  }
}
