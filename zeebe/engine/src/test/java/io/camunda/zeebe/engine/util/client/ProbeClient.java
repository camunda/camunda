/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.Random;
import java.util.function.LongFunction;
import org.agrona.concurrent.UnsafeBuffer;

public final class ProbeClient {

  private static final LongFunction<Record<ProbeRecordValue>> SUCCESS_SUPPLIER =
      position ->
          RecordingExporter.probeRecords()
              .onlyEvents()
              .withSourceRecordPosition(position)
              .getFirst();
  private static final LongFunction<Record<ProbeRecordValue>> REJECTION_SUPPLIER =
      position ->
          RecordingExporter.probeRecords()
              .onlyCommandRejections()
              .withSourceRecordPosition(position)
              .getFirst();

  private final ProbeRecord probeRecord = new ProbeRecord();
  private final CommandWriter writer;
  private long requestId = new Random().nextLong(Long.MAX_VALUE);
  private int requestStreamId = new Random().nextInt(Integer.MAX_VALUE);
  private LongFunction<Record<ProbeRecordValue>> expectation = SUCCESS_SUPPLIER;

  public ProbeClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public ProbeClient withProbeId(final String probeId) {
    probeRecord.setProbeId(probeId);
    return this;
  }

  public ProbeClient withInput(final String inputExpression) {
    probeRecord.setInputExpression(inputExpression);
    return this;
  }

  public ProbeClient withTenantId(final String tenantId) {
    probeRecord.setTenantId(tenantId);
    return this;
  }

  public ProbeClient withRequestTimeout(final long requestTimeout) {
    probeRecord.setRequestTimeout(requestTimeout);
    return this;
  }

  public ProbeClient withRequest(final int requestStreamId, final long requestId) {
    this.requestStreamId = requestStreamId;
    this.requestId = requestId;
    return this;
  }

  public ProbeClient expectRejection() {
    expectation = REJECTION_SUPPLIER;
    return this;
  }

  /** Creates the probe; returns its first event (CREATED), or the rejection. */
  public Record<ProbeRecordValue> create() {
    final long position =
        writer.writeCommand(requestStreamId, requestId, ProbeIntent.CREATE, probeRecord);
    return expectation.apply(position);
  }

  /** Answers the probe as a worker; returns ANSWERED, or the rejection. */
  public Record<ProbeRecordValue> answer(
      final long probeKey, final ProbeOutcome outcome, final Map<String, Object> data) {
    probeRecord
        .setOutcome(outcome)
        .setData(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(data)));
    final long position =
        writer.writeCommand(probeKey, requestStreamId, requestId, ProbeIntent.ANSWER, probeRecord);
    return expectation.apply(position);
  }

  /** Yields the probe as the broker does after a failed push; returns its first event. */
  public Record<ProbeRecordValue> yield(final long probeKey) {
    final long position = writer.writeCommand(probeKey, ProbeIntent.YIELD, probeRecord);
    return expectation.apply(position);
  }
}
