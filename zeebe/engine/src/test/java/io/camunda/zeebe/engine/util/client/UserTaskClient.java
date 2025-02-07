/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class UserTaskClient {
  private static final long DEFAULT_KEY = -1L;
  private static final int DEFAULT_REQUEST_STREAM_ID = 1;
  private static final long DEFAULT_REQUEST_ID = 1L;

  private static final Function<Long, Record<UserTaskRecordValue>> SUCCESS_SUPPLIER =
      (position) ->
          RecordingExporter.userTaskRecords()
              .onlyEvents()
              .withSourceRecordPosition(position)
              .getFirst();

  private static final Function<Long, Record<UserTaskRecordValue>> REJECTION_SUPPLIER =
      (position) ->
          RecordingExporter.userTaskRecords()
              .onlyCommandRejections()
              .withSourceRecordPosition(position)
              .getFirst();

  private final UserTaskRecord userTaskRecord;
  private final CommandWriter writer;
  private long processInstanceKey;
  private long userTaskKey = DEFAULT_KEY;
  private List<String> authorizedTenantIds = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  private Function<Long, Record<UserTaskRecordValue>> expectation = SUCCESS_SUPPLIER;

  public UserTaskClient(final CommandWriter writer) {
    this.writer = writer;
    userTaskRecord = new UserTaskRecord();
  }

  public UserTaskClient ofInstance(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public UserTaskClient withKey(final long userTaskKey) {
    this.userTaskKey = userTaskKey;
    return this;
  }

  public UserTaskClient withAuthorizedTenantIds(final String... tenantIds) {
    authorizedTenantIds = List.of(tenantIds);
    return this;
  }

  public UserTaskClient withAssignee(final String assignee) {
    userTaskRecord.setAssignee(assignee);
    return this;
  }

  public UserTaskClient withoutAssignee() {
    userTaskRecord.setAssignee("");
    return this;
  }

  public UserTaskClient withAction(final String action) {
    userTaskRecord.setAction(action);
    return this;
  }

  public UserTaskClient withVariables(final String variables) {
    userTaskRecord.setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
    return this;
  }

  public UserTaskClient withVariables(final DirectBuffer variables) {
    userTaskRecord.setVariables(variables);
    return this;
  }

  public UserTaskClient withVariable(final String key, final Object value) {
    userTaskRecord.setVariables(MsgPackUtil.asMsgPack(key, value));
    return this;
  }

  public UserTaskClient withVariables(final Map<String, Object> variables) {
    userTaskRecord.setVariables(MsgPackUtil.asMsgPack(variables));
    return this;
  }

  public UserTaskClient expectRejection() {
    expectation = REJECTION_SUPPLIER;
    return this;
  }

  private long findUserTaskKey() {
    if (userTaskKey == DEFAULT_KEY) {
      final Record<UserTaskRecordValue> userTask =
          RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .getFirst();

      return userTask.getKey();
    }

    return userTaskKey;
  }

  public Record<UserTaskRecordValue> unassign() {
    return withoutAssignee().withAction(userTaskRecord.getActionOrDefault("unassign")).assign();
  }

  public Record<UserTaskRecordValue> assign() {
    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            DEFAULT_REQUEST_STREAM_ID,
            DEFAULT_REQUEST_ID,
            UserTaskIntent.ASSIGN,
            userTaskRecord.setUserTaskKey(userTaskKey),
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<UserTaskRecordValue> assign(final String username) {
    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            DEFAULT_REQUEST_STREAM_ID,
            DEFAULT_REQUEST_ID,
            UserTaskIntent.ASSIGN,
            username,
            userTaskRecord.setUserTaskKey(userTaskKey),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    return expectation.apply(position);
  }

  public Record<UserTaskRecordValue> assign(
      final Consumer<UserTaskRecord> userTaskRecordCustomizer) {
    final long userTaskKey = findUserTaskKey();
    final UserTaskRecord taskRecord = userTaskRecord.setUserTaskKey(userTaskKey);
    userTaskRecordCustomizer.accept(taskRecord);
    final long position =
        writer.writeCommand(
            userTaskKey,
            DEFAULT_REQUEST_STREAM_ID,
            DEFAULT_REQUEST_ID,
            UserTaskIntent.ASSIGN,
            taskRecord,
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<UserTaskRecordValue> claim() {
    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            DEFAULT_REQUEST_STREAM_ID,
            DEFAULT_REQUEST_ID,
            UserTaskIntent.CLAIM,
            userTaskRecord.setUserTaskKey(userTaskKey),
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<UserTaskRecordValue> claim(final String username) {
    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            DEFAULT_REQUEST_STREAM_ID,
            DEFAULT_REQUEST_ID,
            UserTaskIntent.CLAIM,
            username,
            userTaskRecord.setUserTaskKey(userTaskKey),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    return expectation.apply(position);
  }

  public Record<UserTaskRecordValue> complete() {
    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            DEFAULT_REQUEST_STREAM_ID,
            DEFAULT_REQUEST_ID,
            UserTaskIntent.COMPLETE,
            userTaskRecord.setUserTaskKey(userTaskKey),
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<UserTaskRecordValue> complete(final String username) {
    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            DEFAULT_REQUEST_STREAM_ID,
            DEFAULT_REQUEST_ID,
            UserTaskIntent.COMPLETE,
            username,
            userTaskRecord.setUserTaskKey(userTaskKey),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    return expectation.apply(position);
  }

  public Record<UserTaskRecordValue> update(final UserTaskRecord changes) {
    if (changes.getChangedAttributesProp().isEmpty()) {
      // assume all attributes have been changed
      changes
          .setCandidateGroupsChanged()
          .setCandidateUsersChanged()
          .setDueDateChanged()
          .setFollowUpDateChanged()
          .setPriorityChanged();
    }
    userTaskRecord.wrapChangedAttributes(changes, true);

    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            DEFAULT_REQUEST_STREAM_ID,
            DEFAULT_REQUEST_ID,
            UserTaskIntent.UPDATE,
            userTaskRecord.setUserTaskKey(userTaskKey),
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<UserTaskRecordValue> update(final UserTaskRecord changes, final String username) {
    if (changes.getChangedAttributesProp().isEmpty()) {
      // assume all attributes have been changed
      changes
          .setCandidateGroupsChanged()
          .setCandidateUsersChanged()
          .setDueDateChanged()
          .setFollowUpDateChanged()
          .setPriorityChanged();
    }
    userTaskRecord.wrapChangedAttributes(changes, true);

    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            DEFAULT_REQUEST_STREAM_ID,
            DEFAULT_REQUEST_ID,
            UserTaskIntent.UPDATE,
            username,
            userTaskRecord.setUserTaskKey(userTaskKey),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    return expectation.apply(position);
  }
}
