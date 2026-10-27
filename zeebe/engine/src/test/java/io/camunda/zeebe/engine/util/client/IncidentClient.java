/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.function.Function;

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

    private static final Function<Long, Record<IncidentRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.incidentRecords()
                .withSourceRecordPosition(position)
                .withIntent(IncidentIntent.RESOLVED)
                .getFirst();

    private static final Function<Long, Record<IncidentRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.incidentRecords()
                .onlyCommandRejections()
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final long processInstanceKey;
    private final IncidentRecord incidentRecord;

    private long incidentKey = DEFAULT_KEY;
    private List<String> authorizedTenantIds = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    private Function<Long, Record<IncidentRecordValue>> expectation = SUCCESS_SUPPLIER;

    public ResolveIncidentClient(final CommandWriter writer, final long processInstanceKey) {
      this.writer = writer;
      this.processInstanceKey = processInstanceKey;
      incidentRecord = new IncidentRecord();
    }

    public ResolveIncidentClient withKey(final long incidentKey) {
      this.incidentKey = incidentKey;
      return this;
    }

    public ResolveIncidentClient withAuthorizedTenantIds(final String... tenantIds) {
      authorizedTenantIds = List.of(tenantIds);
      return this;
    }

    public ResolveIncidentClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
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
              incidentRecord,
              authorizedTenantIds.toArray(new String[0]));

      return expectation.apply(position);
    }

    public Record<IncidentRecordValue> resolve(final AuthInfo authorizations) {
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
              incidentRecord,
              authorizations);

      return expectation.apply(position);
    }

    public Record<IncidentRecordValue> resolve(final String username) {
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
              username,
              incidentRecord,
              TenantOwned.DEFAULT_TENANT_IDENTIFIER);

      return expectation.apply(position);
    }
  }
}
