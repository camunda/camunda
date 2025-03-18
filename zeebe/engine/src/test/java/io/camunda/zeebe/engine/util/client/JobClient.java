/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final CommandWriter writer;
  private long processInstanceKey;
  private long jobKey = DEFAULT_KEY;
  private List<String> authorizedTenantIds = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  private Function<Long, Record<JobRecordValue>> expectation = SUCCESS_SUPPLIER;

  public JobClient(final CommandWriter writer) {
    this.writer = writer;
    jobRecord = new JobRecord();
  }

  public JobClient ofInstance(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
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

  public JobClient withResult(final JobResult result) {
    jobRecord.setResult(result);
    return this;
  }

  public JobClient withRetries(final int retries) {
    jobRecord.setRetries(retries);
    return this;
  }

  public JobClient withTimeout(final long timeout) {
    jobRecord.setTimeout(timeout);
    return this;
  }

  public JobClient withBackOff(final Duration backOff) {
    jobRecord.setRetryBackoff(backOff.toMillis());
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

  public JobClient withAuthorizedTenantIds(final String... tenantIds) {
    authorizedTenantIds = List.of(tenantIds);
    return this;
  }

  public JobClient withChangeset(final Set<String> changeset) {
    jobRecord.setChangedAttributes(changeset);
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
              .withProcessInstanceKey(processInstanceKey)
              .getFirst();

      return createdJob.getKey();
    }

    return jobKey;
  }

  public Record<JobRecordValue> complete() {
    final long jobKey = findJobKey();
    final long position =
        writer.writeCommand(
            jobKey, JobIntent.COMPLETE, jobRecord, authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<JobRecordValue> complete(final String username) {
    final long jobKey = findJobKey();
    final long position =
        writer.writeCommand(
            jobKey,
            JobIntent.COMPLETE,
            username,
            jobRecord,
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<JobRecordValue> fail() {
    final long jobKey = findJobKey();
    final long position =
        writer.writeCommand(
            jobKey, JobIntent.FAIL, jobRecord, authorizedTenantIds.toArray(new String[0]));

    return expectation.apply(position);
  }

  public Record<JobRecordValue> fail(final String username) {
    final long jobKey = findJobKey();
    final long position =
        writer.writeCommand(
            jobKey,
            JobIntent.FAIL,
            username,
            jobRecord,
            authorizedTenantIds.toArray(new String[0]));

    return expectation.apply(position);
  }

  public Record<JobRecordValue> yield() {
    final long jobKey = findJobKey();
    final long position = writer.writeCommand(jobKey, JobIntent.YIELD, jobRecord);

    return expectation.apply(position);
  }

  public Record<JobRecordValue> updateRetries() {
    final long jobKey = findJobKey();
    final long position =
        writer.writeCommand(
            jobKey,
            JobIntent.UPDATE_RETRIES,
            jobRecord,
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<JobRecordValue> updateRetries(final String username) {
    final long jobKey = findJobKey();
    final long position =
        writer.writeCommand(
            jobKey,
            JobIntent.UPDATE_RETRIES,
            username,
            jobRecord,
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<JobRecordValue> updateTimeout() {
    final long jobKey = findJobKey();
    final long position =
        writer.writeCommand(
            jobKey,
            JobIntent.UPDATE_TIMEOUT,
            jobRecord,
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<JobRecordValue> updateTimeout(final String username) {
    final long jobKey = findJobKey();
    final long position =
        writer.writeCommand(
            jobKey,
            JobIntent.UPDATE_TIMEOUT,
            username,
            jobRecord,
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<JobRecordValue> update() {
    final long jobKey = findJobKey();
    final long position =
        writer.writeCommand(
            jobKey, JobIntent.UPDATE, jobRecord, authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }

  public Record<JobRecordValue> throwError() {
    final long jobKey = findJobKey();
    final long position =
        writer.writeCommand(
            jobKey, JobIntent.THROW_ERROR, jobRecord, authorizedTenantIds.toArray(new String[0]));

    return expectation.apply(position);
  }

  public Record<JobRecordValue> throwError(final String username) {
    final long jobKey = findJobKey();
    final long position =
        writer.writeCommand(
            jobKey,
            JobIntent.THROW_ERROR,
            username,
            jobRecord,
            authorizedTenantIds.toArray(new String[0]));
    return expectation.apply(position);
  }
}
