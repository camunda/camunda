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
   * @param tenantId the id of the tenant to be updated
   * @return a new instance of {@link TenantUpdateClient}
   */
  public TenantUpdateClient updateTenant(final String tenantId) {
    return new TenantUpdateClient(writer, tenantId);
  }

  /**
   * Creates a new {@link TenantAddEntityClient} for adding an entity to a tenant. The client uses
   * the internal command writer to submit the add entity commands.
   *
   * @param tenantId the id of the tenant
   * @return a new instance of {@link TenantAddEntityClient}
   */
  public TenantAddEntityClient addEntity(final String tenantId) {
    return new TenantAddEntityClient(writer, tenantId);
  }

  /**
   * Creates a new {@link TenantRemoveEntityClient} for removing an entity from a tenant. The client
   * uses the internal command writer to submit the remove entity commands.
   *
   * <p>This operation is used when a specific entity (e.g., a user) needs to be disassociated from
   * a tenant. The entity type and entity id must be provided through the {@link
   * TenantRemoveEntityClient}.
   *
   * @param tenantId the id of the tenant from which the entity will be removed
   * @return a new instance of {@link TenantRemoveEntityClient}
   */
  public TenantRemoveEntityClient removeEntity(final String tenantId) {
    return new TenantRemoveEntityClient(writer, tenantId);
  }

  /**
   * Creates a new {@link TenantDeleteClient} for deleting a tenant. The client uses the internal
   * command writer to submit the delete tenant commands.
   *
   * @param tenantId the id of the tenant to be deleted
   * @return a new instance of {@link TenantDeleteClient}
   */
  public TenantDeleteClient deleteTenant(final String tenantId) {
    return new TenantDeleteClient(writer, tenantId);
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

    public TenantUpdateClient(final CommandWriter writer, final String tenantId) {
      this.writer = writer;
      tenantRecord = new TenantRecord();
      tenantRecord.setTenantId(tenantId);
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

    public TenantAddEntityClient(final CommandWriter writer, final String tenantId) {
      this.writer = writer;
      tenantRecord = new TenantRecord();
      tenantRecord.setTenantId(tenantId);
    }

    public TenantAddEntityClient withEntityId(final String entityId) {
      tenantRecord.setEntityId(entityId);
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

    public TenantRemoveEntityClient(final CommandWriter writer, final String tenantId) {
      this.writer = writer;
      tenantRecord = new TenantRecord();
      tenantRecord.setTenantId(tenantId);
    }

    public TenantRemoveEntityClient withEntityId(final String entityId) {
      tenantRecord.setEntityId(entityId);
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

  public static class TenantDeleteClient {

    private static final Function<Long, Record<TenantRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.tenantRecords()
                .withIntent(TenantIntent.DELETED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<TenantRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.tenantRecords()
                .onlyCommandRejections()
                .withIntent(TenantIntent.DELETE)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final TenantRecord tenantRecord;
    private Function<Long, Record<TenantRecordValue>> expectation = SUCCESS_SUPPLIER;

    public TenantDeleteClient(final CommandWriter writer, final String tenantId) {
      this.writer = writer;
      tenantRecord = new TenantRecord();
      tenantRecord.setTenantId(tenantId);
    }

    /**
     * Submits the delete command for the tenant and returns the resulting record.
     *
     * @return the deleted tenant record
     */
    public Record<TenantRecordValue> delete() {
      final long position = writer.writeCommand(TenantIntent.DELETE, tenantRecord);
      return expectation.apply(position);
    }

    /**
     * Expects the tenant deletion to be rejected.
     *
     * @return this instance with rejection expectation
     */
    public TenantDeleteClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
