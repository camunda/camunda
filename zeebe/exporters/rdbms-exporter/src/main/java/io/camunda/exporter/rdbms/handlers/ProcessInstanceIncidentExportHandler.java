/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.service.ProcessInstanceWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceIncidentExportHandler
    implements RdbmsExportHandler<IncidentRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceIncidentExportHandler.class);

  private final ProcessInstanceWriter processInstanceWriter;

  public ProcessInstanceIncidentExportHandler(final ProcessInstanceWriter processInstanceWriter) {
    this.processInstanceWriter = processInstanceWriter;
  }

  @Override
  public boolean canExport(final Record<IncidentRecordValue> record) {
    return record.getValue().getProcessInstanceKey() > 0
        && (record.getIntent() == IncidentIntent.CREATED
            || record.getIntent() == IncidentIntent.RESOLVED);
  }

  @Override
  public void export(final Record<IncidentRecordValue> record) {
    final var value = record.getValue();

    for (final List<Long> elementPair : value.getElementInstancePath()) {
      final var processInstanceKey = elementPair.getFirst();
      if (record.getIntent().equals(IncidentIntent.CREATED)) {
        processInstanceWriter.createIncident(processInstanceKey);
      } else if (record.getIntent().equals(IncidentIntent.RESOLVED)) {
        processInstanceWriter.resolveIncident(processInstanceKey);
      } else {
        LOGGER.warn(
            "Unexpected incident intent {} for record {}/{}",
            record.getIntent(),
            record.getPartitionId(),
            record.getPosition());
      }
    }
  }
}
