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
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public class MappingRuleClient {

  private final CommandWriter writer;

  public MappingRuleClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public MappingRuleCreateClient newMappingRule(final String mappingRuleId) {
    return new MappingRuleCreateClient(writer, mappingRuleId);
  }

  public MappingRuleDeleteClient deleteMappingRule(final String mappingRuleId) {
    return new MappingRuleDeleteClient(writer, mappingRuleId);
  }

  public MappingRuleUpdateClient updateMappingRule(final String mappingRuleId) {
    return new MappingRuleUpdateClient(writer, mappingRuleId);
  }

  public static class MappingRuleCreateClient {

    private static final Function<Long, Record<MappingRuleRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRuleRecords()
                .withIntent(MappingRuleIntent.CREATED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<MappingRuleRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRuleRecords()
                .onlyCommandRejections()
                .withIntent(MappingRuleIntent.CREATE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final MappingRuleRecord mappingRuleRecord;
    private Function<Long, Record<MappingRuleRecordValue>> expectation = SUCCESS_SUPPLIER;

    public MappingRuleCreateClient(final CommandWriter writer, final String mappingId) {
      this.writer = writer;
      mappingRuleRecord = new MappingRuleRecord();
      mappingRuleRecord.setMappingRuleId(mappingId);
    }

    public MappingRuleCreateClient withClaimName(final String claimName) {
      mappingRuleRecord.setClaimName(claimName);
      return this;
    }

    public MappingRuleCreateClient withClaimValue(final String claimValue) {
      mappingRuleRecord.setClaimValue(claimValue);
      return this;
    }

    public MappingRuleCreateClient withName(final String name) {
      mappingRuleRecord.setName(name);
      return this;
    }

    public Record<MappingRuleRecordValue> create() {
      final long position = writer.writeCommand(MappingRuleIntent.CREATE, mappingRuleRecord);
      return expectation.apply(position);
    }

    public Record<MappingRuleRecordValue> create(final String username) {
      final long position =
          writer.writeCommand(MappingRuleIntent.CREATE, username, mappingRuleRecord);
      return expectation.apply(position);
    }

    public MappingRuleCreateClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class MappingRuleDeleteClient {

    private static final Function<Long, Record<MappingRuleRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRuleRecords()
                .withIntent(MappingRuleIntent.DELETED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<MappingRuleRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRuleRecords()
                .onlyCommandRejections()
                .withIntent(MappingRuleIntent.DELETE)
                .withSourceRecordPosition(position)
                .getFirst();
    private final CommandWriter writer;
    private final MappingRuleRecord mappingRuleRecord;
    private Function<Long, Record<MappingRuleRecordValue>> expectation = SUCCESS_SUPPLIER;

    public MappingRuleDeleteClient(final CommandWriter writer, final String mappingId) {
      this.writer = writer;
      mappingRuleRecord = new MappingRuleRecord();
      mappingRuleRecord.setMappingRuleId(mappingId);
    }

    public Record<MappingRuleRecordValue> delete() {
      final long position = writer.writeCommand(MappingRuleIntent.DELETE, mappingRuleRecord);
      return expectation.apply(position);
    }

    public MappingRuleDeleteClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class MappingRuleUpdateClient {

    private static final Function<String, Record<MappingRuleRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRuleRecords()
                .withIntent(MappingRuleIntent.UPDATED)
                .filter(
                    mappingRecordValueRecord ->
                        mappingRecordValueRecord.getValue().getMappingRuleId().equals(position))
                .getFirst();

    private static final Function<String, Record<MappingRuleRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.mappingRuleRecords()
                .onlyCommandRejections()
                .withIntent(MappingRuleIntent.UPDATE)
                .filter(
                    mappingRecordValueRecord ->
                        mappingRecordValueRecord.getValue().getMappingRuleId().equals(position))
                .getFirst();
    private final CommandWriter writer;
    private final MappingRuleRecord mappingRuleRecord;
    private Function<String, Record<MappingRuleRecordValue>> expectation = SUCCESS_SUPPLIER;

    public MappingRuleUpdateClient(final CommandWriter writer, final String mappingId) {
      this.writer = writer;
      mappingRuleRecord = new MappingRuleRecord();
      mappingRuleRecord.setMappingRuleId(mappingId);
    }

    public Record<MappingRuleRecordValue> update() {
      writer.writeCommand(MappingRuleIntent.UPDATE, mappingRuleRecord);
      return expectation.apply(mappingRuleRecord.getMappingRuleId());
    }

    public Record<MappingRuleRecordValue> update(final String username) {
      writer.writeCommand(MappingRuleIntent.UPDATE, username, mappingRuleRecord);
      return expectation.apply(mappingRuleRecord.getMappingRuleId());
    }

    public MappingRuleUpdateClient withClaimName(final String claimName) {
      mappingRuleRecord.setClaimName(claimName);
      return this;
    }

    public MappingRuleUpdateClient withClaimValue(final String claimValue) {
      mappingRuleRecord.setClaimValue(claimValue);
      return this;
    }

    public MappingRuleUpdateClient withName(final String name) {
      mappingRuleRecord.setName(name);
      return this;
    }

    public MappingRuleUpdateClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
