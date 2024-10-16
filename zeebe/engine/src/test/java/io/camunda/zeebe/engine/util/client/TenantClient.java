/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public class TenantClient {

  private final CommandWriter writer;

  public TenantClient(final CommandWriter writer) {
    this.writer = writer;
  }

  /**
   * Creates a new {@link TenantCreationClient} for creating a tenant. The client uses the internal
   * command writer to submit tenant creation commands.
   *
   * @return a new instance of {@link TenantCreationClient}
   */
  public TenantCreationClient newTenant() {
    return new TenantCreationClient(writer);
  }

  public static class TenantCreationClient {

    private static final Function<Long, Record<TenantRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.tenantRecords()
                .withIntent(TenantIntent.CREATED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<TenantRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.tenantRecords()
                .onlyCommandRejections()
                .withIntent(TenantIntent.CREATE)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final TenantRecord tenantRecord;
    private Function<Long, Record<TenantRecordValue>> expectation = SUCCESS_SUPPLIER;

    public TenantCreationClient(final CommandWriter writer) {
      this.writer = writer;
      tenantRecord = new TenantRecord();
    }

    /**
     * Sets the tenantId for the tenant record.
     *
     * @param tenantId the ID to set for the tenant
     * @return this instance
     */
    public TenantCreationClient withTenantId(final String tenantId) {
      tenantRecord.setTenantId(tenantId);
      return this;
    }

    /**
     * Sets the name for the tenant record.
     *
     * @param name the name of the tenant
     * @return this instance
     */
    public TenantCreationClient withName(final String name) {
      tenantRecord.setName(name);
      return this;
    }

    /**
     * Sets the entityKey for the tenant record.
     *
     * @param entityKey the name of the tenant
     * @return this instance
     */
    public TenantCreationClient withEntityKey(final Long entityKey) {
      tenantRecord.setEntityKey(entityKey);
      return this;
    }

    /**
     * Creates the tenant record and returns the resulting record.
     *
     * @return the created tenant record
     */
    public Record<TenantRecordValue> create() {
      final long position = writer.writeCommand(TenantIntent.CREATE, tenantRecord);
      return expectation.apply(position);
    }

    /**
     * Expects the tenant creation to be rejected.
     *
     * @return this instance with rejection expectation
     */
    public TenantCreationClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
