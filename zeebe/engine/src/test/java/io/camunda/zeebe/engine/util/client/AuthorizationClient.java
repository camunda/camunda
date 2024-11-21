/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.Permission;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
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

  public AuthorizationPermissionClient permission() {
    return new AuthorizationPermissionClient(writer);
  }

  public static class AuthorizationPermissionClient {

    private static final Function<Long, Record<AuthorizationRecordValue>> ADD_SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.authorizationRecords()
                .withIntent(AuthorizationIntent.PERMISSION_ADDED)
                .withSourceRecordPosition(position)
                .getFirst();
    private static final Function<Long, Record<AuthorizationRecordValue>> ADD_REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.authorizationRecords()
                .onlyCommandRejections()
                .withIntent(AuthorizationIntent.ADD_PERMISSION)
                .withSourceRecordPosition(position)
                .getFirst();
    private static final Function<Long, Record<AuthorizationRecordValue>> REMOVE_SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.authorizationRecords()
                .withIntent(AuthorizationIntent.PERMISSION_REMOVED)
                .withSourceRecordPosition(position)
                .getFirst();
    private static final Function<Long, Record<AuthorizationRecordValue>>
        REMOVE_REJECTION_SUPPLIER =
            (position) ->
                RecordingExporter.authorizationRecords()
                    .onlyCommandRejections()
                    .withIntent(AuthorizationIntent.REMOVE_PERMISSION)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private Function<Long, Record<AuthorizationRecordValue>> expectation = ADD_SUCCESS_SUPPLIER;
    private final CommandWriter writer;
    private final AuthorizationRecord authorizationCreationRecord;
    private boolean expectRejection = false;

    public AuthorizationPermissionClient(final CommandWriter writer) {
      this.writer = writer;
      authorizationCreationRecord = new AuthorizationRecord();
    }

    public AuthorizationPermissionClient withOwnerKey(final Long ownerKey) {
      authorizationCreationRecord.setOwnerKey(ownerKey);
      return this;
    }

    public AuthorizationPermissionClient withOwnerType(final AuthorizationOwnerType ownerType) {
      authorizationCreationRecord.setOwnerType(ownerType);
      return this;
    }

    public AuthorizationPermissionClient withResourceType(
        final AuthorizationResourceType resourceType) {
      authorizationCreationRecord.setResourceType(resourceType);
      return this;
    }

    public AuthorizationPermissionClient withPermission(
        final PermissionType permissionType, final String... resourceIds) {
      authorizationCreationRecord.addPermission(
          new Permission().setPermissionType(permissionType).addResourceIds(Set.of(resourceIds)));
      return this;
    }

    public AuthorizationPermissionClient withPermission(final Permission permission) {
      authorizationCreationRecord.addPermission(permission);
      return this;
    }

    public Record<AuthorizationRecordValue> add() {
      expectation = expectRejection ? ADD_REJECTION_SUPPLIER : ADD_SUCCESS_SUPPLIER;
      final long position =
          writer.writeCommand(AuthorizationIntent.ADD_PERMISSION, authorizationCreationRecord);
      return expectation.apply(position);
    }

    public AuthorizationPermissionClient expectRejection() {
      expectRejection = true;
      return this;
    }

    public Record<AuthorizationRecordValue> remove() {
      expectation = expectRejection ? REMOVE_REJECTION_SUPPLIER : REMOVE_SUCCESS_SUPPLIER;
      final long position =
          writer.writeCommand(AuthorizationIntent.REMOVE_PERMISSION, authorizationCreationRecord);
      return expectation.apply(position);
    }
  }
}
