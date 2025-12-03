/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.keygenerator.KeyGeneratorResetRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.KeyGeneratorResetIntent;
import io.camunda.zeebe.protocol.record.value.KeyGeneratorResetRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Objects;
import java.util.function.Function;

public final class KeyGeneratorResetClient {
  private static final Function<Long, Record<KeyGeneratorResetRecordValue>> SUCCESS_EXPECTATION =
      (position) ->
          RecordingExporter.keyGeneratorResetRecords(KeyGeneratorResetIntent.RESET_APPLIED)
              .withSourceRecordPosition(position)
              .getFirst();
  private static final Function<Long, Record<KeyGeneratorResetRecordValue>> REJECTION_EXPECTATION =
      (position) ->
          RecordingExporter.keyGeneratorResetRecords()
              .onlyCommandRejections()
              .withSourceRecordPosition(position)
              .getFirst();

  private final CommandWriter writer;
  private final KeyGeneratorResetRecord record = new KeyGeneratorResetRecord();
  private long requestId = -1L;
  private int requestStreamId = -1;
  private Function<Long, Record<KeyGeneratorResetRecordValue>> expectation = null;

  public KeyGeneratorResetClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public KeyGeneratorResetClient requestId(final long requestId) {
    this.requestId = requestId;
    return this;
  }

  public KeyGeneratorResetClient requestStreamId(final int requestStreamId) {
    this.requestStreamId = requestStreamId;
    return this;
  }

  public KeyGeneratorResetClient expectRejection() {
    expectation = REJECTION_EXPECTATION;
    return this;
  }

  public KeyGeneratorResetClient withPartitionId(final int partitionId) {
    record.setPartitionId(partitionId);
    return this;
  }

  public KeyGeneratorResetClient withNewKeyValue(final long newKeyValue) {
    record.setNewKeyValue(newKeyValue);
    return this;
  }

  public Record<KeyGeneratorResetRecordValue> reset() {
    final long position = writeCommand(KeyGeneratorResetIntent.RESET);
    return Objects.requireNonNullElse(expectation, SUCCESS_EXPECTATION).apply(position);
  }

  public Record<KeyGeneratorResetRecordValue> reset(final String username) {
    final long position = writeCommand(KeyGeneratorResetIntent.RESET, username);
    return Objects.requireNonNullElse(expectation, SUCCESS_EXPECTATION).apply(position);
  }

  private long writeCommand(final KeyGeneratorResetIntent intent) {
    if (requestId != -1 && requestStreamId != -1) {
      return writer.writeCommand(requestStreamId, requestId, intent, record);
    }

    return writer.writeCommand(intent, record);
  }

  private long writeCommand(final KeyGeneratorResetIntent intent, final String username) {
    return writer.writeCommand(intent, username, record);
  }
}
