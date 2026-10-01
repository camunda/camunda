/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;

public final class StandaloneJobClient {

  private final CommandWriter writer;
  private final JobRecord jobRecord = new JobRecord().setTimeout(Duration.ofMinutes(1).toMillis());
  private int requestStreamId = 1;
  private long requestId = 1L;
  private String username;

  public StandaloneJobClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public StandaloneJobClient withType(final String type) {
    jobRecord.setType(type);
    return this;
  }

  public StandaloneJobClient withInputExpression(final String inputExpression) {
    jobRecord.setInputExpression(inputExpression);
    return this;
  }

  public StandaloneJobClient withCustomHeaders(final Map<String, String> customHeaders) {
    jobRecord.setCustomHeaders(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(customHeaders)));
    return this;
  }

  /** The time the job waits for an answer before it expires. */
  public StandaloneJobClient withTimeToAnswer(final Duration timeToAnswer) {
    jobRecord.setTimeout(timeToAnswer.toMillis());
    return this;
  }

  public StandaloneJobClient withRetries(final int retries) {
    jobRecord.setRetries(retries);
    return this;
  }

  public StandaloneJobClient withTenantId(final String tenantId) {
    jobRecord.setTenantId(tenantId);
    return this;
  }

  public StandaloneJobClient withRequest(final int requestStreamId, final long requestId) {
    this.requestStreamId = requestStreamId;
    this.requestId = requestId;
    return this;
  }

  public StandaloneJobClient byUser(final String username) {
    this.username = username;
    return this;
  }

  /** Creates the job and returns its {@code CREATED} event. */
  public Record<JobRecordValue> create() {
    final long position = write();
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withSourceRecordPosition(position)
        .getFirst();
  }

  public Record<JobRecordValue> createExpectingRejection() {
    final long position = write();
    return RecordingExporter.jobRecords(JobIntent.CREATE)
        .onlyCommandRejections()
        .withSourceRecordPosition(position)
        .getFirst();
  }

  private long write() {
    if (username == null) {
      return writer.writeCommand(requestStreamId, requestId, JobIntent.CREATE, jobRecord);
    }
    return writer.writeCommand(
        -1L, requestStreamId, requestId, JobIntent.CREATE, username, jobRecord);
  }
}
