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
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.function.Function;

public final class AuthorizationClient {

  private final CommandWriter writer;

  public AuthorizationClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public AuthorizationCreationClient newAuthorization() {
    return new AuthorizationCreationClient(writer);
  }

  public static class AuthorizationCreationClient {

    private static final Function<Long, Record<AuthorizationRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.authorizationRecords()
                .withIntent(AuthorizationIntent.CREATED)
                .withSourceRecordPosition(position)
                .getFirst();
    private static final Function<Long, Record<AuthorizationRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.authorizationRecords()
                .onlyCommandRejections()
                .withIntent(AuthorizationIntent.CREATE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final AuthorizationRecord authorizationCreationRecord;
    private Function<Long, Record<AuthorizationRecordValue>> expectation = SUCCESS_SUPPLIER;

    public AuthorizationCreationClient(final CommandWriter writer) {
      this.writer = writer;
      authorizationCreationRecord = new AuthorizationRecord();
    }

    public AuthorizationCreationClient withOwnerKey(final String ownerKey) {
      authorizationCreationRecord.setOwnerKey(ownerKey);
      return this;
    }

    public AuthorizationCreationClient withOwnerType(final AuthorizationOwnerType ownerType) {
      authorizationCreationRecord.setOwnerType(ownerType);
      return this;
    }

    public AuthorizationCreationClient withResourceKey(final String resourceKey) {
      authorizationCreationRecord.setResourceKey(resourceKey);
      return this;
    }

    public AuthorizationCreationClient withResourceType(final String resourceType) {
      authorizationCreationRecord.setResourceType(resourceType);
      return this;
    }

    public AuthorizationCreationClient withPermissions(final List<String> permissions) {
      authorizationCreationRecord.setPermissions(permissions);
      return this;
    }

    public Record<AuthorizationRecordValue> create() {
      final long position =
          writer.writeCommand(AuthorizationIntent.CREATE, authorizationCreationRecord);
      return expectation.apply(position);
    }

    public AuthorizationCreationClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
