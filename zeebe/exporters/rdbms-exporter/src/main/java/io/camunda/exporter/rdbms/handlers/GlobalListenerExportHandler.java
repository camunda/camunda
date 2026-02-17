/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel;
import io.camunda.db.rdbms.write.service.GlobalListenerWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;

public class GlobalListenerExportHandler implements RdbmsExportHandler<GlobalListenerRecordValue> {

  private final GlobalListenerWriter globalListenerWriter;

  public GlobalListenerExportHandler(final GlobalListenerWriter globalListenerWriter) {
    this.globalListenerWriter = globalListenerWriter;
  }

  @Override
  public boolean canExport(final Record<GlobalListenerRecordValue> record) {
    return record.getValueType() == ValueType.GLOBAL_LISTENER
        && (record.getIntent() == GlobalListenerIntent.CREATED
            || record.getIntent() == GlobalListenerIntent.UPDATED
            || record.getIntent() == GlobalListenerIntent.DELETED);
  }

  @Override
  public void export(final Record<GlobalListenerRecordValue> record) {
    if (record.getIntent() == GlobalListenerIntent.CREATED) {
      globalListenerWriter.create(map(record));
    } else if (record.getIntent() == GlobalListenerIntent.UPDATED) {
      globalListenerWriter.update(map(record));
    } else if (record.getIntent() == GlobalListenerIntent.DELETED) {
      globalListenerWriter.delete(map(record));
    }
  }

  private GlobalListenerDbModel map(final Record<GlobalListenerRecordValue> record) {
    final var listener = record.getValue();
    final var builder =
        new GlobalListenerDbModel.GlobalListenerDbModelBuilder()
            .listenerId(listener.getId())
            .type(listener.getType())
            .retries(listener.getRetries())
            .eventTypes(listener.getEventTypes())
            .afterNonGlobal(listener.isAfterNonGlobal())
            .priority(listener.getPriority())
            .source(GlobalListenerSource.valueOf(listener.getSource().name()))
            .listenerType(GlobalListenerType.valueOf(listener.getListenerType().name()));
    return builder.build();
  }
}
