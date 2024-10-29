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
import io.camunda.zeebe.protocol.record.value.EntityType;
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

  /**
   * Creates a new {@link TenantAddEntityClient} for adding an entity to a tenant. The client uses
   * the internal command writer to submit the add entity commands.
   *
   * @param tenantKey the key of the tenant
   * @return a new instance of {@link TenantAddEntityClient}
   */
  public TenantAddEntityClient addEntity(final long tenantKey) {
    return new TenantAddEntityClient(writer, tenantKey);
  }

  /**
   * Creates a new {@link TenantRemoveEntityClient} for removing an entity from a tenant. The client
   * uses the internal command writer to submit the remove entity commands.
   *
   * <p>This operation is used when a specific entity (e.g., a user) needs to be disassociated from
   * a tenant. The entity type and entity key must be provided through the {@link
   * TenantRemoveEntityClient}.
   *
   * @param tenantKey the key of the tenant from which the entity will be removed
   * @return a new instance of {@link TenantRemoveEntityClient}
   */
  public TenantRemoveEntityClient removeEntity(final long tenantKey) {
    return new TenantRemoveEntityClient(writer, tenantKey);
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

  public static class TenantAddEntityClient {

    private static final Function<Long, Record<TenantRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.tenantRecords()
                .withIntent(TenantIntent.ENTITY_ADDED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<TenantRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.tenantRecords()
                .onlyCommandRejections()
                .withIntent(TenantIntent.ADD_ENTITY)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final TenantRecord tenantRecord;
    private Function<Long, Record<TenantRecordValue>> expectation = SUCCESS_SUPPLIER;

    public TenantAddEntityClient(final CommandWriter writer, final long tenantKey) {
      this.writer = writer;
      tenantRecord = new TenantRecord();
      tenantRecord.setTenantKey(tenantKey);
    }

    public TenantAddEntityClient withEntityKey(final long entityKey) {
      tenantRecord.setEntityKey(entityKey);
      return this;
    }

    public TenantAddEntityClient withEntityType(final EntityType entityType) {
      tenantRecord.setEntityType(entityType);
      return this;
    }

    public Record<TenantRecordValue> add() {
      final long position = writer.writeCommand(TenantIntent.ADD_ENTITY, tenantRecord);
      return expectation.apply(position);
    }

    public TenantAddEntityClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class TenantRemoveEntityClient {

    private static final Function<Long, Record<TenantRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.tenantRecords()
                .withIntent(TenantIntent.ENTITY_REMOVED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<TenantRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.tenantRecords()
                .onlyCommandRejections()
                .withIntent(TenantIntent.REMOVE_ENTITY)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final TenantRecord tenantRecord;
    private Function<Long, Record<TenantRecordValue>> expectation = SUCCESS_SUPPLIER;

    public TenantRemoveEntityClient(final CommandWriter writer, final long tenantKey) {
      this.writer = writer;
      tenantRecord = new TenantRecord();
      tenantRecord.setTenantKey(tenantKey);
    }

    public TenantRemoveEntityClient withEntityKey(final long entityKey) {
      tenantRecord.setEntityKey(entityKey);
      return this;
    }

    public TenantRemoveEntityClient withEntityType(final EntityType entityType) {
      tenantRecord.setEntityType(entityType);
      return this;
    }

    public Record<TenantRecordValue> remove() {
      final long position = writer.writeCommand(TenantIntent.REMOVE_ENTITY, tenantRecord);
      return expectation.apply(position);
    }

    public TenantRemoveEntityClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
