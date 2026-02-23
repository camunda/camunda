/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel;
import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel.GlobalListenerDbModelBuilder;
import io.camunda.db.rdbms.write.service.GlobalListenerWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalListenerExportHandler implements RdbmsExportHandler<GlobalListenerRecordValue> {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalListenerExportHandler.class);

  private static final Set<GlobalListenerIntent> EXPORTABLE_INTENTS =
      Set.of(
          GlobalListenerIntent.CREATED, GlobalListenerIntent.UPDATED, GlobalListenerIntent.DELETED);
  private final GlobalListenerWriter globalListenerWriter;

  public GlobalListenerExportHandler(final GlobalListenerWriter globalListenerWriter) {
    this.globalListenerWriter = globalListenerWriter;
  }

  @Override
  public boolean canExport(final Record<GlobalListenerRecordValue> record) {
    return record.getValueType() == ValueType.GLOBAL_LISTENER
        && record.getIntent() instanceof final GlobalListenerIntent intent
        && EXPORTABLE_INTENTS.contains(intent);
  }

  @Override
  public void export(final Record<GlobalListenerRecordValue> record) {
    switch (record.getIntent()) {
      case GlobalListenerIntent.CREATED -> globalListenerWriter.create(map(record));
      case GlobalListenerIntent.UPDATED -> globalListenerWriter.update(map(record));
      case GlobalListenerIntent.DELETED -> globalListenerWriter.delete(map(record));
      default -> LOG.warn("Unexpected intent {} for global listener record", record.getIntent());
    }
  }

  private GlobalListenerDbModel map(final Record<GlobalListenerRecordValue> record) {
    final var listener = record.getValue();
    final var builder =
        new GlobalListenerDbModelBuilder()
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
