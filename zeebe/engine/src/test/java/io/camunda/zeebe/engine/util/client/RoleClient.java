/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public class RoleClient {

  private final CommandWriter writer;

  public RoleClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public RoleCreationClient newRole(final String name) {
    return new RoleCreationClient(writer, name);
  }

  public static class RoleCreationClient {

    private static final Function<Long, io.camunda.zeebe.protocol.record.Record<RoleRecordValue>>
        SUCCESS_SUPPLIER =
            (position) ->
                RecordingExporter.roleRecords()
                    .withIntent(RoleIntent.CREATED)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private static final Function<Long, io.camunda.zeebe.protocol.record.Record<RoleRecordValue>>
        REJECTION_SUPPLIER =
            (position) ->
                RecordingExporter.roleRecords()
                    .onlyCommandRejections()
                    .withIntent(RoleIntent.CREATE)
                    .withSourceRecordPosition(position)
                    .getFirst();
    private final CommandWriter writer;
    private final RoleRecord roleRecord;
    private Function<Long, io.camunda.zeebe.protocol.record.Record<RoleRecordValue>> expectation =
        SUCCESS_SUPPLIER;

    public RoleCreationClient(final CommandWriter writer, final String name) {
      this.writer = writer;
      roleRecord = new RoleRecord();
      roleRecord.setName(name);
    }

    public RoleCreationClient withEntityKey(final long entityKey) {
      roleRecord.setEntityKey(entityKey);
      return this;
    }

    public Record<RoleRecordValue> create() {
      final long position = writer.writeCommand(RoleIntent.CREATE, roleRecord);
      return expectation.apply(position);
    }

    public RoleCreationClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
