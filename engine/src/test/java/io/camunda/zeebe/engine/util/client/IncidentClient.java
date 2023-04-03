/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;

public final class IncidentClient {

  private final CommandWriter writer;

  public IncidentClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public ResolveIncidentClient ofInstance(final long processInstanceKey) {
    return new ResolveIncidentClient(writer, processInstanceKey);
  }

  public static class ResolveIncidentClient {
    private static final long DEFAULT_KEY = -1L;

    private final CommandWriter writer;
    private final long processInstanceKey;
    private final IncidentRecord incidentRecord;

    private long incidentKey = DEFAULT_KEY;

    public ResolveIncidentClient(final CommandWriter writer, final long processInstanceKey) {
      this.writer = writer;
      this.processInstanceKey = processInstanceKey;
      incidentRecord = new IncidentRecord();
    }

    public ResolveIncidentClient withKey(final long incidentKey) {
      this.incidentKey = incidentKey;
      return this;
    }

    public Record<IncidentRecordValue> resolve() {
      if (incidentKey == DEFAULT_KEY) {
        incidentKey =
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getKey();
      }

      final long position =
          writer.writeCommandOnPartition(
              Protocol.decodePartitionId(incidentKey),
              incidentKey,
              IncidentIntent.RESOLVE,
              incidentRecord);

      return RecordingExporter.incidentRecords()
          .withProcessInstanceKey(processInstanceKey)
          .withRecordKey(incidentKey)
          .withSourceRecordPosition(position)
          .withIntent(IncidentIntent.RESOLVED)
          .getFirst();
    }
  }
}
