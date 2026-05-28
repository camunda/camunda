/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.Protocol;
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
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class UserTaskClient {
  private static final long DEFAULT_KEY = -1L;
  private static final int DEFAULT_REQUEST_STREAM_ID = 1;
  private static final long DEFAULT_REQUEST_ID = 1L;
  private static final int DEFAULT_PARTITION_ID = 1;

  private static final Function<Long, Record<UserTaskRecordValue>> SUCCESS_SUPPLIER =
      (position) ->
          RecordingExporter.userTaskRecords().withSourceRecordPosition(position).getFirst();

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

  public UserTaskClient withCandidateUsers(final String... candidateUsers) {
    return withCandidateUsersList(List.of(candidateUsers));
  }

  public UserTaskClient withCandidateUsersList(final List<String> candidateUsers) {
    userTaskRecord.setCandidateUsersList(candidateUsers).setCandidateUsersChanged();
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

  public Record<UserTaskRecordValue> assign() {
    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            UserTaskIntent.ASSIGN,
            userTaskRecord.setUserTaskKey(userTaskKey),
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<UserTaskRecordValue> claim() {
    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            UserTaskIntent.CLAIM,
            userTaskRecord.setUserTaskKey(userTaskKey),
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<UserTaskRecordValue> complete() {
    final long userTaskKey = findUserTaskKey();
    final int partitionId = decodePartitionId(userTaskKey);
    final long position =
        writer.writeCommandOnPartition(
            partitionId,
            r ->
                r.key(userTaskKey)
                    .requestStreamId(DEFAULT_REQUEST_STREAM_ID)
                    .requestId(DEFAULT_REQUEST_ID)
                    .intent(UserTaskIntent.COMPLETE)
                    .event(userTaskRecord.setUserTaskKey(userTaskKey))
                    .authorizations(authorizedTenantIds.toArray(new String[0])));
    return expectation.apply(position);
  }

  private static int decodePartitionId(final long userTaskKey) {
    final var partitionId = Protocol.decodePartitionId(userTaskKey);
    if (partitionId <= 0) {
      // the userTaskKey does not encode a partition id
      return DEFAULT_PARTITION_ID;
    }
    return partitionId;
  }

  /**
   * Sends an {@link UserTaskIntent#UPDATE} command for the user task with the specified attribute
   * values.
   *
   * <p>This method uses the attributes set through the {@code with<AttributeName>()} methods to
   * construct the {@link UserTaskRecord} with the appropriate changed attributes. Only explicitly
   * set attributes will be included in the {@code changedAttributes} list, ensuring precise control
   * over which properties are updated.
   *
   * <p>Example 1: Update specific attributes
   *
   * <pre>{@code
   * final var candidateGroups = List.of("elves", "dwarves");
   * userTaskClient
   *     .ofInstance(processInstanceKey)
   *     .withCandidateGroupsList(candidateGroups)
   *     .withCandidateUsers("legolas", "thorin")
   *     .withDueDate("2023-03-02T15:35+02:00")
   *     .withPriority(99)
   *     .update();
   * }</pre>
   *
   * This example constructs and sends an {@link UserTaskIntent#UPDATE} command that updates the
   * following attributes: {@code candidateGroupsList}, {@code candidateUsersList}, {@code dueDate},
   * and {@code priority}. These attribute names will be included in the {@code changedAttributes}
   * list.
   *
   * <p>Example 2: Reset all updatable attributes to their default values
   *
   * <pre>{@code
   * userTaskClient
   *     .ofInstance(processInstanceKey)
   *     .withAllAttributesChanged()
   *     .update();
   * }</pre>
   *
   * This example sends an {@link UserTaskIntent#UPDATE} command with all updatable attributes
   * marked as changed, resulting in all such attributes being reset to their default values.
   *
   * <p>Example 3: Trigger update transition without updating any attributes
   *
   * <pre>{@code
   * userTaskClient
   *     .ofInstance(processInstanceKey)
   *     .update();
   * }</pre>
   *
   * In this example, the {@link UserTaskIntent#UPDATE} command is sent with an empty {@code
   * changedAttributes} list. This triggers the update transition, including the execution of
   * configured {@code updating} listeners, by without updating any user task properties.
   *
   * @return the {@link UserTaskIntent#UPDATING} record.
   */
  public Record<UserTaskRecordValue> update() {
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

  public Record<UserTaskRecordValue> update(
      final List<String> candidateGroups,
      final List<String> candidateUsers,
      final String dueDate,
      final String followUpDate) {
    if (candidateGroups != null) {
      userTaskRecord.setCandidateGroupsList(candidateGroups).setCandidateGroupsChanged();
    }
    if (candidateUsers != null) {
      userTaskRecord.setCandidateUsersList(candidateUsers).setCandidateUsersChanged();
    }
    if (dueDate != null) {
      userTaskRecord.setDueDate(dueDate).setDueDateChanged();
    }
    if (followUpDate != null) {
      userTaskRecord.setFollowUpDate(followUpDate).setFollowUpDateChanged();
    }
    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            UserTaskIntent.UPDATE,
            userTaskRecord.setUserTaskKey(userTaskKey),
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<UserTaskRecordValue> update(final UserTaskRecord changes) {
    changes
        .setCandidateGroupsChanged()
        .setCandidateUsersChanged()
        .setDueDateChanged()
        .setFollowUpDateChanged();
    userTaskRecord.wrapChangedAttributes(changes, true);

    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            UserTaskIntent.UPDATE,
            userTaskRecord.setUserTaskKey(userTaskKey),
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }
}
