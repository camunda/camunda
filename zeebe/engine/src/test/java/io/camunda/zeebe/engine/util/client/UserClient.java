/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public final class UserClient {

  private final CommandWriter writer;

  public UserClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public UserCreationClient newUser(final String username) {
    return new UserCreationClient(writer, username);
  }

  public UpdateUserClient updateUser() {
    return new UpdateUserClient(writer);
  }

  public UpdateUserClient updateUser(final long userKey, final UserRecord userRecord) {
    return new UpdateUserClient(writer, userKey, userRecord);
  }

  public DeleteUserClient deleteUser(final String username) {
    return new DeleteUserClient(writer, username);
  }

  public static class UserCreationClient {

    private static final Function<Long, Record<UserRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.userRecords()
                .withIntent(UserIntent.CREATED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<UserRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.userRecords()
                .onlyCommandRejections()
                .withIntent(UserIntent.CREATE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final UserRecord userCreationRecord;
    private Function<Long, Record<UserRecordValue>> expectation = SUCCESS_SUPPLIER;

    public UserCreationClient(final CommandWriter writer, final String username) {
      this.writer = writer;
      userCreationRecord = new UserRecord();
      userCreationRecord.setUsername(username);
    }

    public UserCreationClient withUsername(final String username) {
      userCreationRecord.setUsername(username);
      return this;
    }

    public UserCreationClient withName(final String name) {
      userCreationRecord.setName(name);
      return this;
    }

    public UserCreationClient withEmail(final String email) {
      userCreationRecord.setEmail(email);
      return this;
    }

    public UserCreationClient withPassword(final String password) {
      userCreationRecord.setPassword(password);
      return this;
    }

    public Record<UserRecordValue> create() {
      final long position = writer.writeCommand(UserIntent.CREATE, userCreationRecord);
      return expectation.apply(position);
    }

    public Record<UserRecordValue> create(final String username) {
      final long position = writer.writeCommand(UserIntent.CREATE, username, userCreationRecord);
      return expectation.apply(position);
    }

    public UserCreationClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class UpdateUserClient {
    private static final Function<Long, Record<UserRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.userRecords()
                .withIntent(UserIntent.UPDATED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<UserRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.userRecords()
                .onlyCommandRejections()
                .withIntent(UserIntent.UPDATE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final UserRecord userRecord;
    private Function<Long, Record<UserRecordValue>> expectation = SUCCESS_SUPPLIER;

    public UpdateUserClient(final CommandWriter writer) {
      this.writer = writer;
      userRecord = new UserRecord();
    }

    public UpdateUserClient(
        final CommandWriter writer, final long userKey, final UserRecord userRecord) {
      this.writer = writer;
      this.userRecord = userRecord;
      this.userRecord.setUserKey(userKey);
    }

    public UpdateUserClient withUsername(final String username) {
      userRecord.setUsername(username);
      return this;
    }

    public UpdateUserClient withName(final String name) {
      userRecord.setName(name);
      return this;
    }

    public UpdateUserClient withEmail(final String email) {
      userRecord.setEmail(email);
      return this;
    }

    public UpdateUserClient withPassword(final String password) {
      userRecord.setPassword(password);
      return this;
    }

    public Record<UserRecordValue> update() {
      final long position = writer.writeCommand(UserIntent.UPDATE, userRecord);
      return expectation.apply(position);
    }

    public UpdateUserClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class DeleteUserClient {
    private static final Function<Long, Record<UserRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.userRecords()
                .withIntent(UserIntent.DELETED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<UserRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.userRecords()
                .onlyCommandRejections()
                .withIntent(UserIntent.DELETE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final UserRecord userRecord;
    private Function<Long, Record<UserRecordValue>> expectation = SUCCESS_SUPPLIER;

    public DeleteUserClient(final CommandWriter writer, final String username) {
      this.writer = writer;
      userRecord = new UserRecord();
      userRecord.setUsername(username);
    }

    public DeleteUserClient withUserKey(final long userKey) {
      userRecord.setUserKey(userKey);
      return this;
    }

    public DeleteUserClient withName(final String name) {
      userRecord.setName(name);
      return this;
    }

    public DeleteUserClient withEmail(final String email) {
      userRecord.setEmail(email);
      return this;
    }

    public DeleteUserClient withPassword(final String password) {
      userRecord.setPassword(password);
      return this;
    }

    public Record<UserRecordValue> delete() {
      final long position = writer.writeCommand(UserIntent.DELETE, userRecord);
      return expectation.apply(position);
    }

    public DeleteUserClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
