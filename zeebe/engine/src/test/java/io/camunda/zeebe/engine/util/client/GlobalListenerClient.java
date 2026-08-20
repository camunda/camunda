/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.function.BiFunction;

public final class GlobalListenerClient {

  private static final BiFunction<Long, GlobalListenerIntent, Record<GlobalListenerRecordValue>>
      SUCCESS_SUPPLIER =
          (sourceRecordPosition, intent) ->
              RecordingExporter.globalListenerRecords()
                  .withIntent(intent)
                  .withSourceRecordPosition(sourceRecordPosition)
                  .getFirst();
  private static final BiFunction<Long, GlobalListenerIntent, Record<GlobalListenerRecordValue>>
      REJECTION_SUPPLIER =
          (sourceRecordPosition, intent) ->
              RecordingExporter.globalListenerRecords()
                  .onlyCommandRejections()
                  .withSourceRecordPosition(sourceRecordPosition)
                  .getFirst();

  private final GlobalListenerRecord globalListenerRecord;
  private final CommandWriter writer;
  private BiFunction<Long, GlobalListenerIntent, Record<GlobalListenerRecordValue>> expectation =
      SUCCESS_SUPPLIER;

  public GlobalListenerClient(final CommandWriter writer) {
    globalListenerRecord = new GlobalListenerRecord();
    this.writer = writer;
  }

  public GlobalListenerClient withId(final String id) {
    globalListenerRecord.setId(id);
    return this;
  }

  public GlobalListenerClient withType(final String type) {
    globalListenerRecord.setType(type);
    return this;
  }

  public GlobalListenerClient withRetries(final int retries) {
    globalListenerRecord.setRetries(retries);
    return this;
  }

  public GlobalListenerClient withEventTypes(final String... eventTypes) {
    globalListenerRecord.setEventTypes(List.of(eventTypes));
    return this;
  }

  public GlobalListenerClient addEventType(final String eventType) {
    globalListenerRecord.addEventType(eventType);
    return this;
  }

  public GlobalListenerClient withAfterNonGlobal(final boolean afterNonGlobal) {
    globalListenerRecord.setAfterNonGlobal(afterNonGlobal);
    return this;
  }

  public GlobalListenerClient afterNonGlobal() {
    return withAfterNonGlobal(true);
  }

  public GlobalListenerClient beforeNonGlobal() {
    return withAfterNonGlobal(false);
  }

  public GlobalListenerClient withPriority(final int priority) {
    globalListenerRecord.setPriority(priority);
    return this;
  }

  public GlobalListenerClient withSource(final GlobalListenerSource source) {
    globalListenerRecord.setSource(source);
    return this;
  }

  public GlobalListenerClient withListenerType(final GlobalListenerType listenerType) {
    globalListenerRecord.setListenerType(listenerType);
    return this;
  }

  public GlobalListenerClient expectRejection() {
    expectation = REJECTION_SUPPLIER;
    return this;
  }

  public Record<GlobalListenerRecordValue> create() {
    final long position = writer.writeCommand(GlobalListenerIntent.CREATE, globalListenerRecord);
    return expectation.apply(position, GlobalListenerIntent.CREATED);
  }

  public Record<GlobalListenerRecordValue> create(final String username) {
    final long position =
        writer.writeCommand(GlobalListenerIntent.CREATE, username, globalListenerRecord);
    return expectation.apply(position, GlobalListenerIntent.CREATED);
  }

  public Record<GlobalListenerRecordValue> update() {
    final long position = writer.writeCommand(GlobalListenerIntent.UPDATE, globalListenerRecord);
    return expectation.apply(position, GlobalListenerIntent.UPDATED);
  }

  public Record<GlobalListenerRecordValue> update(final String username) {
    final long position =
        writer.writeCommand(GlobalListenerIntent.UPDATE, username, globalListenerRecord);
    return expectation.apply(position, GlobalListenerIntent.UPDATED);
  }

  public Record<GlobalListenerRecordValue> delete() {
    final long position = writer.writeCommand(GlobalListenerIntent.DELETE, globalListenerRecord);
    return expectation.apply(position, GlobalListenerIntent.DELETED);
  }

  public Record<GlobalListenerRecordValue> delete(final String username) {
    final long position =
        writer.writeCommand(GlobalListenerIntent.DELETE, username, globalListenerRecord);
    return expectation.apply(position, GlobalListenerIntent.DELETED);
  }
}
