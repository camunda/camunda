/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProbeBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
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
  private boolean expectRejection;

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

  /** The absolute deadline a gateway sends with CREATE. */
  public ProbeClient withDeadline(final long deadline) {
    probeRecord.setDeadline(deadline);
    return this;
  }

  public ProbeClient withRequest(final int requestStreamId, final long requestId) {
    this.requestStreamId = requestStreamId;
    this.requestId = requestId;
    return this;
  }

  public ProbeClient expectRejection() {
    expectation = REJECTION_SUPPLIER;
    expectRejection = true;
    return this;
  }

  /** Creates the probe; returns its first event (CREATED), or the rejection. */
  public Record<ProbeRecordValue> create() {
    final long position =
        writer.writeCommand(requestStreamId, requestId, ProbeIntent.CREATE, probeRecord);
    return expectation.apply(position);
  }

  /** Creates the probe as the user; returns its first event (CREATED), or the rejection. */
  public Record<ProbeRecordValue> create(final String username) {
    final long position =
        writer.writeCommand(requestStreamId, requestId, ProbeIntent.CREATE, probeRecord, username);
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

  /**
   * Polls for the probes of the probe ID as a worker; returns the PROBE_BATCH ACTIVATED event, or
   * the rejection. The secret values are only in the response, which the event does not carry.
   */
  public Record<ProbeBatchRecordValue> activate(
      final String probeId,
      final String worker,
      final int maxProbesToActivate,
      final String... tenantIds) {
    return activate(List.of(probeId), worker, maxProbesToActivate, tenantIds);
  }

  /** Polls for the probes of several probe IDs at once, as a worker that answers them all. */
  public Record<ProbeBatchRecordValue> activate(
      final List<String> probeIds,
      final String worker,
      final int maxProbesToActivate,
      final String... tenantIds) {
    final var batch =
        new ProbeBatchRecord()
            .setProbeIds(probeIds)
            .setWorker(worker)
            .setMaxProbesToActivate(maxProbesToActivate)
            .setTenantIds(List.of(tenantIds));
    return activated(
        writer.writeCommand(requestStreamId, requestId, ProbeBatchIntent.ACTIVATE, batch));
  }

  /** Polls for the probes of the probe IDs as a worker authenticated as the user. */
  public Record<ProbeBatchRecordValue> activateAs(
      final String username,
      final List<String> probeIds,
      final String worker,
      final int maxProbesToActivate) {
    final var batch =
        new ProbeBatchRecord()
            .setProbeIds(probeIds)
            .setWorker(worker)
            .setMaxProbesToActivate(maxProbesToActivate);
    return activated(
        writer.writeCommand(
            requestStreamId, requestId, ProbeBatchIntent.ACTIVATE, batch, username));
  }

  private Record<ProbeBatchRecordValue> activated(final long position) {
    final var records = RecordingExporter.probeBatchRecords().withSourceRecordPosition(position);
    return expectRejection
        ? records.onlyCommandRejections().getFirst()
        : records.onlyEvents().getFirst();
  }

  /** Yields the probe as the gateway does when it cannot deliver it; returns its first event. */
  public Record<ProbeRecordValue> yield(final long probeKey) {
    final long position = writer.writeCommand(probeKey, ProbeIntent.YIELD, probeRecord);
    return expectation.apply(position);
  }
}
