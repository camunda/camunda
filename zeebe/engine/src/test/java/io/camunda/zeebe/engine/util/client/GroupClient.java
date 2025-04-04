/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public class GroupClient {

  private final CommandWriter writer;

  public GroupClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public GroupCreateClient newGroup(final String name) {
    return new GroupCreateClient(writer, name);
  }

  public GroupUpdateClient updateGroup(final String groupId) {
    return new GroupUpdateClient(writer, groupId);
  }

  public GroupAddEntityClient addEntity(final String groupId) {
    return new GroupAddEntityClient(writer, groupId);
  }

  public GroupRemoveEntityClient removeEntity(final long groupKey) {
    return new GroupRemoveEntityClient(writer, groupKey);
  }

  public GroupDeleteClient deleteGroup(final String groupId) {
    return new GroupDeleteClient(writer, groupId);
  }

  public static class GroupCreateClient {

    private static final Function<Long, Record<GroupRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.groupRecords()
                .withIntent(GroupIntent.CREATED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<GroupRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.groupRecords()
                .onlyCommandRejections()
                .withIntent(GroupIntent.CREATE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final GroupRecord groupRecord;
    private Function<Long, Record<GroupRecordValue>> expectation = SUCCESS_SUPPLIER;

    public GroupCreateClient(final CommandWriter writer, final String name) {
      this.writer = writer;
      groupRecord = new GroupRecord();
      groupRecord.setName(name);
    }

    public GroupCreateClient withGroupId(final String groupId) {
      groupRecord.setGroupId(groupId);
      return this;
    }

    public Record<GroupRecordValue> create() {
      final long position = writer.writeCommand(GroupIntent.CREATE, groupRecord);
      return expectation.apply(position);
    }

    public GroupCreateClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class GroupUpdateClient {

    private static final Function<Long, Record<GroupRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.groupRecords()
                .withIntent(GroupIntent.UPDATED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<GroupRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.groupRecords()
                .onlyCommandRejections()
                .withIntent(GroupIntent.UPDATE)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final GroupRecord groupRecord;
    private Function<Long, Record<GroupRecordValue>> expectation = SUCCESS_SUPPLIER;

    public GroupUpdateClient(final CommandWriter writer, final String groupId) {
      this.writer = writer;
      groupRecord = new GroupRecord();
      groupRecord.setGroupId(groupId);
    }

    public GroupUpdateClient withName(final String name) {
      groupRecord.setName(name);
      return this;
    }

    public Record<GroupRecordValue> update() {
      final long position = writer.writeCommand(GroupIntent.UPDATE, groupRecord);
      return expectation.apply(position);
    }

    public GroupUpdateClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class GroupAddEntityClient {

    private static final Function<Long, Record<GroupRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.groupRecords()
                .withIntent(GroupIntent.ENTITY_ADDED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<GroupRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.groupRecords()
                .onlyCommandRejections()
                .withIntent(GroupIntent.ADD_ENTITY)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final GroupRecord groupRecord;

    private Function<Long, Record<GroupRecordValue>> expectation = SUCCESS_SUPPLIER;

    public GroupAddEntityClient(final CommandWriter writer, final String groupId) {
      this.writer = writer;
      groupRecord = new GroupRecord();
      groupRecord.setGroupId(groupId);
    }

    public GroupAddEntityClient withEntityId(final String entityId) {
      groupRecord.setEntityId(entityId);
      return this;
    }

    public GroupAddEntityClient withEntityType(final EntityType entityType) {
      groupRecord.setEntityType(entityType);
      return this;
    }

    public Record<GroupRecordValue> add() {
      final long position = writer.writeCommand(GroupIntent.ADD_ENTITY, groupRecord);
      return expectation.apply(position);
    }

    public GroupAddEntityClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class GroupRemoveEntityClient {

    private static final Function<Long, Record<GroupRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.groupRecords()
                .withIntent(GroupIntent.ENTITY_REMOVED)
                .withSourceRecordPosition(position)
                .getFirst();
    private static final Function<Long, Record<GroupRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.groupRecords()
                .onlyCommandRejections()
                .withIntent(GroupIntent.REMOVE_ENTITY)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final GroupRecord groupRecord;

    private Function<Long, Record<GroupRecordValue>> expectation = SUCCESS_SUPPLIER;

    public GroupRemoveEntityClient(final CommandWriter writer, final long key) {
      this.writer = writer;
      groupRecord = new GroupRecord();
      groupRecord.setGroupKey(key);
    }

    // TODO: refactor this with https://github.com/camunda/camunda/issues/30029
    public GroupRemoveEntityClient withGroupId(final String groupId) {
      groupRecord.setGroupId(groupId);
      return this;
    }

    public GroupRemoveEntityClient withEntityId(final String entityId) {
      groupRecord.setEntityId(entityId);
      return this;
    }

    public GroupRemoveEntityClient withEntityType(final EntityType entityType) {
      groupRecord.setEntityType(entityType);
      return this;
    }

    public Record<GroupRecordValue> remove() {
      final long position = writer.writeCommand(GroupIntent.REMOVE_ENTITY, groupRecord);
      return expectation.apply(position);
    }

    public GroupRemoveEntityClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class GroupDeleteClient {

    private static final Function<Long, Record<GroupRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.groupRecords()
                .withIntent(GroupIntent.DELETED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<GroupRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.groupRecords()
                .onlyCommandRejections()
                .withIntent(GroupIntent.DELETE)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final GroupRecord groupRecord;

    private Function<Long, Record<GroupRecordValue>> expectation = SUCCESS_SUPPLIER;

    public GroupDeleteClient(final CommandWriter writer, final String groupId) {
      this.writer = writer;
      groupRecord = new GroupRecord();
      groupRecord.setGroupId(groupId);
    }

    public GroupDeleteClient withGroupKey(final long groupKey) {
      groupRecord.setGroupKey(groupKey);
      return this;
    }

    public Record<GroupRecordValue> delete() {
      final long position = writer.writeCommand(GroupIntent.DELETE, groupRecord);
      return expectation.apply(position);
    }

    public GroupDeleteClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
