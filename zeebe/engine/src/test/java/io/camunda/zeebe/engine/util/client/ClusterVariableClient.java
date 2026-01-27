/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Random;
import java.util.function.LongFunction;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ClusterVariableClient {

  private static final long DEFAULT_KEY = -1;

  private static final LongFunction<Record<ClusterVariableRecordValue>> SUCCESS_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.clusterVariableRecords()
              .onlyEvents()
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();
  private static final LongFunction<Record<ClusterVariableRecordValue>> REJECTION_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.clusterVariableRecords()
              .onlyCommandRejections()
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();

  private final long requestId = new Random().nextLong();
  private final int requestStreamId = new Random().nextInt();

  private final ClusterVariableRecord clusterVariableRecord;
  private final CommandWriter writer;
  private LongFunction<Record<ClusterVariableRecordValue>> expectation = SUCCESS_SUPPLIER;

  public ClusterVariableClient(final CommandWriter writer) {
    clusterVariableRecord = new ClusterVariableRecord();
    this.writer = writer;
  }

  public ClusterVariableClient withValue(final String value) {
    return withValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value)));
  }

  public ClusterVariableClient withValue(final Object value) {
    return withValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value)));
  }

  public ClusterVariableClient withValue(final DirectBuffer value) {
    clusterVariableRecord.setValue(value);
    return this;
  }

  public ClusterVariableClient withName(final String name) {
    clusterVariableRecord.setName(name);
    return this;
  }

  public ClusterVariableClient withTenantId(final String tenantId) {
    clusterVariableRecord.setTenantId(tenantId);
    return this;
  }

  public ClusterVariableClient setTenantScope() {
    clusterVariableRecord.setTenantScope();
    return this;
  }

  public ClusterVariableClient setGlobalScope() {
    clusterVariableRecord.setGlobalScope();
    return this;
  }

  public ClusterVariableClient expectRejection() {
    expectation = REJECTION_SUPPLIER;
    return this;
  }

  public Record<ClusterVariableRecordValue> create() {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            ClusterVariableIntent.CREATE,
            clusterVariableRecord);
    return expectation.apply(position);
  }

  public Record<ClusterVariableRecordValue> create(final String username) {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            ClusterVariableIntent.CREATE,
            username,
            clusterVariableRecord);
    return expectation.apply(position);
  }

  public Record<ClusterVariableRecordValue> delete() {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            ClusterVariableIntent.DELETE,
            clusterVariableRecord);
    return expectation.apply(position);
  }

  public Record<ClusterVariableRecordValue> delete(final String username) {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            ClusterVariableIntent.DELETE,
            username,
            clusterVariableRecord);
    return expectation.apply(position);
  }

  public Record<ClusterVariableRecordValue> update() {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            ClusterVariableIntent.UPDATE,
            clusterVariableRecord);
    return expectation.apply(position);
  }

  public Record<ClusterVariableRecordValue> update(final String username) {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            ClusterVariableIntent.UPDATE,
            username,
            clusterVariableRecord);
    return expectation.apply(position);
  }
}
