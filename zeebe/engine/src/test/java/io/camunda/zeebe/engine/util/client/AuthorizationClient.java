/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Set;
import java.util.function.Function;

public final class AuthorizationClient {

  private final CommandWriter writer;

  public AuthorizationClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public AuthorizationCreateClient newAuthorization() {
    return new AuthorizationCreateClient(writer);
  }

  public AuthorizationDeleteClient deleteAuthorization(final long authorizationKey) {
    return new AuthorizationDeleteClient(writer, authorizationKey);
  }

  public AuthorizationUpdateClient updateAuthorization(final long authorizationKey) {
    return new AuthorizationUpdateClient(writer, authorizationKey);
  }

  public static class AuthorizationCreateClient {
    private static final Function<Long, Record<AuthorizationRecordValue>> CREATE_SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.authorizationRecords()
                .withIntent(AuthorizationIntent.CREATED)
                .withSourceRecordPosition(position)
                .getFirst();
    private static final Function<Long, Record<AuthorizationRecordValue>>
        CREATE_REJECTION_SUPPLIER =
            (position) ->
                RecordingExporter.authorizationRecords()
                    .onlyCommandRejections()
                    .withIntent(AuthorizationIntent.CREATE)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private Function<Long, Record<AuthorizationRecordValue>> expectation = CREATE_SUCCESS_SUPPLIER;
    private final CommandWriter writer;
    private final AuthorizationRecord authorizationCreationRecord;
    private boolean expectRejection = false;

    public AuthorizationCreateClient(final CommandWriter writer) {
      this.writer = writer;
      authorizationCreationRecord = new AuthorizationRecord();
    }

    public AuthorizationCreateClient withOwnerId(final String ownerId) {
      authorizationCreationRecord.setOwnerId(ownerId);
      return this;
    }

    public AuthorizationCreateClient withOwnerType(final AuthorizationOwnerType ownerType) {
      authorizationCreationRecord.setOwnerType(ownerType);
      return this;
    }

    public AuthorizationCreateClient withResourceMatcher(
        final AuthorizationResourceMatcher resourceMatcher) {
      authorizationCreationRecord.setResourceMatcher(resourceMatcher);
      return this;
    }

    public AuthorizationCreateClient withResourceId(final String resourceId) {
      authorizationCreationRecord.setResourceId(resourceId);
      return this;
    }

    public AuthorizationCreateClient withResourceType(
        final AuthorizationResourceType resourceType) {
      authorizationCreationRecord.setResourceType(resourceType);
      return this;
    }

    public AuthorizationCreateClient withPermissions(final PermissionType... permissions) {
      authorizationCreationRecord.setPermissionTypes(Set.of(permissions));
      return this;
    }

    public AuthorizationCreateClient expectRejection() {
      expectRejection = true;
      return this;
    }

    public Record<AuthorizationRecordValue> create() {
      expectation = expectRejection ? CREATE_REJECTION_SUPPLIER : CREATE_SUCCESS_SUPPLIER;
      final long position =
          writer.writeCommand(AuthorizationIntent.CREATE, authorizationCreationRecord);
      return expectation.apply(position);
    }

    public Record<AuthorizationRecordValue> create(final String username) {
      expectation = expectRejection ? CREATE_REJECTION_SUPPLIER : CREATE_SUCCESS_SUPPLIER;
      final long position =
          writer.writeCommand(AuthorizationIntent.CREATE, username, authorizationCreationRecord);
      return expectation.apply(position);
    }
  }

  public static class AuthorizationDeleteClient {
    private static final Function<Long, Record<AuthorizationRecordValue>> DELETE_SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.authorizationRecords()
                .withIntent(AuthorizationIntent.DELETED)
                .withSourceRecordPosition(position)
                .getFirst();
    private static final Function<Long, Record<AuthorizationRecordValue>>
        DELETE_REJECTION_SUPPLIER =
            (position) ->
                RecordingExporter.authorizationRecords()
                    .onlyCommandRejections()
                    .withIntent(AuthorizationIntent.DELETE)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private Function<Long, Record<AuthorizationRecordValue>> expectation = DELETE_SUCCESS_SUPPLIER;
    private final CommandWriter writer;
    private final AuthorizationRecord authorizationDeletionRecord;
    private boolean expectRejection = false;

    public AuthorizationDeleteClient(final CommandWriter writer, final long authorizationKey) {
      this.writer = writer;
      authorizationDeletionRecord = new AuthorizationRecord().setAuthorizationKey(authorizationKey);
    }

    public AuthorizationDeleteClient expectRejection() {
      expectRejection = true;
      return this;
    }

    public Record<AuthorizationRecordValue> delete() {
      expectation = expectRejection ? DELETE_REJECTION_SUPPLIER : DELETE_SUCCESS_SUPPLIER;
      final long position =
          writer.writeCommand(AuthorizationIntent.DELETE, authorizationDeletionRecord);
      return expectation.apply(position);
    }

    public Record<AuthorizationRecordValue> delete(final String username) {
      expectation = expectRejection ? DELETE_REJECTION_SUPPLIER : DELETE_SUCCESS_SUPPLIER;
      final long position =
          writer.writeCommand(AuthorizationIntent.DELETE, username, authorizationDeletionRecord);
      return expectation.apply(position);
    }
  }

  public static class AuthorizationUpdateClient {
    private static final Function<Long, Record<AuthorizationRecordValue>> UPDATE_SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.authorizationRecords()
                .withIntent(AuthorizationIntent.UPDATED)
                .withSourceRecordPosition(position)
                .getFirst();
    private static final Function<Long, Record<AuthorizationRecordValue>>
        UPDATE_REJECTION_SUPPLIER =
            (position) ->
                RecordingExporter.authorizationRecords()
                    .onlyCommandRejections()
                    .withIntent(AuthorizationIntent.UPDATE)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private Function<Long, Record<AuthorizationRecordValue>> expectation = UPDATE_SUCCESS_SUPPLIER;
    private final CommandWriter writer;
    private final AuthorizationRecord authorizationUpdateRecord;
    private boolean expectRejection = false;

    public AuthorizationUpdateClient(final CommandWriter writer, final long authorizationKey) {
      this.writer = writer;
      authorizationUpdateRecord = new AuthorizationRecord().setAuthorizationKey(authorizationKey);
    }

    public AuthorizationUpdateClient withOwnerId(final String ownerId) {
      authorizationUpdateRecord.setOwnerId(ownerId);
      return this;
    }

    public AuthorizationUpdateClient withOwnerType(final AuthorizationOwnerType ownerType) {
      authorizationUpdateRecord.setOwnerType(ownerType);
      return this;
    }

    public AuthorizationUpdateClient withResourceMatcher(
        final AuthorizationResourceMatcher resourceMatcher) {
      authorizationUpdateRecord.setResourceMatcher(resourceMatcher);
      return this;
    }

    public AuthorizationUpdateClient withResourceId(final String resourceId) {
      authorizationUpdateRecord.setResourceId(resourceId);
      return this;
    }

    public AuthorizationUpdateClient withResourceType(
        final AuthorizationResourceType resourceType) {
      authorizationUpdateRecord.setResourceType(resourceType);
      return this;
    }

    public AuthorizationUpdateClient withPermissions(final PermissionType... permissions) {
      authorizationUpdateRecord.setPermissionTypes(Set.of(permissions));
      return this;
    }

    public AuthorizationUpdateClient expectRejection() {
      expectRejection = true;
      return this;
    }

    public Record<AuthorizationRecordValue> update() {
      expectation = expectRejection ? UPDATE_REJECTION_SUPPLIER : UPDATE_SUCCESS_SUPPLIER;
      final long position =
          writer.writeCommand(AuthorizationIntent.UPDATE, authorizationUpdateRecord);
      return expectation.apply(position);
    }

    public Record<AuthorizationRecordValue> update(final String username) {
      expectation = expectRejection ? UPDATE_REJECTION_SUPPLIER : UPDATE_SUCCESS_SUPPLIER;
      final long position =
          writer.writeCommand(AuthorizationIntent.UPDATE, username, authorizationUpdateRecord);
      return expectation.apply(position);
    }
  }
}
