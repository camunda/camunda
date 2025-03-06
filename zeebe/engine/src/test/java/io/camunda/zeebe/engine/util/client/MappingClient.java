/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public class MappingClient {

  private final CommandWriter writer;

  public MappingClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public MappingCreateClient newMapping(final String name) {
    return new MappingCreateClient(writer, name);
  }

  public MappingDeleteClient deleteMapping(final long key) {
    return new MappingDeleteClient(writer, key);
  }

  public MappingUpdateClient updateMapping(final String id) {
    return new MappingUpdateClient(writer, id);
  }

  public static class MappingCreateClient {

    private static final Function<Long, Record<MappingRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRecords()
                .withIntent(MappingIntent.CREATED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<MappingRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRecords()
                .onlyCommandRejections()
                .withIntent(MappingIntent.CREATE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final MappingRecord mappingRecord;
    private Function<Long, Record<MappingRecordValue>> expectation = SUCCESS_SUPPLIER;

    public MappingCreateClient(final CommandWriter writer, final String claimName) {
      this.writer = writer;
      mappingRecord = new MappingRecord();
      mappingRecord.setClaimName(claimName);
    }

    public MappingCreateClient withClaimValue(final String claimValue) {
      mappingRecord.setClaimValue(claimValue);
      return this;
    }

    public MappingCreateClient withId(final String id) {
      mappingRecord.setId(id);
      return this;
    }

    public MappingCreateClient withName(final String name) {
      mappingRecord.setName(name);
      return this;
    }

    public Record<MappingRecordValue> create() {
      final long position = writer.writeCommand(MappingIntent.CREATE, mappingRecord);
      return expectation.apply(position);
    }

    public Record<MappingRecordValue> create(final String username) {
      final long position = writer.writeCommand(MappingIntent.CREATE, username, mappingRecord);
      return expectation.apply(position);
    }

    public MappingCreateClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class MappingDeleteClient {

    private static final Function<Long, Record<MappingRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRecords()
                .withIntent(MappingIntent.DELETED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<MappingRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRecords()
                .onlyCommandRejections()
                .withIntent(MappingIntent.DELETE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final MappingRecord mappingRecord;
    private Function<Long, Record<MappingRecordValue>> expectation = SUCCESS_SUPPLIER;

    public MappingDeleteClient(final CommandWriter writer, final long key) {
      this.writer = writer;
      mappingRecord = new MappingRecord();
      mappingRecord.setMappingKey(key);
    }

    public Record<MappingRecordValue> delete() {
      final long position = writer.writeCommand(MappingIntent.DELETE, mappingRecord);
      return expectation.apply(position);
    }

    public MappingDeleteClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class MappingUpdateClient {

    private static final Function<String, Record<MappingRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRecords()
                .withIntent(MappingIntent.UPDATED)
                .filter(
                    mappingRecordValueRecord ->
                        mappingRecordValueRecord.getValue().getId().equals(position))
                .getFirst();

    private static final Function<String, Record<MappingRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRecords()
                .onlyCommandRejections()
                .withIntent(MappingIntent.UPDATE)
                .filter(
                    mappingRecordValueRecord ->
                        mappingRecordValueRecord.getValue().getId().equals(position))
                .getFirst();
    private final CommandWriter writer;
    private final MappingRecord mappingRecord;
    private Function<String, Record<MappingRecordValue>> expectation = SUCCESS_SUPPLIER;

    public MappingUpdateClient(final CommandWriter writer, final String id) {
      this.writer = writer;
      mappingRecord = new MappingRecord();
      mappingRecord.setId(id);
    }

    public Record<MappingRecordValue> update() {
      writer.writeCommand(MappingIntent.UPDATE, mappingRecord);
      return expectation.apply(mappingRecord.getId());
    }

    public MappingUpdateClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
