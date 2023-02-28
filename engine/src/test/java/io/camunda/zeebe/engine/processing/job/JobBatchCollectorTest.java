/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.job.JobBatchCollector.TooLargeJob;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValueWithVariablesAssert;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValueAssert;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValueAssert;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.collections.MutableReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class JobBatchCollectorTest {
  private static final String JOB_TYPE = "job";

  private final RecordLengthEvaluator lengthEvaluator = new RecordLengthEvaluator();

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState state;

  private JobBatchCollector collector;

  @BeforeEach
  void beforeEach() {
    collector =
        new JobBatchCollector(state.getJobState(), state.getVariableState(), lengthEvaluator);
  }

  @Test
  void shouldTruncateBatchIfNoMoreCanBeWritten() {
    // given
    final long variableScopeKey = state.getKeyGenerator().nextKey();
    final TypedRecord<JobBatchRecord> record = createRecord();
    final List<Job> jobs = Arrays.asList(createJob(variableScopeKey), createJob(variableScopeKey));
    final var toggle = new AtomicBoolean(true);

    // when - set up the evaluator to only accept the first job
    lengthEvaluator.canWriteEventOfLength = (length) -> toggle.getAndSet(false);
    final Either<TooLargeJob, Integer> result = collector.collectJobs(record);

    // then
    final JobBatchRecord batchRecord = record.getValue();
    EitherAssert.assertThat(result)
        .as("should have activated only one job successfully")
        .right()
        .isEqualTo(1);
    JobBatchRecordValueAssert.assertThat(batchRecord).hasOnlyJobKeys(jobs.get(0).key).isTruncated();
  }

  @Test
  void shouldReturnLargeJobIfFirstJobCannotBeWritten() {
    // given
    final long variableScopeKey = state.getKeyGenerator().nextKey();
    final TypedRecord<JobBatchRecord> record = createRecord();
    final List<Job> jobs = Arrays.asList(createJob(variableScopeKey), createJob(variableScopeKey));

    // when - set up the evaluator to accept no jobs
    lengthEvaluator.canWriteEventOfLength = (length) -> false;
    final Either<TooLargeJob, Integer> result = collector.collectJobs(record);

    // then
    final JobBatchRecord batchRecord = record.getValue();
    EitherAssert.assertThat(result)
        .as("should return excessively large job")
        .left()
        .hasFieldOrPropertyWithValue("key", jobs.get(0).key);
    JobBatchRecordValueAssert.assertThat(batchRecord).hasNoJobKeys().hasNoJobs().isTruncated();
  }

  @Test
  void shouldCollectJobsWithVariables() {
    // given - multiple jobs to ensure variables are collected based on the scope
    final TypedRecord<JobBatchRecord> record = createRecord();
    final long firstScopeKey = state.getKeyGenerator().nextKey();
    final long secondScopeKey = state.getKeyGenerator().nextKey();
    final Map<String, String> firstJobVariables = Map.of("foo", "bar", "baz", "buz");
    final Map<String, String> secondJobVariables = Map.of("fizz", "buzz");
    createJobWithVariables(firstScopeKey, firstJobVariables);
    createJobWithVariables(secondScopeKey, secondJobVariables);

    // when
    collector.collectJobs(record);

    // then
    final JobBatchRecord batchRecord = record.getValue();
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .satisfies(
            batch -> {
              final List<JobRecordValue> activatedJobs = batch.getJobs();
              RecordValueWithVariablesAssert.assertThat(activatedJobs.get(0))
                  .hasVariables(firstJobVariables);
              RecordValueWithVariablesAssert.assertThat(activatedJobs.get(1))
                  .hasVariables(secondJobVariables);
            });
  }

  @Test
  void shouldAppendJobKeyToBatchRecord() {
    // given - multiple jobs to ensure variables are collected based on the scope
    final TypedRecord<JobBatchRecord> record = createRecord();
    final long scopeKey = state.getKeyGenerator().nextKey();
    final List<Job> jobs = Arrays.asList(createJob(scopeKey), createJob(scopeKey));

    // when
    collector.collectJobs(record);

    // then
    final JobBatchRecord batchRecord = record.getValue();
    JobBatchRecordValueAssert.assertThat(batchRecord).hasJobKeys(jobs.get(0).key, jobs.get(1).key);
  }

  @Test
  void shouldActivateUpToMaxJobs() {
    // given
    final TypedRecord<JobBatchRecord> record = createRecord();
    final long scopeKey = state.getKeyGenerator().nextKey();
    final List<Job> jobs = Arrays.asList(createJob(scopeKey), createJob(scopeKey));
    record.getValue().setMaxJobsToActivate(1);

    // when
    final Either<TooLargeJob, Integer> result = collector.collectJobs(record);

    // then
    final JobBatchRecord batchRecord = record.getValue();
    EitherAssert.assertThat(result).as("should collect only the first job").right().isEqualTo(1);
    JobBatchRecordValueAssert.assertThat(batchRecord).hasJobKeys(jobs.get(0).key).isNotTruncated();
  }

  @Test
  void shouldSetDeadlineOnActivation() {
    // given
    final TypedRecord<JobBatchRecord> record = createRecord();
    final long scopeKey = state.getKeyGenerator().nextKey();
    final long expectedDeadline = record.getTimestamp() + record.getValue().getTimeout();
    createJob(scopeKey);
    createJob(scopeKey);

    // when
    collector.collectJobs(record);

    // then
    final JobBatchRecord batchRecord = record.getValue();
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .satisfies(
            batch -> {
              final List<JobRecordValue> activatedJobs = batch.getJobs();
              JobRecordValueAssert.assertThat(activatedJobs.get(0))
                  .as("first activated job has the expected deadline")
                  .hasDeadline(expectedDeadline);
              JobRecordValueAssert.assertThat(activatedJobs.get(1))
                  .as("second activated job has the expected deadline")
                  .hasDeadline(expectedDeadline);
            });
  }

  @Test
  void shouldSetWorkerOnActivation() {
    // given
    final TypedRecord<JobBatchRecord> record = createRecord();
    final long scopeKey = state.getKeyGenerator().nextKey();
    final String expectedWorker = "foo";
    createJob(scopeKey);
    createJob(scopeKey);
    record.getValue().setWorker(expectedWorker);

    // when
    collector.collectJobs(record);

    // then
    final JobBatchRecord batchRecord = record.getValue();
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .satisfies(
            batch -> {
              final List<JobRecordValue> activatedJobs = batch.getJobs();
              JobRecordValueAssert.assertThat(activatedJobs.get(0))
                  .as("first activated job has the expected worker")
                  .hasWorker(expectedWorker);
              JobRecordValueAssert.assertThat(activatedJobs.get(1))
                  .as("second activated job has the expected worker")
                  .hasWorker(expectedWorker);
            });
  }

  @Test
  void shouldFetchOnlyRequestedVariables() {
    // given
    final TypedRecord<JobBatchRecord> record = createRecord();
    final long firstScopeKey = state.getKeyGenerator().nextKey();
    final long secondScopeKey = state.getKeyGenerator().nextKey();
    final Map<String, String> firstJobVariables = Map.of("foo", "bar", "baz", "buz");
    final Map<String, String> secondJobVariables = Map.of("fizz", "buzz");
    createJobWithVariables(firstScopeKey, firstJobVariables);
    createJobWithVariables(secondScopeKey, secondJobVariables);
    record.getValue().variables().add().wrap(BufferUtil.wrapString("foo"));

    // when
    collector.collectJobs(record);

    // then
    final JobBatchRecord batchRecord = record.getValue();
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .satisfies(
            batch -> {
              final List<JobRecordValue> activatedJobs = batch.getJobs();
              RecordValueWithVariablesAssert.assertThat(activatedJobs.get(0))
                  .hasVariables(Map.of("foo", "bar"));
              RecordValueWithVariablesAssert.assertThat(activatedJobs.get(1))
                  .hasVariables(Collections.emptyMap());
            });
  }

  /**
   * This is specifically a regression test for #5525. It's possible for this test to become
   * outdated if we ever change how records are serialized, variables packed, etc. But it's a
   * best-effort solution to make sure that if we do, we are forced to double-check and ensure we're
   * correctly estimating the size of the record before writing it.
   *
   * <p>Long term, the writer should be able to cope with arbitrarily large batches, but until then
   * we're stuck with this workaround for a better UX.
   */
  @Test
  void shouldEstimateLengthCorrectly() {
    // given - multiple jobs to ensure variables are collected based on the scope
    final TypedRecord<JobBatchRecord> record = createRecord();
    final long scopeKey = state.getKeyGenerator().nextKey();
    final Map<String, String> variables = Map.of("foo", "bar");
    final MutableReference<Integer> estimatedLength = new MutableReference<>();
    final int initialLength = record.getLength();
    createJobWithVariables(scopeKey, variables);

    // when
    lengthEvaluator.canWriteEventOfLength =
        length -> {
          estimatedLength.set(length);
          return true;
        };
    collector.collectJobs(record);

    // then
    // the expected length is then the length of the initial record + the length of the activated
    // job + the length of a key (long) and one byte for its list header
    final var activatedJob = (JobRecord) record.getValue().getJobs().get(0);
    final int expectedLength = initialLength + activatedJob.getLength() + 9;
    assertThat(estimatedLength.ref).isEqualTo(expectedLength);
  }

  private TypedRecord<JobBatchRecord> createRecord() {
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(JobBatchIntent.ACTIVATE)
            .valueType(ValueType.JOB_BATCH);
    final var batchRecord =
        new JobBatchRecord()
            .setTimeout(Duration.ofSeconds(10).toMillis())
            .setMaxJobsToActivate(10)
            .setType(JOB_TYPE)
            .setWorker("test");

    return new MockTypedRecord<>(state.getKeyGenerator().nextKey(), metadata, batchRecord);
  }

  private Job createJob(final long variableScopeKey) {
    final var jobRecord =
        new JobRecord()
            .setBpmnProcessId("process")
            .setElementId("element")
            .setElementInstanceKey(variableScopeKey)
            .setType(JOB_TYPE);
    final long jobKey = state.getKeyGenerator().nextKey();

    state.getJobState().create(jobKey, jobRecord);
    return new Job(jobKey, jobRecord);
  }

  private void createJobWithVariables(
      final long variableScopeKey, final Map<String, String> variables) {
    setVariables(variableScopeKey, variables);
    createJob(variableScopeKey);
  }

  private void setVariables(final long variableScopeKey, final Map<String, String> variables) {
    final var variableState = state.getVariableState();
    variables.forEach(
        (key, value) ->
            variableState.setVariableLocal(
                variableScopeKey,
                variableScopeKey,
                variableScopeKey,
                BufferUtil.wrapString(key),
                packString(value)));
  }

  private DirectBuffer packString(final String value) {
    return MsgPackUtil.encodeMsgPack(b -> b.packString(value));
  }

  private static final class RecordLengthEvaluator implements Predicate<Integer> {
    private Predicate<Integer> canWriteEventOfLength = length -> true;

    @Override
    public boolean test(final Integer length) {
      return canWriteEventOfLength.test(length);
    }
  }

  private record Job(long key, JobRecord job) {}
}
