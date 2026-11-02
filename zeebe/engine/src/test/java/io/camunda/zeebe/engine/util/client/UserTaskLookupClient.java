/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskLookupRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UserTaskLookupIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskLookupRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.function.Function;

public final class UserTaskLookupClient {

  private static final Function<Long, Record<UserTaskLookupRecordValue>> SUCCESS_EXPECTATION =
      (position) ->
          RecordingExporter.userTaskLookupRecords()
              .withIntent(UserTaskLookupIntent.FETCHED)
              .withSourceRecordPosition(position)
              .getFirst();
  private static final Function<Long, Record<UserTaskLookupRecordValue>> REJECTION_EXPECTATION =
      (position) ->
          RecordingExporter.userTaskLookupRecords()
              .onlyCommandRejections()
              .withIntent(UserTaskLookupIntent.FETCH)
              .withSourceRecordPosition(position)
              .getFirst();

  private final CommandWriter writer;
  private final UserTaskLookupRecord lookupRecord = new UserTaskLookupRecord();
  private Function<Long, Record<UserTaskLookupRecordValue>> expectation = SUCCESS_EXPECTATION;
  private long userTaskKey = -1L;
  private List<String> authorizedTenantIds = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private int requestStreamId = 1;
  private long requestId = 1L;

  public UserTaskLookupClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public UserTaskLookupClient withKey(final long userTaskKey) {
    this.userTaskKey = userTaskKey;
    return this;
  }

  public UserTaskLookupClient withFetchVariables(final String... variableNames) {
    lookupRecord.setFetchVariables(List.of(variableNames));
    return this;
  }

  /** Sets the response-only fields on the command, as an untrusted client could. */
  public UserTaskLookupClient withUserTask(final UserTaskRecord userTask) {
    lookupRecord.setUserTask(userTask);
    return this;
  }

  public UserTaskLookupClient withAuthorizedTenantIds(final String... tenantIds) {
    authorizedTenantIds = List.of(tenantIds);
    return this;
  }

  public UserTaskLookupClient withRequestStreamId(final int requestStreamId) {
    this.requestStreamId = requestStreamId;
    return this;
  }

  public UserTaskLookupClient withRequestId(final long requestId) {
    this.requestId = requestId;
    return this;
  }

  public UserTaskLookupClient expectRejection() {
    expectation = REJECTION_EXPECTATION;
    return this;
  }

  /**
   * Returns the record the lookup appended to the log: the {@code FETCHED} event, or the rejection.
   * The task itself is only in the response, see {@code UserTaskLookupResponses}.
   */
  public Record<UserTaskLookupRecordValue> fetch() {
    final long position =
        writer.writeCommand(
            userTaskKey,
            requestStreamId,
            requestId,
            UserTaskLookupIntent.FETCH,
            lookupRecord,
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<UserTaskLookupRecordValue> fetch(final String username) {
    final long position =
        writer.writeCommand(
            userTaskKey,
            requestStreamId,
            requestId,
            UserTaskLookupIntent.FETCH,
            username,
            lookupRecord,
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }
}
