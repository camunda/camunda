/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public class MappingClient {

  private final CommandWriter writer;

  public MappingClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public MappingCreateClient newMapping(final String mappingId) {
    return new MappingCreateClient(writer, mappingId);
  }

  public MappingDeleteClient deleteMapping(final String mappingId) {
    return new MappingDeleteClient(writer, mappingId);
  }

  public MappingUpdateClient updateMapping(final String mappingId) {
    return new MappingUpdateClient(writer, mappingId);
  }

  public static class MappingCreateClient {

    private static final Function<Long, Record<MappingRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRuleRecords()
                .withIntent(MappingRuleIntent.CREATED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<MappingRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRuleRecords()
                .onlyCommandRejections()
                .withIntent(MappingRuleIntent.CREATE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final MappingRuleRecord mappingRecord;
    private Function<Long, Record<MappingRecordValue>> expectation = SUCCESS_SUPPLIER;

    public MappingCreateClient(final CommandWriter writer, final String mappingId) {
      this.writer = writer;
      mappingRecord = new MappingRuleRecord();
      mappingRecord.setMappingRuleId(mappingId);
    }

    public MappingCreateClient withClaimName(final String claimName) {
      mappingRecord.setClaimName(claimName);
      return this;
    }

    public MappingCreateClient withClaimValue(final String claimValue) {
      mappingRecord.setClaimValue(claimValue);
      return this;
    }

    public MappingCreateClient withName(final String name) {
      mappingRecord.setName(name);
      return this;
    }

    public Record<MappingRecordValue> create() {
      final long position = writer.writeCommand(MappingRuleIntent.CREATE, mappingRecord);
      return expectation.apply(position);
    }

    public Record<MappingRecordValue> create(final String username) {
      final long position = writer.writeCommand(MappingRuleIntent.CREATE, username, mappingRecord);
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
            RecordingExporter.mappingRuleRecords()
                .withIntent(MappingRuleIntent.DELETED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<MappingRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRuleRecords()
                .onlyCommandRejections()
                .withIntent(MappingRuleIntent.DELETE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final MappingRuleRecord mappingRecord;
    private Function<Long, Record<MappingRecordValue>> expectation = SUCCESS_SUPPLIER;

    public MappingDeleteClient(final CommandWriter writer, final String mappingId) {
      this.writer = writer;
      mappingRecord = new MappingRuleRecord();
      mappingRecord.setMappingRuleId(mappingId);
    }

    public Record<MappingRecordValue> delete() {
      final long position = writer.writeCommand(MappingRuleIntent.DELETE, mappingRecord);
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
            RecordingExporter.mappingRuleRecords()
                .withIntent(MappingRuleIntent.UPDATED)
                .filter(
                    mappingRecordValueRecord ->
                        mappingRecordValueRecord.getValue().getMappingRuleId().equals(position))
                .getFirst();

    private static final Function<String, Record<MappingRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRuleRecords()
                .onlyCommandRejections()
                .withIntent(MappingRuleIntent.UPDATE)
                .filter(
                    mappingRecordValueRecord ->
                        mappingRecordValueRecord.getValue().getMappingRuleId().equals(position))
                .getFirst();
    private final CommandWriter writer;
    private final MappingRuleRecord mappingRecord;
    private Function<String, Record<MappingRecordValue>> expectation = SUCCESS_SUPPLIER;

    public MappingUpdateClient(final CommandWriter writer, final String mappingId) {
      this.writer = writer;
      mappingRecord = new MappingRuleRecord();
      mappingRecord.setMappingRuleId(mappingId);
    }

    public Record<MappingRecordValue> update() {
      writer.writeCommand(MappingRuleIntent.UPDATE, mappingRecord);
      return expectation.apply(mappingRecord.getMappingRuleId());
    }

    public Record<MappingRecordValue> update(final String username) {
      writer.writeCommand(MappingRuleIntent.UPDATE, username, mappingRecord);
      return expectation.apply(mappingRecord.getMappingRuleId());
    }

    public MappingUpdateClient withClaimName(final String claimName) {
      mappingRecord.setClaimName(claimName);
      return this;
    }

    public MappingUpdateClient withClaimValue(final String claimValue) {
      mappingRecord.setClaimValue(claimValue);
      return this;
    }

    public MappingUpdateClient withName(final String name) {
      mappingRecord.setName(name);
      return this;
    }

    public MappingUpdateClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
