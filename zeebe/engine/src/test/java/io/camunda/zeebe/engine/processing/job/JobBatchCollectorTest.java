/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.SecurityConfigurations;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.identity.AuthenticatedAuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
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
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValueAssert;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.ControllableStreamClockImpl;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
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
  private final ControllableStreamClock clock =
      new ControllableStreamClockImpl(InstantSource.system());

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState state;

  private JobBatchCollector collector;

  @BeforeEach
  void beforeEach() {
    final var authorizationCheckBehavior =
        new AuthorizationCheckBehavior(
            state,
            SecurityConfigurations.unauthenticatedAndUnauthorized(),
            new EngineConfiguration());
    collector = new JobBatchCollector(state, lengthEvaluator, authorizationCheckBehavior, clock);
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
    final Either<TooLargeJob, Map<JobKind, Integer>> result =
        collector.collectJobs(record, List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

    // then
    final JobBatchRecord batchRecord = record.getValue();
    EitherAssert.assertThat(result)
        .as("should have activated only one job successfully")
        .right()
        .isEqualTo(Map.of(JobKind.BPMN_ELEMENT, 1));
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .hasOnlyJobKeys(jobs.getFirst().key)
        .isTruncated();
  }

  @Test
  void shouldReturnLargeJobIfFirstJobCannotBeWritten() {
    // given
    final long variableScopeKey = state.getKeyGenerator().nextKey();
    final TypedRecord<JobBatchRecord> record = createRecord();
    final List<Job> jobs = Arrays.asList(createJob(variableScopeKey), createJob(variableScopeKey));

    // when - set up the evaluator to accept no jobs
    lengthEvaluator.canWriteEventOfLength = (length) -> false;
    final Either<TooLargeJob, Map<JobKind, Integer>> result =
        collector.collectJobs(record, List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

    // then
    final JobBatchRecord batchRecord = record.getValue();
    EitherAssert.assertThat(result)
        .as("should return excessively large job")
        .left()
        .hasFieldOrPropertyWithValue("key", jobs.getFirst().key);
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
    collector.collectJobs(record, List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

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
    collector.collectJobs(record, List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

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
    final Either<TooLargeJob, Map<JobKind, Integer>> result =
        collector.collectJobs(record, List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

    // then
    final JobBatchRecord batchRecord = record.getValue();
    EitherAssert.assertThat(result)
        .as("should collect only the first job")
        .right()
        .isEqualTo(Map.of(JobKind.BPMN_ELEMENT, 1));
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .hasJobKeys(jobs.getFirst().key)
        .isNotTruncated();
  }

  @Test
  void shouldSetDeadlineOnActivation() {
    // given
    final TypedRecord<JobBatchRecord> record = createRecord();
    final long scopeKey = state.getKeyGenerator().nextKey();
    clock.pinAt(Instant.now().plusSeconds(30)); // pin clock for a deterministic timeout

    final long expectedDeadline = clock.millis() + record.getValue().getTimeout();
    createJob(scopeKey);
    createJob(scopeKey);

    // when
    collector.collectJobs(record, List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

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
    collector.collectJobs(record, List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

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
    collector.collectJobs(record, List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

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
    collector.collectJobs(record, List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

    // then
    // the expected length is then the length of the initial record + the length of the activated
    // job and an 8 KB buffer
    final var activatedJob = (JobRecord) record.getValue().getJobs().get(0);
    final int expectedLength =
        initialLength
            + activatedJob.getLength()
            + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;
    assertThat(estimatedLength.ref).isEqualTo(expectedLength);
  }

  @Test
  public void shouldCollectOnlyCustomTenantJobs() {
    // given
    final String tenantA = "tenant-a";
    final String tenantB = "tenant-b";
    final TypedRecord<JobBatchRecord> record = createRecord(tenantA, tenantB);
    final long firstScopeKey = state.getKeyGenerator().nextKey();
    final long secondScopeKey = state.getKeyGenerator().nextKey();
    createJob(firstScopeKey, tenantA);
    createJob(secondScopeKey, tenantB);

    // when
    collector.collectJobs(record, List.of(tenantA, tenantB));

    // then
    final JobBatchRecord batchRecord = record.getValue();
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .satisfies(
            batch -> {
              final List<JobRecordValue> activatedJobs = batch.getJobs();
              assertThat(activatedJobs).hasSize(2);
              assertThat(activatedJobs.stream().map(job -> job.getTenantId()).toList())
                  .containsExactlyInAnyOrder(tenantA, tenantB);
            });
  }

  @Test
  public void shouldCollectOnlyProvidedTenantJobs() {
    // given
    final String tenantA = "tenant-a";
    final String tenantB = "tenant-b";
    final TypedRecord<JobBatchRecord> record = createRecord(tenantA);
    final long firstScopeKey = state.getKeyGenerator().nextKey();
    final long secondScopeKey = state.getKeyGenerator().nextKey();
    createJob(firstScopeKey, tenantA);
    createJob(secondScopeKey, tenantB);

    // when
    collector.collectJobs(record, List.of(tenantA));

    // then
    final JobBatchRecord batchRecord = record.getValue();
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .satisfies(
            batch -> {
              final List<JobRecordValue> activatedJobs = batch.getJobs();
              assertThat(activatedJobs).hasSize(1);
              JobRecordValueAssert.assertThat(activatedJobs.get(0)).hasTenantId(tenantA);
            });
  }

  @Test
  public void shouldCollectJobsBasedOnAssignedTenantsWhenFilterIsAssigned() {
    // given
    final String tenantA = "tenant-a";
    final String tenantB = "tenant-b";
    final String tenantC = "tenant-c";
    final TypedRecord<JobBatchRecord> record = createRecordWithTenantFilter(TenantFilter.ASSIGNED);

    final long scopeKeyA = state.getKeyGenerator().nextKey();
    final long scopeKeyB = state.getKeyGenerator().nextKey();
    final long scopeKeyC = state.getKeyGenerator().nextKey();

    createJob(scopeKeyA, tenantA);
    createJob(scopeKeyB, tenantB);
    createJob(scopeKeyC, tenantC);

    // when - user is authorized for tenantA and tenantB only
    final var authorizedTenants = new AuthenticatedAuthorizedTenants(List.of(tenantA, tenantB));
    collector.collectJobs(record, authorizedTenants.getAuthorizedTenantIds());

    // then - only jobs from tenantA and tenantB should be collected
    final JobBatchRecord batchRecord = record.getValue();
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .satisfies(
            batch -> {
              final List<JobRecordValue> activatedJobs = batch.getJobs();
              assertThat(activatedJobs).hasSize(2);
              assertThat(activatedJobs.stream().map(JobRecordValue::getTenantId).toList())
                  .containsExactlyInAnyOrder(tenantA, tenantB);
            });
  }

  @Test
  public void shouldIgnoreProvidedTenantIdsWhenFilterIsAssigned() {
    // given
    final String tenantA = "tenant-a";
    final String tenantB = "tenant-b";
    final String tenantC = "tenant-c";

    // Create record with tenantC in the tenant IDs list but ASSIGNED filter
    final TypedRecord<JobBatchRecord> record =
        createRecordWithTenantFilter(TenantFilter.ASSIGNED, tenantC);

    final long scopeKeyA = state.getKeyGenerator().nextKey();
    final long scopeKeyB = state.getKeyGenerator().nextKey();
    final long scopeKeyC = state.getKeyGenerator().nextKey();

    createJob(scopeKeyA, tenantA);
    createJob(scopeKeyB, tenantB);
    createJob(scopeKeyC, tenantC);

    // when - user is authorized for tenantA and tenantB, record specifies tenantC
    final var authorizedTenants = new AuthenticatedAuthorizedTenants(List.of(tenantA, tenantB));
    collector.collectJobs(record, authorizedTenants.getAuthorizedTenantIds());

    // then - should use authorized tenants (A, B) and ignore provided tenant (C)
    final JobBatchRecord batchRecord = record.getValue();
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .satisfies(
            batch -> {
              final List<JobRecordValue> activatedJobs = batch.getJobs();
              assertThat(activatedJobs).hasSize(2);
              assertThat(activatedJobs.stream().map(JobRecordValue::getTenantId).toList())
                  .containsExactlyInAnyOrder(tenantA, tenantB)
                  .doesNotContain(tenantC);
            });
  }

  @Test
  public void shouldUseAssignedTenantsEvenWhenProvidedTenantIdsIsEmpty() {
    // given
    final String tenantA = "tenant-a";
    final String tenantB = "tenant-b";

    // Create record with empty tenant IDs but ASSIGNED filter
    final TypedRecord<JobBatchRecord> record = createRecordWithTenantFilter(TenantFilter.ASSIGNED);

    final long scopeKeyA = state.getKeyGenerator().nextKey();
    final long scopeKeyB = state.getKeyGenerator().nextKey();
    final long scopeKeyDefault = state.getKeyGenerator().nextKey();

    createJob(scopeKeyA, tenantA);
    createJob(scopeKeyB, tenantB);
    createJob(scopeKeyDefault, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // when - user is authorized for tenantA only, record has empty tenant IDs
    final var authorizedTenants = new AuthenticatedAuthorizedTenants(tenantA);
    collector.collectJobs(record, authorizedTenants.getAuthorizedTenantIds());

    // then - should use assigned tenants (A) and not default tenant
    final JobBatchRecord batchRecord = record.getValue();
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .satisfies(
            batch -> {
              final List<JobRecordValue> activatedJobs = batch.getJobs();
              assertThat(activatedJobs).hasSize(1);
              JobRecordValueAssert.assertThat(activatedJobs.get(0)).hasTenantId(tenantA);
            });
  }

  @Test
  public void shouldCollectNoJobsWhenAssignedTenantsHaveNoMatchingJobs() {
    // given
    final String tenantA = "tenant-a";
    final String tenantB = "tenant-b";

    final TypedRecord<JobBatchRecord> record = createRecordWithTenantFilter(TenantFilter.ASSIGNED);

    final long scopeKeyA = state.getKeyGenerator().nextKey();

    createJob(scopeKeyA, tenantA);

    // when - user is authorized for tenantB only, but job exists for tenantA
    final var authorizedTenants = new AuthenticatedAuthorizedTenants(tenantB);
    collector.collectJobs(record, authorizedTenants.getAuthorizedTenantIds());

    // then - no jobs should be collected
    final JobBatchRecord batchRecord = record.getValue();
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .satisfies(
            batch -> {
              final List<JobRecordValue> activatedJobs = batch.getJobs();
              assertThat(activatedJobs).isEmpty();
            });
  }

  @Test
  public void shouldUseProvidedTenantsWhenFilterIsProvided() {
    // given
    final String tenantA = "tenant-a";
    final String tenantB = "tenant-b";

    // Create record with tenantA in tenant IDs and PROVIDED filter (default)
    final TypedRecord<JobBatchRecord> record =
        createRecordWithTenantFilter(TenantFilter.PROVIDED, tenantA);

    final long scopeKeyA = state.getKeyGenerator().nextKey();
    final long scopeKeyB = state.getKeyGenerator().nextKey();

    createJob(scopeKeyA, tenantA);
    createJob(scopeKeyB, tenantB);

    // when - user is authorized for tenantB, but record specifies tenantA
    final var authorizedTenants = new AuthenticatedAuthorizedTenants(tenantB);
    collector.collectJobs(record, List.of(tenantA));

    // then - should use provided tenant (A) from record
    final JobBatchRecord batchRecord = record.getValue();
    JobBatchRecordValueAssert.assertThat(batchRecord)
        .satisfies(
            batch -> {
              final List<JobRecordValue> activatedJobs = batch.getJobs();
              assertThat(activatedJobs).hasSize(1);
              JobRecordValueAssert.assertThat(activatedJobs.get(0)).hasTenantId(tenantA);
            });
  }

  private TypedRecord<JobBatchRecord> createRecord(final String... tenantIds) {
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

    final List<String> tenantIdsList =
        tenantIds.length > 0 ? List.of(tenantIds) : List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    batchRecord.setTenantIds(tenantIdsList);

    return new MockTypedRecord<>(state.getKeyGenerator().nextKey(), metadata, batchRecord);
  }

  private TypedRecord<JobBatchRecord> createRecordWithTenantFilter(
      final TenantFilter tenantFilter, final String... tenantIds) {
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
            .setWorker("test")
            .setTenantFilter(tenantFilter);

    if (tenantIds.length > 0) {
      batchRecord.setTenantIds(List.of(tenantIds));
    } else {
      batchRecord.setTenantIds(List.of());
    }

    return new MockTypedRecord<>(state.getKeyGenerator().nextKey(), metadata, batchRecord);
  }

  private Job createJob(final long variableScopeKey) {
    return createJob(variableScopeKey, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private Job createJob(final long variableScopeKey, final String tenantId) {
    final var jobRecord =
        new JobRecord()
            .setBpmnProcessId("process")
            .setElementId("element")
            .setElementInstanceKey(variableScopeKey)
            .setType(JOB_TYPE)
            .setTenantId(tenantId);
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
