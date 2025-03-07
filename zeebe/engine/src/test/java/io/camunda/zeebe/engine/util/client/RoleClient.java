/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public class RoleClient {

  private final CommandWriter writer;

  public RoleClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public RoleCreateClient newRole(final String name) {
    return new RoleCreateClient(writer, name);
  }

  public RoleUpdateClient updateRole(final long roleKey) {
    return new RoleUpdateClient(writer, roleKey);
  }

  public RoleAddEntityClient addEntity(final long roleKey) {
    return new RoleAddEntityClient(writer, roleKey);
  }

  public RoleRemoveEntityClient removeEntity(final long key) {
    return new RoleRemoveEntityClient(writer, key);
  }

  public RoleDeleteClient deleteRole(final long key) {
    return new RoleDeleteClient(writer, key);
  }

  public static class RoleCreateClient {

    private static final Function<Long, Record<RoleRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.roleRecords()
                .withIntent(RoleIntent.CREATED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<RoleRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.roleRecords()
                .onlyCommandRejections()
                .withIntent(RoleIntent.CREATE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final RoleRecord roleRecord;
    private Function<Long, Record<RoleRecordValue>> expectation = SUCCESS_SUPPLIER;

    public RoleCreateClient(final CommandWriter writer, final String name) {
      this.writer = writer;
      roleRecord = new RoleRecord();
      roleRecord.setName(name);
    }

    public Record<RoleRecordValue> create() {
      final long position = writer.writeCommand(RoleIntent.CREATE, roleRecord);
      return expectation.apply(position);
    }

    public Record<RoleRecordValue> create(final String username) {
      final long position = writer.writeCommand(RoleIntent.CREATE, username, roleRecord);
      return expectation.apply(position);
    }

    public RoleCreateClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class RoleUpdateClient {

    private static final Function<Long, Record<RoleRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.roleRecords()
                .withIntent(RoleIntent.UPDATED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<RoleRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.roleRecords()
                .onlyCommandRejections()
                .withIntent(RoleIntent.UPDATE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final RoleRecord roleRecord;
    private Function<Long, Record<RoleRecordValue>> expectation = SUCCESS_SUPPLIER;

    public RoleUpdateClient(final CommandWriter writer, final long roleKey) {
      this.writer = writer;
      roleRecord = new RoleRecord();
      roleRecord.setRoleKey(roleKey);
    }

    public RoleUpdateClient withName(final String name) {
      roleRecord.setName(name);
      return this;
    }

    public Record<RoleRecordValue> update() {
      final long position = writer.writeCommand(RoleIntent.UPDATE, roleRecord);
      return expectation.apply(position);
    }

    public RoleUpdateClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class RoleAddEntityClient {

    private static final Function<Long, Record<RoleRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.roleRecords()
                .withIntent(RoleIntent.ENTITY_ADDED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<RoleRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.roleRecords()
                .onlyCommandRejections()
                .withIntent(RoleIntent.ADD_ENTITY)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final RoleRecord roleRecord;
    private Function<Long, Record<RoleRecordValue>> expectation = SUCCESS_SUPPLIER;

    public RoleAddEntityClient(final CommandWriter writer, final long key) {
      this.writer = writer;
      roleRecord = new RoleRecord();
      roleRecord.setRoleKey(key);
    }

    public RoleAddEntityClient withEntityKey(final String entityKey) {
      roleRecord.setEntityKey(entityKey);
      return this;
    }

    public RoleAddEntityClient withEntityType(final EntityType entityType) {
      roleRecord.setEntityType(entityType);
      return this;
    }

    public Record<RoleRecordValue> add() {
      final long position = writer.writeCommand(RoleIntent.ADD_ENTITY, roleRecord);
      return expectation.apply(position);
    }

    public RoleAddEntityClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class RoleRemoveEntityClient {

    private static final Function<Long, Record<RoleRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.roleRecords()
                .withIntent(RoleIntent.ENTITY_REMOVED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<RoleRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.roleRecords()
                .onlyCommandRejections()
                .withIntent(RoleIntent.REMOVE_ENTITY)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final RoleRecord roleRecord;
    private Function<Long, Record<RoleRecordValue>> expectation = SUCCESS_SUPPLIER;

    public RoleRemoveEntityClient(final CommandWriter writer, final long key) {
      this.writer = writer;
      roleRecord = new RoleRecord();
      roleRecord.setRoleKey(key);
    }

    public RoleRemoveEntityClient withEntityKey(final long entityKey) {
      roleRecord.setEntityKey(entityKey);
      return this;
    }

    public RoleRemoveEntityClient withEntityType(final EntityType entityType) {
      roleRecord.setEntityType(entityType);
      return this;
    }

    public Record<RoleRecordValue> remove() {
      final long position = writer.writeCommand(RoleIntent.REMOVE_ENTITY, roleRecord);
      return expectation.apply(position);
    }

    public RoleRemoveEntityClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class RoleDeleteClient {

    private static final Function<Long, Record<RoleRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.roleRecords()
                .withIntent(RoleIntent.DELETED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<RoleRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.roleRecords()
                .onlyCommandRejections()
                .withIntent(RoleIntent.DELETE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final RoleRecord roleRecord;
    private Function<Long, Record<RoleRecordValue>> expectation = SUCCESS_SUPPLIER;

    public RoleDeleteClient(final CommandWriter writer, final long key) {
      this.writer = writer;
      roleRecord = new RoleRecord();
      roleRecord.setRoleKey(key);
    }

    public Record<RoleRecordValue> delete() {
      final long position = writer.writeCommand(RoleIntent.DELETE, roleRecord);
      return expectation.apply(position);
    }

    public RoleDeleteClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
