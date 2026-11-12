/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProbeBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Random;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

public final class ProbeClient {

  private static final long DEFAULT_REQUEST_TIMEOUT = 10_000L;

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

  private final ProbeRecord probeRecord =
      new ProbeRecord().setRequestTimeout(DEFAULT_REQUEST_TIMEOUT);
  private final CommandWriter writer;
  private final LongSupplier clock;
  private boolean deadlineSet;
  private long requestId = new Random().nextLong(Long.MAX_VALUE);
  private int requestStreamId = new Random().nextInt(Integer.MAX_VALUE);
  private LongFunction<Record<ProbeRecordValue>> expectation = SUCCESS_SUPPLIER;

  public ProbeClient(final CommandWriter writer, final LongSupplier clock) {
    this.writer = writer;
    this.clock = clock;
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

  /**
   * The absolute deadline, as the gateway sends it. Without it, the client sends the current time
   * plus the request timeout, as the gateway computes it.
   */
  public ProbeClient withDeadline(final long deadline) {
    probeRecord.setDeadline(deadline);
    deadlineSet = true;
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

  /** Creates the probe; returns its first event ({@code CREATED}), or the rejection. */
  public Record<ProbeRecordValue> create() {
    setDefaultDeadline();
    final long position =
        writer.writeCommand(requestStreamId, requestId, ProbeIntent.CREATE, probeRecord);
    return expectation.apply(position);
  }

  /** As {@link #create()}, sent by the user. */
  public Record<ProbeRecordValue> create(final String username) {
    setDefaultDeadline();
    final long position =
        writer.writeCommand(requestStreamId, requestId, ProbeIntent.CREATE, probeRecord, username);
    return expectation.apply(position);
  }

  /**
   * Polls for the probes of the probe IDs as a worker; returns the {@code PROBE_BATCH:ACTIVATED}
   * event, or the rejection.
   */
  public Record<ProbeBatchRecordValue> activate(
      final List<String> probeIds, final String worker, final int maxProbesToActivate) {
    return activated(
        writer.writeCommand(
            requestStreamId,
            requestId,
            ProbeBatchIntent.ACTIVATE,
            batch(probeIds, worker, maxProbesToActivate)));
  }

  /** As {@link #activate}, sent by the user, for the tenants it is assigned to. */
  public Record<ProbeBatchRecordValue> activate(
      final List<String> probeIds,
      final String worker,
      final int maxProbesToActivate,
      final String username) {
    return activated(
        writer.writeCommand(
            requestStreamId,
            requestId,
            ProbeBatchIntent.ACTIVATE,
            batch(probeIds, worker, maxProbesToActivate).setTenantFilter(TenantFilter.ASSIGNED),
            username));
  }

  /** Sends the worker's answer; returns {@code ANSWERED}, or the rejection. */
  public Record<ProbeRecordValue> answer(final long probeKey, final ProbeRecord answer) {
    return expectation.apply(
        writer.writeCommand(probeKey, requestStreamId, requestId, ProbeIntent.ANSWER, answer));
  }

  /** As {@link #answer(long, ProbeRecord)}, sent by the user. */
  public Record<ProbeRecordValue> answer(
      final long probeKey, final ProbeRecord answer, final String username) {
    return expectation.apply(
        writer.writeCommand(
            probeKey, requestStreamId, requestId, ProbeIntent.ANSWER, username, answer));
  }

  /** Sends the engine's internal RESPOND command as a client would; returns its rejection. */
  public Record<ProbeRecordValue> respond(final long probeKey, final ProbeRecord response) {
    return expectation.apply(
        writer.writeCommand(probeKey, requestStreamId, requestId, ProbeIntent.RESPOND, response));
  }

  /** Yields the probe, as the gateway does when it cannot deliver it; returns {@code YIELDED}. */
  public Record<ProbeRecordValue> yield(final long probeKey) {
    return expectation.apply(writer.writeCommand(probeKey, ProbeIntent.YIELD, new ProbeRecord()));
  }

  private Record<ProbeBatchRecordValue> activated(final long position) {
    final var records = RecordingExporter.probeBatchRecords().withSourceRecordPosition(position);
    return expectation == REJECTION_SUPPLIER
        ? records.onlyCommandRejections().getFirst()
        : records.onlyEvents().getFirst();
  }

  private static ProbeBatchRecord batch(
      final List<String> probeIds, final String worker, final int maxProbesToActivate) {
    return new ProbeBatchRecord()
        .setProbeIds(probeIds)
        .setWorker(worker)
        .setMaxProbesToActivate(maxProbesToActivate);
  }

  private void setDefaultDeadline() {
    if (!deadlineSet) {
      probeRecord.setDeadline(clock.getAsLong() + probeRecord.getRequestTimeout());
    }
  }
}
