/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.function.Function;

public final class UserTaskClient {
  private static final long DEFAULT_KEY = -1L;

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

  public Record<UserTaskRecordValue> complete() {
    final long userTaskKey = findUserTaskKey();
    final long position =
        writer.writeCommand(
            userTaskKey,
            UserTaskIntent.COMPLETE,
            userTaskRecord,
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }
}
