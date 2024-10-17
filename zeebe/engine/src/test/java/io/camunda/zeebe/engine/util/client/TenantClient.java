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

  /**
   * Creates a new {@link TenantUpdateClient} for updating a tenant. The client uses the internal
   * command writer to submit tenant update commands.
   *
   * @param tenantKey the key of the tenant to be updated
   * @return a new instance of {@link TenantUpdateClient}
   */
  public TenantUpdateClient updateTenant(final long tenantKey) {
    return new TenantUpdateClient(writer, tenantKey);
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
     * @param entityKey the key of the tenant entity
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

  public static class TenantUpdateClient {

    private static final Function<Long, Record<TenantRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.tenantRecords()
                .withIntent(TenantIntent.UPDATED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<TenantRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.tenantRecords()
                .onlyCommandRejections()
                .withIntent(TenantIntent.UPDATE)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final TenantRecord tenantRecord;
    private Function<Long, Record<TenantRecordValue>> expectation = SUCCESS_SUPPLIER;

    public TenantUpdateClient(final CommandWriter writer, final long tenantKey) {
      this.writer = writer;
      tenantRecord = new TenantRecord();
      tenantRecord.setTenantKey(tenantKey);
    }

    /**
     * Sets the tenantId for the tenant record.
     *
     * @param tenantId the ID to set for the tenant
     * @return this instance
     */
    public TenantUpdateClient withTenantId(final String tenantId) {
      tenantRecord.setTenantId(tenantId);
      return this;
    }

    /**
     * Sets the name for the tenant record.
     *
     * @param name the name of the tenant
     * @return this instance
     */
    public TenantUpdateClient withName(final String name) {
      tenantRecord.setName(name);
      return this;
    }

    /**
     * Sets the entityKey for the tenant record.
     *
     * @param entityKey the key of the tenant entity
     * @return this instance
     */
    public TenantUpdateClient withEntityKey(final Long entityKey) {
      tenantRecord.setEntityKey(entityKey);
      return this;
    }

    /**
     * Submits the update command for the tenant record and returns the updated record.
     *
     * @return the updated tenant record
     */
    public Record<TenantRecordValue> update() {
      final long position = writer.writeCommand(TenantIntent.UPDATE, tenantRecord);
      return expectation.apply(position);
    }

    /**
     * Expects the tenant update to be rejected.
     *
     * @return this instance with rejection expectation
     */
    public TenantUpdateClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
