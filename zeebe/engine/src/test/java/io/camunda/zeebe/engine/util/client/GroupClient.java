/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord.GroupAttribute;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class GroupClient {

  private final CommandWriter writer;

  public GroupClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public GroupCreateClient newGroup(final String groupId) {
    return new GroupCreateClient(writer, groupId);
  }

  public GroupUpdateClient updateGroup(final String groupId) {
    return new GroupUpdateClient(writer, groupId);
  }

  public GroupAddEntityClient addEntity(final String groupId) {
    return new GroupAddEntityClient(writer, groupId);
  }

  public GroupSetEntitiesClient setEntities(final String groupId) {
    return new GroupSetEntitiesClient(writer, groupId);
  }

  public GroupRemoveEntityClient removeEntity(final String groupId) {
    return new GroupRemoveEntityClient(writer, groupId);
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

    public GroupCreateClient(final CommandWriter writer, final String groupId) {
      this.writer = writer;
      groupRecord = new GroupRecord();
      groupRecord.setGroupId(groupId);
    }

    public GroupCreateClient withName(final String name) {
      groupRecord.setName(name);
      return this;
    }

    public GroupCreateClient withDescription(final String description) {
      groupRecord.setDescription(description);
      return this;
    }

    public GroupCreateClient withAttributes(final Map<String, Object> attributes) {
      groupRecord.setAttributes(attributes);
      return this;
    }

    public GroupCreateClient withExternalId(final String externalId) {
      groupRecord.setExternalId(externalId);
      return this;
    }

    public Record<GroupRecordValue> create() {
      final long position = writer.writeCommand(GroupIntent.CREATE, groupRecord);
      return expectation.apply(position);
    }

    public Record<GroupRecordValue> create(final String username) {
      final long position = writer.writeCommand(GroupIntent.CREATE, username, groupRecord);
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

    public GroupUpdateClient withDescription(final String description) {
      groupRecord.setDescription(description);
      return this;
    }

    public GroupUpdateClient withAttributes(final Map<String, Object> attributes) {
      groupRecord.setAttributes(attributes);
      return this;
    }

    public GroupUpdateClient withExternalId(final String externalId) {
      groupRecord.setExternalId(externalId);
      return this;
    }

    public GroupUpdateClient withChangedAttributes(final GroupAttribute... changedAttributes) {
      groupRecord.setChangedAttributes(Set.of(changedAttributes));
      return this;
    }

    public Record<GroupRecordValue> update() {
      final long position = writer.writeCommand(GroupIntent.UPDATE, groupRecord);
      return expectation.apply(position);
    }

    public Record<GroupRecordValue> update(final String username) {
      final long position = writer.writeCommand(GroupIntent.UPDATE, username, groupRecord);
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

    public Record<GroupRecordValue> add(final String username) {
      final long position = writer.writeCommand(GroupIntent.ADD_ENTITY, username, groupRecord);
      return expectation.apply(position);
    }

    public GroupAddEntityClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class GroupSetEntitiesClient {

    private static final Function<Long, Record<GroupRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.groupRecords()
                .withIntent(GroupIntent.ENTITIES_SET)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<GroupRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.groupRecords()
                .onlyCommandRejections()
                .withIntent(GroupIntent.SET_ENTITIES)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final GroupRecord groupRecord;

    private Function<Long, Record<GroupRecordValue>> expectation = SUCCESS_SUPPLIER;

    public GroupSetEntitiesClient(final CommandWriter writer, final String groupId) {
      this.writer = writer;
      groupRecord = new GroupRecord();
      groupRecord.setGroupId(groupId);
    }

    public GroupSetEntitiesClient withEntityIds(final Collection<String> entityIds) {
      groupRecord.setEntityIds(List.copyOf(entityIds));
      return this;
    }

    public GroupSetEntitiesClient withEntityType(final EntityType entityType) {
      groupRecord.setEntityType(entityType);
      return this;
    }

    public Record<GroupRecordValue> set() {
      final long position = writer.writeCommand(GroupIntent.SET_ENTITIES, groupRecord);
      return expectation.apply(position);
    }

    public Record<GroupRecordValue> set(final String username) {
      final long position = writer.writeCommand(GroupIntent.SET_ENTITIES, username, groupRecord);
      return expectation.apply(position);
    }

    public GroupSetEntitiesClient expectRejection() {
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

    public GroupRemoveEntityClient(final CommandWriter writer, final String groupId) {
      this.writer = writer;
      groupRecord = new GroupRecord();
      groupRecord.setGroupId(groupId);
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

    public Record<GroupRecordValue> remove(final String username) {
      final long position = writer.writeCommand(GroupIntent.REMOVE_ENTITY, username, groupRecord);
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

    public Record<GroupRecordValue> delete(final String username) {
      final long position = writer.writeCommand(GroupIntent.DELETE, username, groupRecord);
      return expectation.apply(position);
    }

    public GroupDeleteClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
