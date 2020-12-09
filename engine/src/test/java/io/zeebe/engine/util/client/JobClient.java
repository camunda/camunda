/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.client;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class JobClient {
  private static final long DEFAULT_KEY = -1L;

  private static final Function<Long, Record<JobRecordValue>> SUCCESS_SUPPLIER =
      (position) -> RecordingExporter.jobRecords().withSourceRecordPosition(position).getFirst();

  private static final Function<Long, Record<JobRecordValue>> REJECTION_SUPPLIER =
      (position) ->
          RecordingExporter.jobRecords()
              .onlyCommandRejections()
              .withSourceRecordPosition(position)
              .getFirst();

  private final JobRecord jobRecord;
  private final StreamProcessorRule environmentRule;
  private long workflowInstanceKey;
  private long jobKey = DEFAULT_KEY;

  private Function<Long, Record<JobRecordValue>> expectation = SUCCESS_SUPPLIER;

  public JobClient(final StreamProcessorRule environmentRule) {
    this.environmentRule = environmentRule;
    jobRecord = new JobRecord();
  }

  public JobClient ofInstance(final long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
    return this;
  }

  public JobClient withType(final String jobType) {
    jobRecord.setType(jobType);
    return this;
  }

  public JobClient withKey(final long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  public JobClient withVariables(final String variables) {
    jobRecord.setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
    return this;
  }

  public JobClient withVariables(final DirectBuffer variables) {
    jobRecord.setVariables(variables);
    return this;
  }

  public JobClient withVariable(final String key, final Object value) {
    jobRecord.setVariables(MsgPackUtil.asMsgPack(key, value));
    return this;
  }

  public JobClient withVariables(final Map<String, Object> variables) {
    jobRecord.setVariables(MsgPackUtil.asMsgPack(variables));
    return this;
  }

  public JobClient withRetries(final int retries) {
    jobRecord.setRetries(retries);
    return this;
  }

  public JobClient withBackOff(final Duration backOff) {
    jobRecord.setRetryBackOff(backOff.toMillis());
    return this;
  }

  public JobClient withErrorMessage(final String errorMessage) {
    jobRecord.setErrorMessage(errorMessage);
    return this;
  }

  public JobClient withErrorCode(final String errorCode) {
    jobRecord.setErrorCode(wrapString(errorCode));
    return this;
  }

  public JobClient expectRejection() {
    expectation = REJECTION_SUPPLIER;
    return this;
  }

  private long findJobKey() {
    if (jobKey == DEFAULT_KEY) {
      final Record<JobRecordValue> createdJob =
          RecordingExporter.jobRecords()
              .withType(jobRecord.getType())
              .withIntent(JobIntent.CREATED)
              .withWorkflowInstanceKey(workflowInstanceKey)
              .getFirst();

      return createdJob.getKey();
    }

    return jobKey;
  }

  public Record<JobRecordValue> complete() {
    final long jobKey = findJobKey();
    final long position = environmentRule.writeCommand(jobKey, JobIntent.COMPLETE, jobRecord);
    return expectation.apply(position);
  }

  public Record<JobRecordValue> fail() {
    final long jobKey = findJobKey();
    final long position = environmentRule.writeCommand(jobKey, JobIntent.FAIL, jobRecord);

    return expectation.apply(position);
  }

  public Record<JobRecordValue> updateRetries() {
    final long jobKey = findJobKey();
    final long position = environmentRule.writeCommand(jobKey, JobIntent.UPDATE_RETRIES, jobRecord);
    return expectation.apply(position);
  }

  public Record<JobRecordValue> throwError() {
    final long jobKey = findJobKey();
    final long position = environmentRule.writeCommand(jobKey, JobIntent.THROW_ERROR, jobRecord);

    return expectation.apply(position);
  }
}
